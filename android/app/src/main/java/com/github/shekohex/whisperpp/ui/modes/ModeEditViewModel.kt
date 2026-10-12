package com.github.shekohex.whisperpp.ui.modes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.modes.ModesRepository
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.ui.settings.common.LauncherApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Draft of one mode (new when [modeId] is null). Nothing is stored until [save] passes [validate];
 * saving moves the draft's apps away from the modes that had them (the repository enforces it).
 */
class ModeEditViewModel(
    private val modeId: String?,
    private val modesRepository: ModesRepository,
    providers: Flow<List<ServiceProvider>>,
    profiles: Flow<List<PromptProfile>>,
    private val loadApps: suspend () -> List<LauncherApp>,
) : ViewModel() {
    constructor(container: AppContainer, modeId: String?, loadApps: suspend () -> List<LauncherApp>) : this(
        modeId = modeId,
        modesRepository = container.modesRepository,
        providers = container.settingsRepository.providers,
        profiles = container.settingsRepository.promptProfiles,
        loadApps = loadApps,
    )

    enum class ValidationError {
        NAME_BLANK,
        NAME_TOO_LONG,
        NAME_DUPLICATE,

        /** A provider is chosen without a model. */
        STT_MODEL_MISSING,
        ENHANCEMENT_MODEL_MISSING,
    }

    enum class Status { LOADING, EDITING, NOT_FOUND, SAVING, SAVED, SAVE_FAILED }

    data class UiState(
        val status: Status = Status.LOADING,
        val draft: Mode = Mode(),
        val isNew: Boolean = true,
        /** Every other mode, to show (and move) the apps they own. */
        val otherModes: List<Mode> = emptyList(),
        val providers: List<ServiceProvider> = emptyList(),
        val profiles: List<PromptProfile> = emptyList(),
        val installedApps: List<LauncherApp> = emptyList(),
        val errors: Set<ValidationError> = emptySet(),
        /** Errors are shown once a save was attempted. */
        val showErrors: Boolean = false,
    ) {
        /** The other mode that currently owns [packageName]; it moves to the draft on save. */
        fun ownerOf(packageName: String): Mode? = otherModes.firstOrNull { packageName in it.appPackages }
    }

    private data class Edit(
        val status: Status = Status.LOADING,
        val draft: Mode? = null,
        val showErrors: Boolean = false,
    )

    private val edit = MutableStateFlow(Edit())
    private val apps = MutableStateFlow<List<LauncherApp>>(emptyList())

    val uiState: StateFlow<UiState> = combine(
        edit,
        modesRepository.modes,
        providers,
        profiles,
        apps,
    ) { current, modes, providerList, profileList, appList ->
        val draft = current.draft ?: Mode()
        val others = modes.filter { it.id != draft.id }
        UiState(
            status = current.status,
            draft = draft,
            isNew = modeId == null,
            otherModes = others,
            providers = providerList,
            profiles = profileList,
            installedApps = appList,
            errors = validate(draft, others),
            showErrors = current.showErrors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            apps.value = try {
                loadApps()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Loading apps failed: ${e.javaClass.simpleName}")
                emptyList()
            }
        }
    }

    private suspend fun load() {
        val existing = modeId?.let { id -> modesRepository.modes.first().firstOrNull { it.id == id } }
        edit.value = when {
            modeId == null -> Edit(Status.EDITING, Mode(id = ModesViewModel.newModeId(), name = "", icon = Mode.Icon.CUSTOM))
            existing == null -> Edit(Status.NOT_FOUND)
            else -> Edit(Status.EDITING, existing)
        }
    }

    /** Applies [transform] to the draft; ignored until the mode loaded. */
    fun update(transform: (Mode) -> Mode) {
        edit.update { current ->
            val draft = current.draft ?: return@update current
            current.copy(draft = transform(draft), status = if (current.status == Status.SAVE_FAILED) Status.EDITING else current.status)
        }
    }

    /** Adds or removes [packageName]; an app owned by another mode is listed as moving here. */
    fun toggleApp(packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        update { mode ->
            val packages = if (pkg in mode.appPackages) mode.appPackages - pkg else mode.appPackages + pkg
            mode.copy(appPackages = packages)
        }
    }

    fun save() {
        val current = edit.value
        val draft = current.draft ?: return
        if (current.status == Status.SAVING || current.status == Status.SAVED) return
        edit.value = current.copy(status = Status.SAVING, showErrors = true)
        viewModelScope.launch {
            val status = try {
                val others = modesRepository.modes.first().filter { it.id != draft.id }
                when {
                    validate(draft, others).isNotEmpty() -> Status.EDITING
                    modesRepository.upsert(draft.copy(name = draft.name.trim())) -> Status.SAVED
                    else -> Status.SAVE_FAILED
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving mode failed: ${e.javaClass.simpleName}")
                Status.SAVE_FAILED
            }
            edit.update { it.copy(status = status) }
        }
    }

    companion object {
        private const val TAG = "ModeEditViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_NAME_LENGTH = 40

        /** Pure save checks: a unique, non-blank name and complete explicit provider/model choices. */
        fun validate(draft: Mode, otherModes: List<Mode>): Set<ValidationError> = buildSet {
            val name = draft.name.trim()
            when {
                name.isEmpty() -> add(ValidationError.NAME_BLANK)
                name.length > MAX_NAME_LENGTH -> add(ValidationError.NAME_TOO_LONG)
                otherModes.any { it.name.trim().equals(name, ignoreCase = true) } -> add(ValidationError.NAME_DUPLICATE)
            }
            if (draft.sttSelection.isIncomplete()) add(ValidationError.STT_MODEL_MISSING)
            if (draft.enhancementEnabled != false && draft.enhancementSelection.isIncomplete()) {
                add(ValidationError.ENHANCEMENT_MODEL_MISSING)
            }
        }

        private fun Mode.Selection?.isIncomplete(): Boolean =
            this != null && (providerId.isBlank() || modelId.isBlank())
    }
}
