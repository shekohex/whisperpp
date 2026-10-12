package com.github.shekohex.whisperpp.ui.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.AppPromptMapping
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.ui.settings.common.LauncherApp
import com.github.shekohex.whisperpp.ui.settings.common.loadLauncherApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Installed apps with their prompt-profile mapping; supports bulk profile assignment. */
class AppMappingsViewModel(container: AppContainer, appContext: Context) : ViewModel() {
    private val repository = container.settingsRepository

    val appMappings: StateFlow<List<AppPromptMapping>> = repository.appPromptMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val promptProfiles: StateFlow<List<PromptProfile>> = repository.promptProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _launcherApps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val launcherApps: StateFlow<List<LauncherApp>> = _launcherApps.asStateFlow()

    init {
        viewModelScope.launch { _launcherApps.value = loadLauncherApps(appContext) }
    }

    /** Sets (or clears, when blank) the profile of every package in [packages]. */
    fun assignProfile(packages: Set<String>, profileId: String?, onDone: () -> Unit) {
        val normalizedProfileId = profileId?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val byPackage = repository.appPromptMappings.first().associateBy { it.packageName }.toMutableMap()
            packages.forEach { pkg ->
                byPackage[pkg] = (byPackage[pkg] ?: AppPromptMapping(packageName = pkg)).copy(profileId = normalizedProfileId)
            }
            repository.saveAppPromptMappings(byPackage.values.toList())
            onDone()
        }
    }

    /** Adds an empty mapping for a package typed by hand, then reports the normalized name. */
    fun addManual(packageName: String, onAdded: (String) -> Unit) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            repository.upsertAppPromptMapping(AppPromptMapping(packageName = normalized))
            onAdded(normalized)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
