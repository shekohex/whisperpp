package com.github.shekohex.whisperpp.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room access for [TranscriptHistoryEntry]. Newest first everywhere; `id` breaks createdAt ties. */
@Dao
interface TranscriptHistoryDao {
    @Insert
    suspend fun insert(entry: TranscriptHistoryEntry): Long

    @Insert
    suspend fun insertAll(entries: List<TranscriptHistoryEntry>)

    @Query("UPDATE transcript_history SET enhancedText = :enhancedText, enhancementModelId = :enhancementModelId WHERE id = :id")
    suspend fun updateEnhancedText(id: Long, enhancedText: String?, enhancementModelId: String?): Int

    @Query("SELECT * FROM transcript_history WHERE id = :id")
    suspend fun get(id: Long): TranscriptHistoryEntry?

    @Query("SELECT * FROM transcript_history ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TranscriptHistoryEntry>>

    /** [pattern] is a LIKE pattern escaped with `\`; LIKE is case-insensitive for ASCII. */
    @Query(
        "SELECT * FROM transcript_history " +
            "WHERE rawText LIKE :pattern ESCAPE '\\' OR enhancedText LIKE :pattern ESCAPE '\\' " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit"
    )
    fun observeSearch(pattern: String, limit: Int): Flow<List<TranscriptHistoryEntry>>

    @Query("SELECT * FROM transcript_history ORDER BY createdAt DESC, id DESC")
    suspend fun all(): List<TranscriptHistoryEntry>

    @Query("SELECT COUNT(*) FROM transcript_history")
    fun observeCount(): Flow<Int>

    @Query("SELECT audioPath FROM transcript_history WHERE id IN (:ids) AND audioPath IS NOT NULL")
    suspend fun audioPathsFor(ids: List<Long>): List<String>

    @Query("SELECT audioPath FROM transcript_history WHERE audioPath IS NOT NULL")
    suspend fun allAudioPaths(): List<String>

    @Query("SELECT audioPath FROM transcript_history WHERE createdAt < :cutoff AND audioPath IS NOT NULL")
    suspend fun audioPathsOlderThan(cutoff: Long): List<String>

    @Query("DELETE FROM transcript_history WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>): Int

    @Query("DELETE FROM transcript_history WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM transcript_history")
    suspend fun deleteAll()
}
