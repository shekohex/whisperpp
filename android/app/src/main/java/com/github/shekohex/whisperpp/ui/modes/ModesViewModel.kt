package com.github.shekohex.whisperpp.ui.modes

import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.modes.LegacyModesMigration
import com.github.shekohex.whisperpp.data.modes.ModesRepository
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.ModeAvailability
import com.github.shekohex.whisperpp.ui.settings.common.LauncherApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Mode cards (§4.10), their quick actions and the one-way legacy migration prompt. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModesViewModel(
    private val modesRepository: ModesRepository,
    private val migration: LegacyModesMigration,
    providers: Flow<List<ServiceProvider>>,
    profiles: Flow<List<PromptProfile>>,
    prefs: Flow<Preferences>,
    hasCredential: (providerId: String) -> Boolean,
    private val loadApps: suspend () -> List<LauncherApp>,
) : ViewModel() {
    constructor(container: AppContainer, loadApps: suspend () -> List<LauncherApp>) : this(
        modesRepository = container.modesRepository,
        migration = container.legacyModesMigration,
        providers = container.settingsRepository.providers,
        profiles = container.settingsRepository.promptProfiles,
        prefs = container.settingsDataStore.data,
        hasCredential = { id -> !container.secretsStore.getProviderApiKey(id).isNullOrBlank() },
        loadApps = loadApps,
    )

    /** One card; null labels mean "not set" and hide the chip. */
    data class Card(
        val mode: Mode,
        val availability: ModeAvailability,
        val sttModel: String?,
        /** Null = the keyboard's language. */
        val language: String?,
        /** Null when enhancement is off for this mode. */
        val enhancementModel: String?,
        val enhancementOn: Boolean,
        val prompt: String?,
        val appLabels: List<String>,
    )

    data class UiState(
        val loading: Boolean = true,
        val cards: List<Card> = emptyList(),
        /** Legacy data waiting for [migrate]; null once migrated or when there is nothing to migrate. */
        val pendingMigration: LegacyModesMigration.Preview? = null,
        val migrating: Boolean = false,
    )

    enum class Error { ACTION_FAILED, MIGRATION_FAILED }

    private val apps = MutableStateFlow<Map<String, String>>(emptyMap())
    private val migrating = MutableStateFlow(false)
    private val errorState = MutableStateFlow<Error?>(null)

    /** Last failed action, cleared by [clearError]. */
    val error: StateFlow<Error?> = errorState.asStateFlow()

    private val pendingMigration: Flow<LegacyModesMigration.Preview?> = modesRepository.isMigrated.mapLatest { migrated ->
        if (migrated) return@mapLatest null
        try {
            migration.migrationPreview().takeIf { it.hasLegacyData && !it.alreadyMigrated }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Migration preview failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private val globals: Flow<ModeGlobals> = combine(providers, profiles, prefs) { providerList, profileList, preferences ->
        ModeGlobals.from(preferences, providerList, profileList, hasCredential)
    }

    val uiState: StateFlow<UiState> = combine(
        modesRepository.modes,
        globals,
        apps,
        pendingMigration,
        migrating,
    ) { modes, globalsValue, appLabels, pending, isMigrating ->
        UiState(
            loading = false,
            cards = modes.map { card(it, globalsValue, appLabels) },
            pendingMigration = pending,
            migrating = isMigrating,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    init {
        viewModelScope.launch {
            apps.value = runCatching { loadApps() }.getOrDefault(emptyList()).associate { it.packageName to it.label }
        }
    }

    fun setEnabled(modeId: String, enabled: Boolean) = launchAction { modesRepository.setEnabled(modeId, enabled) }

    fun setDefault(modeId: String) = launchAction { modesRepository.setDefault(modeId) }

    fun delete(modeId: String) = launchAction { modesRepository.delete(modeId) }

    /** Copies [mode] under [name]; packages stay with the original (a package belongs to one mode). */
    fun duplicate(mode: Mode, name: String) = launchAction {
        modesRepository.upsert(
            mode.copy(
                id = newModeId(),
                name = name,
                appPackages = emptyList(),
                isDefault = false,
                enabled = true,
            ),
        )
    }

    fun migrate() {
        if (migrating.value) return
        migrating.value = true
        viewModelScope.launch {
            try {
                migration.migrate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Legacy migration failed: ${e.javaClass.simpleName}")
                errorState.value = Error.MIGRATION_FAILED
            } finally {
                migrating.value = false
            }
        }
    }

    fun clearError() {
        errorState.value = null
    }

    private fun launchAction(action: suspend () -> Boolean) {
        viewModelScope.launch {
            val ok = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Mode action failed: ${e.javaClass.simpleName}")
                false
            }
            if (!ok) errorState.value = Error.ACTION_FAILED
        }
    }

    private fun card(mode: Mode, globals: ModeGlobals, appLabels: Map<String, String>): Card {
        val enhancementOn = mode.enhancementEnabled ?: globals.enhancementEnabled
        return Card(
            mode = mode,
            availability = globals.availabilityOf(mode),
            sttModel = globals.modelLabel(mode.sttSelection, globals.stt),
            language = mode.language,
            enhancementModel = if (enhancementOn) globals.modelLabel(mode.enhancementSelection, globals.text) else null,
            enhancementOn = enhancementOn,
            prompt = globals.promptName(mode.promptId),
            appLabels = mode.appPackages.map { appLabels[it] ?: it },
        )
    }

    companion object {
        private const val TAG = "ModesViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        fun newModeId(): String = "mode-" + UUID.randomUUID().toString()
    }
}
