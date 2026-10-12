package com.github.shekohex.whisperpp.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One completed dictation (§4.8 History). Text stays on device; [audioPath] only when keep-audio is on. */
@Entity(
    tableName = TranscriptHistoryEntry.TABLE,
    indices = [Index(value = ["createdAt"])],
)
data class TranscriptHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Epoch millis. */
    val createdAt: Long,
    val rawText: String,
    val enhancedText: String? = null,
    val providerId: String,
    val sttModelId: String,
    val enhancementModelId: String? = null,
    val language: String? = null,
    val appPackage: String? = null,
    val durationMs: Long,
    val wordCount: Int,
    @ColumnInfo(name = "audioPath")
    val audioPath: String? = null,
) {
    /** What ended up in the field. */
    val finalText: String get() = enhancedText ?: rawText

    companion object {
        const val TABLE = "transcript_history"
    }
}
