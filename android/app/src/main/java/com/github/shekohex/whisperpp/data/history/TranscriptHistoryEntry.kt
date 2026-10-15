package com.github.shekohex.whisperpp.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus

/**
 * One recorded take (§4.8 History): written when the recording stops and updated through the
 * pipeline, so a failed take is never lost. Text stays on device. [audioPath] is kept for unresolved
 * ([HistoryStatus.FAILED]) takes, and for resolved ones while "keep recordings" is on. A take without
 * a transcript has an empty [rawText].
 */
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
    @ColumnInfo(defaultValue = "'COMPLETED'")
    val status: HistoryStatus = HistoryStatus.COMPLETED,
    val failureReason: HistoryFailureReason? = null,
    /** Mode the take was recorded with; retry resolves that mode's settings. */
    val modeId: String? = null,
) {
    /** What ended up in the field (empty while there is no transcript). */
    val finalText: String get() = enhancedText ?: rawText

    val hasText: Boolean get() = finalText.isNotBlank()

    /** Unresolved: its audio is kept so it can be retried. */
    val needsAttention: Boolean get() = status == HistoryStatus.FAILED

    /**
     * Audio is there and the take is finished, failed, has no transcript, or has been mid-pipeline
     * long enough that its run must be gone.
     */
    fun isRetryable(nowMillis: Long): Boolean =
        audioPath != null &&
            (!status.isInFlight || !hasText || nowMillis - createdAt > IN_FLIGHT_STALE_MS)

    companion object {
        const val TABLE = "transcript_history"
        private const val IN_FLIGHT_STALE_MS = 10 * 60 * 1000L
    }
}
