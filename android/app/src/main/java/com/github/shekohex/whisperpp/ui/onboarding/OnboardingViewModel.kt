package com.github.shekohex.whisperpp.ui.onboarding

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.PreferenceKeys.MIC_PERMISSION_REQUESTED
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.domain.setup.SetupStatusChecker
import com.github.shekohex.whisperpp.ui.settings.common.PreferencesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException

class OnboardingViewModel(
    dataStore: DataStore<Preferences>,
    private val settingsRepository: SettingsRepository,
    private val setupStatusChecker: SetupStatusChecker,
) : PreferencesViewModel(dataStore) {

    constructor(container: AppContainer) : this(
        dataStore = container.settingsDataStore,
        settingsRepository = container.settingsRepository,
        setupStatusChecker = container.setupStatusChecker,
    )

    internal val uiState: StateFlow<OnboardingUiState> = combine(
        setupStatusChecker.status,
        settingsRepository.providers,
    ) { status, providers ->
        OnboardingUiState(status = status, hasProviders = providers.isNotEmpty())
    }.stateInScreen(OnboardingUiState())

    private val completedState = MutableStateFlow(false)

    /** True once the completion flag is persisted; the screen then leaves for Home. */
    val completed: StateFlow<Boolean> = completedState.asStateFlow()

    fun refresh() = setupStatusChecker.refresh()

    fun markMicRequested() = set(MIC_PERMISSION_REQUESTED, true)

    fun complete() {
        viewModelScope.launch {
            try {
                settingsRepository.setOnboardingCompleted()
            } catch (e: IOException) {
                // Still leave: at worst onboarding shows again on the next launch.
                Log.w(TAG, "Could not persist onboarding completion", e)
            }
            completedState.value = true
        }
    }

    private companion object {
        const val TAG = "Onboarding"
    }
}
