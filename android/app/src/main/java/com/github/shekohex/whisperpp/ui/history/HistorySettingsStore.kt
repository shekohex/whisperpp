package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.data.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** History preferences as the history screen sees them (a seam for ViewModel tests). */
interface HistorySettingsStore {
    val settings: Flow<HistorySettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setKeepAudio(keepAudio: Boolean)

    /** 0 keeps entries forever. */
    suspend fun setRetentionDays(days: Int)
}

class SettingsRepositoryHistorySettings(private val repository: SettingsRepository) : HistorySettingsStore {
    override val settings: Flow<HistorySettings> = repository.historySettings

    override suspend fun setEnabled(enabled: Boolean) = repository.setHistoryEnabled(enabled)

    override suspend fun setKeepAudio(keepAudio: Boolean) = repository.setHistoryKeepAudio(keepAudio)

    override suspend fun setRetentionDays(days: Int) = repository.setHistoryRetentionDays(days)
}
