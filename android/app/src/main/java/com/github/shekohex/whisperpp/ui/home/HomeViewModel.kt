package com.github.shekohex.whisperpp.ui.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot
import com.github.shekohex.whisperpp.data.BackupStatusStore
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.MIC_PERMISSION_REQUESTED
import com.github.shekohex.whisperpp.data.SelectionValidationResult
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.data.validateSelections
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.domain.setup.SetupStatus
import com.github.shekohex.whisperpp.domain.setup.SetupStatusChecker
import com.github.shekohex.whisperpp.ui.settings.common.PreferencesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach

/**
 * Home dashboard (§4.9): analytics hero, setup checklist/issues and recent transcripts. Invalid
 * provider/model selections found while Home is visible are cleared, as before the split.
 */
class HomeViewModel(
    private val dataStore: DataStore<Preferences>,
    settingsRepository: SettingsRepository,
    analyticsRepository: AnalyticsRepository,
    backupStatusStore: BackupStatusStore,
    private val setupStatusChecker: SetupStatusChecker,
    recentTranscripts: Flow<List<TranscriptHistoryEntry>> = flowOf(emptyList()),
    needsAttention: Flow<Int> = flowOf(0),
) : PreferencesViewModel(dataStore) {

    constructor(container: AppContainer) : this(
        dataStore = container.settingsDataStore,
        settingsRepository = container.settingsRepository,
        analyticsRepository = container.analyticsRepository,
        backupStatusStore = container.backupStatusStore,
        setupStatusChecker = container.setupStatusChecker,
        recentTranscripts = container.transcriptHistoryRepository.observeRecent(RECENT_TRANSCRIPTS_LIMIT),
        needsAttention = container.transcriptHistoryRepository.needsAttentionCount,
    )

    internal data class Summary(
        val prefs: Preferences,
        val providers: List<ServiceProvider>,
        val validation: SelectionValidationResult,
    )

    /** `null` until DataStore has emitted once. */
    internal val summary: StateFlow<Summary?> = combine(dataStore.data, settingsRepository.providers) { prefs, providers ->
        Summary(prefs = prefs, providers = providers, validation = validate(prefs, providers))
    }.onEach { summary ->
        val keys = summary.validation.keysToClear
        if (keys.isNotEmpty()) {
            dataStore.edit { prefs -> keys.forEach(prefs::remove) }
        }
    }.stateInScreen(null)

    val analytics: StateFlow<AnalyticsSnapshot?> = analyticsRepository.snapshot.stateInScreen(null)

    val recent: StateFlow<List<TranscriptHistoryEntry>> = recentTranscripts.stateInScreen(emptyList())

    /** Failed takes whose recording is still saved; Home offers to open them. */
    val needsAttention: StateFlow<Int> = needsAttention.stateInScreen(0)

    val backupStatus: StateFlow<BackupStatusStore.Status> = backupStatusStore.status

    /** `null` until first computed; drives the setup checklist. */
    val setupStatus: StateFlow<SetupStatus?> = setupStatusChecker.status.stateInScreen(null)

    fun setMicRequested() = set(MIC_PERMISSION_REQUESTED, true)

    fun refreshSetupStatus() = setupStatusChecker.refresh()

    private fun validate(prefs: Preferences, providers: List<ServiceProvider>): SelectionValidationResult =
        validateSelections(
            providers = providers,
            sttProviderId = prefs[ACTIVE_STT_PROVIDER_ID].orEmpty(),
            sttModelId = prefs[ACTIVE_STT_MODEL_ID].orEmpty(),
            textProviderId = prefs[ACTIVE_TEXT_PROVIDER_ID].orEmpty(),
            textModelId = prefs[ACTIVE_TEXT_MODEL_ID].orEmpty(),
            commandProviderId = prefs[COMMAND_TEXT_PROVIDER_ID].orEmpty(),
            commandModelId = prefs[COMMAND_TEXT_MODEL_ID].orEmpty(),
        )
}
