package com.github.shekohex.whisperpp.data.history

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Transcript history (§4.8, WP-3.8) over Room. Owns the kept audio files: every delete path also
 * removes the rows' recordings. Never logs text.
 */
class TranscriptHistoryRepository(
    private val dao: TranscriptHistoryDao,
    private val audio: HistoryAudioStore,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HistoryBackupSource {
    val count: Flow<Int> = dao.observeCount()

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
     * Deletes entries older than [retentionDays] (0 = keep forever) and kept audio no entry points to.
     * Returns how many entries were removed.
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
