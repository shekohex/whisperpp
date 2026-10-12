package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow

internal fun historyEntry(
    createdAt: Long,
    raw: String,
    enhanced: String? = null,
    appPackage: String? = null,
    audioPath: String? = null,
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
)

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
