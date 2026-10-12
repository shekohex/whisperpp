package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

internal fun historyEntry(
    createdAt: Long,
    raw: String,
    enhanced: String? = null,
    appPackage: String? = null,
    audioPath: String? = null,
    status: HistoryStatus = HistoryStatus.COMPLETED,
    failureReason: HistoryFailureReason? = null,
) = TranscriptHistoryEntry(
    createdAt = createdAt,
    rawText = raw,
    enhancedText = enhanced,
    providerId = "openai",
    sttModelId = "whisper-1",
    appPackage = appPackage,
    durationMs = 4_000,
    wordCount = raw.split(' ').size,
    audioPath = audioPath,
    status = status,
    failureReason = failureReason,
)

/** A [HistoryTranscriber] a test never reaches. */
internal object UnusedTranscriber : HistoryTranscriber {
    override suspend fun transcribe(audio: File, entry: TranscriptHistoryEntry): HistoryTranscriber.Result = error("unused")

    override suspend fun enhancementEnabled(entry: TranscriptHistoryEntry): Boolean = error("unused")
}

/** In-memory [HistorySettingsStore]. */
internal class FakeHistorySettingsStore(initial: HistorySettings = HistorySettings()) : HistorySettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings = state

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = state.value.copy(enabled = enabled)
    }

    override suspend fun setKeepAudio(keepAudio: Boolean) {
        state.value = state.value.copy(keepAudio = keepAudio)
    }

    override suspend fun setRetentionDays(days: Int) {
        state.value = state.value.copy(retentionDays = days)
    }
}
