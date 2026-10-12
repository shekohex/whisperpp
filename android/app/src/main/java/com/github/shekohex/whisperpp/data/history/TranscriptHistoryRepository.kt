package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Transcript history (§4.8, WP-3.8) over Room. Owns the kept audio files: every delete path also
 * removes the rows' recordings. Rows are written when a take stops and updated through the pipeline
 * ([update], [complete]); unresolved takes keep their audio. Never logs text.
 */
class TranscriptHistoryRepository(
    private val dao: TranscriptHistoryDao,
    private val audio: HistoryAudioStore,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HistoryBackupSource {
    private val updateLock = Mutex()

    val count: Flow<Int> = dao.observeCount()

    /** Takes whose transcription or delivery failed and that the user can still retry. */
    val needsAttentionCount: Flow<Int> = dao.observeNeedsAttentionCount()

    /** Newest first. */
    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<TranscriptHistoryEntry>> = dao.observeRecent(limit.coerceAtLeast(1))

    /** Case-insensitive substring match on raw and enhanced text; a blank query lists everything. */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): Flow<List<TranscriptHistoryEntry>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeRecent(limit)
        return dao.observeSearch(likePattern(trimmed), limit.coerceAtLeast(1))
    }

    suspend fun get(id: Long): TranscriptHistoryEntry? = withContext(ioDispatcher) { dao.get(id) }

    suspend fun insert(entry: TranscriptHistoryEntry): Long = withContext(ioDispatcher) {
        dao.insert(entry.copy(id = 0))
    }

    /**
     * Read-modify-write of one row, serialized with other updates; null when the entry is gone.
     * [transform] must not change the id.
     */
    suspend fun update(id: Long, transform: (TranscriptHistoryEntry) -> TranscriptHistoryEntry): TranscriptHistoryEntry? =
        withContext(ioDispatcher) {
            updateLock.withLock {
                val current = dao.get(id) ?: return@withLock null
                val next = transform(current).copy(id = id)
                if (dao.update(next) > 0) next else null
            }
        }

    /**
     * Resolves a take: status COMPLETED, and its audio is deleted unless [keepAudio]. [edit] runs
     * first (new text, reason). Null when the entry is gone.
     */
    suspend fun complete(
        id: Long,
        keepAudio: Boolean,
        edit: (TranscriptHistoryEntry) -> TranscriptHistoryEntry = { it },
    ): TranscriptHistoryEntry? {
        var dropped: String? = null
        val updated = update(id) { current ->
            val next = edit(current).copy(status = HistoryStatus.COMPLETED)
            if (keepAudio || next.audioPath == null) {
                next
            } else {
                dropped = next.audioPath
                next.copy(audioPath = null)
            }
        }
        dropped?.let { path -> withContext(ioDispatcher) { audio.delete(path) } }
        return updated
    }

    /** Sets (or clears, with null) the enhanced text of [id]; false when the entry is gone. */
    suspend fun updateEnhancedText(id: Long, enhancedText: String?, enhancementModelId: String?): Boolean =
        withContext(ioDispatcher) {
            dao.updateEnhancedText(id, enhancedText?.takeIf { it.isNotBlank() }, enhancementModelId) > 0
        }

    suspend fun delete(ids: Collection<Long>) = withContext(ioDispatcher) {
        ids.distinct().chunked(SQL_CHUNK).forEach { chunk ->
            val paths = dao.audioPathsFor(chunk)
            dao.delete(chunk)
            audio.deleteAll(paths)
        }
    }

    suspend fun deleteAll() = withContext(ioDispatcher) {
        dao.deleteAll()
        audio.clear()
    }

    /**
     * Takes a dead process left mid-pipeline (older than [before], the start of this process) become
     * FAILED/INTERRUPTED so the user can retry them. Returns how many were marked.
     */
    suspend fun reconcileInterrupted(before: Long): Int = withContext(ioDispatcher) {
        dao.inFlightBefore(before).count { stale ->
            update(stale.id) { it.copy(status = HistoryStatus.FAILED, failureReason = HistoryFailureReason.INTERRUPTED) } != null
        }
    }

    /**
     * Recordings no row references (a process died before the row was written) are not lost: the
     * ones that hold audio become FAILED/INTERRUPTED rows. Files of takes in use are never touched.
     */
    suspend fun recoverOrphans(): Int = withContext(ioDispatcher) {
        val referenced = dao.allAudioPaths().toSet()
        var recovered = 0
        audio.orphans(referenced, modifiedBefore = clock() - RECOVERY_GRACE_MS).forEach { file ->
            // Read before the header repair touches the file: this is when the take ended.
            val endedAt = file.lastModified()
            if (file.length() < MIN_RECOVERABLE_BYTES || !audio.repairWavHeader(file)) return@forEach
            dao.insert(
                TranscriptHistoryEntry(
                    createdAt = endedAt,
                    rawText = "",
                    providerId = "",
                    sttModelId = "",
                    durationMs = HistoryAudioStore.durationMs(file),
                    wordCount = 0,
                    audioPath = file.absolutePath,
                    status = HistoryStatus.FAILED,
                    failureReason = HistoryFailureReason.INTERRUPTED,
                )
            )
            recovered++
        }
        recovered
    }

    /**
     * Deletes entries older than [retentionDays] (0 = keep forever) with their audio, then the files
     * no entry points to (and no live take owns). Returns how many entries were removed.
     */
    suspend fun pruneExpired(retentionDays: Int): Int = withContext(ioDispatcher) {
        val removed = if (retentionDays > 0) {
            val cutoff = clock() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            val paths = dao.audioPathsOlderThan(cutoff)
            val count = dao.deleteOlderThan(cutoff)
            audio.deleteAll(paths)
            count
        } else {
            0
        }
        audio.deleteOrphans(dao.allAudioPaths().toSet(), modifiedBefore = clock() - ORPHAN_GRACE_MS)
        removed
    }

    /** Text-only snapshot, newest first. */
    override suspend fun exportBackup(): HistoryBackup = withContext(ioDispatcher) {
        HistoryBackup(entries = dao.all().map { it.toBackupEntry() })
    }

    suspend fun exportJson(): String = gson.toJson(exportBackup())

    /**
     * Restores text entries. [replace] wipes the current history (and its audio) first; otherwise
     * entries already present (same createdAt and raw text) are skipped. Returns how many were added.
     */
    override suspend fun importBackup(backup: HistoryBackup, replace: Boolean): Int = withContext(ioDispatcher) {
        val incoming = sanitizeHistoryBackup(backup).entries
        if (replace) {
            dao.deleteAll()
            audio.clear()
        }
        val existing = if (replace) emptySet() else dao.all().map { it.createdAt to it.rawText }.toSet()
        val toInsert = incoming
            .distinctBy { it.createdAt to it.rawText }
            .filterNot { (it.createdAt to it.rawText) in existing }
            .map { it.toEntry() }
        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        toInsert.size
    }

    companion object {
        const val DEFAULT_LIMIT = 500
        private const val SQL_CHUNK = 500
        private val ORPHAN_GRACE_MS = TimeUnit.HOURS.toMillis(1)
        private val RECOVERY_GRACE_MS = TimeUnit.MINUTES.toMillis(1)

        /** About 0.1 s of audio past the WAV header; shorter leftovers are not worth a row. */
        private const val MIN_RECOVERABLE_BYTES = HistoryAudioStore.WAV_HEADER_BYTES + 3_200

        /** `%query%` with LIKE wildcards in the query escaped by `\`. */
        fun likePattern(query: String): String {
            val escaped = buildString(query.length + 2) {
                query.forEach { c ->
                    if (c == '\\' || c == '%' || c == '_') append('\\')
                    append(c)
                }
            }
            return "%$escaped%"
        }
    }
}
