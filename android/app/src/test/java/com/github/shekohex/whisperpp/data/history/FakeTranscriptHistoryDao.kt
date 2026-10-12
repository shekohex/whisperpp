package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TranscriptHistoryDao] mirroring the Room queries (Room needs a device; no Robolectric here). */
class FakeTranscriptHistoryDao : TranscriptHistoryDao {
    private val rows = MutableStateFlow<List<TranscriptHistoryEntry>>(emptyList())
    private var nextId = 1L

    val snapshot: List<TranscriptHistoryEntry> get() = rows.value

    override suspend fun insert(entry: TranscriptHistoryEntry): Long {
        val id = nextId++
        rows.value = rows.value + entry.copy(id = id)
        return id
    }

    override suspend fun insertAll(entries: List<TranscriptHistoryEntry>) {
        entries.forEach { insert(it) }
    }

    override suspend fun update(entry: TranscriptHistoryEntry): Int {
        if (rows.value.none { it.id == entry.id }) return 0
        rows.value = rows.value.map { if (it.id == entry.id) entry else it }
        return 1
    }

    override suspend fun updateEnhancedText(id: Long, enhancedText: String?, enhancementModelId: String?): Int {
        var updated = 0
        rows.value = rows.value.map {
            if (it.id == id) {
                updated++
                it.copy(enhancedText = enhancedText, enhancementModelId = enhancementModelId)
            } else {
                it
            }
        }
        return updated
    }

    override suspend fun get(id: Long): TranscriptHistoryEntry? = rows.value.find { it.id == id }

    override fun observeRecent(limit: Int): Flow<List<TranscriptHistoryEntry>> = rows.map { sorted(it).take(limit) }

    override fun observeSearch(pattern: String, limit: Int): Flow<List<TranscriptHistoryEntry>> {
        val regex = likeToRegex(pattern)
        return rows.map { list ->
            sorted(list).filter { regex.matches(it.rawText) || it.enhancedText?.let(regex::matches) == true }.take(limit)
        }
    }

    override suspend fun all(): List<TranscriptHistoryEntry> = sorted(rows.value)

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override fun observeNeedsAttentionCount(): Flow<Int> =
        rows.map { list -> list.count { it.status == HistoryStatus.FAILED } }

    override suspend fun inFlightBefore(before: Long): List<TranscriptHistoryEntry> =
        rows.value.filter { it.status.isInFlight && it.createdAt < before }

    override suspend fun audioPathsFor(ids: List<Long>): List<String> =
        rows.value.filter { it.id in ids }.mapNotNull { it.audioPath }

    override suspend fun allAudioPaths(): List<String> = rows.value.mapNotNull { it.audioPath }

    override suspend fun audioPathsOlderThan(cutoff: Long): List<String> =
        rows.value.filter { it.createdAt < cutoff }.mapNotNull { it.audioPath }

    override suspend fun delete(ids: List<Long>): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id in ids }
        return before - rows.value.size
    }

    override suspend fun deleteOlderThan(cutoff: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.createdAt < cutoff }
        return before - rows.value.size
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    private fun sorted(list: List<TranscriptHistoryEntry>) =
        list.sortedWith(compareByDescending<TranscriptHistoryEntry> { it.createdAt }.thenByDescending { it.id })

    /** SQLite LIKE with `\` escapes; case-insensitive. */
    private fun likeToRegex(pattern: String): Regex {
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' && i + 1 < pattern.length -> {
                    out.append(Regex.escape(pattern[i + 1].toString()))
                    i++
                }
                c == '%' -> out.append(".*")
                c == '_' -> out.append('.')
                else -> out.append(Regex.escape(c.toString()))
            }
            i++
        }
        return Regex(out.toString(), setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }
}
