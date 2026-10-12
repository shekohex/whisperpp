package com.github.shekohex.whisperpp.ui.privacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.PreferenceKeys.DISCLOSURE_SHOWN_COMMAND_TEXT
import com.github.shekohex.whisperpp.data.PreferenceKeys.DISCLOSURE_SHOWN_DICTATION_AUDIO
import com.github.shekohex.whisperpp.data.PreferenceKeys.DISCLOSURE_SHOWN_ENHANCEMENT_TEXT
import com.github.shekohex.whisperpp.data.PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.privacy.SendPolicyRepository
import com.github.shekohex.whisperpp.ui.settings.common.LauncherApp
import com.github.shekohex.whisperpp.ui.settings.common.PreferencesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Disclosures, diagnostics and the per-app external send policy. */
class PrivacySafetyViewModel(
    dataStore: DataStore<Preferences>,
    settingsRepository: SettingsRepository,
    private val sendPolicyRepository: SendPolicyRepository,
    loadApps: suspend () -> List<LauncherApp>,
) : PreferencesViewModel(dataStore) {

    val rules: StateFlow<Map<String, Boolean>> = sendPolicyRepository.getAllRulesFlow().stateInScreen(emptyMap())
    val providers: StateFlow<List<ServiceProvider>> = settingsRepository.providers.stateInScreen(emptyList())

    private val _launcherApps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val launcherApps: StateFlow<List<LauncherApp>> = _launcherApps.asStateFlow()

    init {
        viewModelScope.launch { _launcherApps.value = loadApps() }
    }

    fun setVerboseNetworkLogs(enabled: Boolean) = set(VERBOSE_NETWORK_LOGS_ENABLED, enabled)

    fun resetDisclosures() = edit { prefs ->
        prefs[DISCLOSURE_SHOWN_DICTATION_AUDIO] = false
        prefs[DISCLOSURE_SHOWN_ENHANCEMENT_TEXT] = false
        prefs[DISCLOSURE_SHOWN_COMMAND_TEXT] = false
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch { sendPolicyRepository.setBlocked(normalized, blocked) }
    }
}
