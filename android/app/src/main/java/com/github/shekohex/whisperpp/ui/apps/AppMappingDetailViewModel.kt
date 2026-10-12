package com.github.shekohex.whisperpp.ui.apps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.AppPromptMapping
import com.github.shekohex.whisperpp.data.ProviderModelOverride
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.di.AppContainer
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Edits one app's mapping. The unsaved mapping is the draft (kept in [SavedStateHandle]); empty
 * override fields mean "inherit" and are dropped on save.
 */
class AppMappingDetailViewModel(
    container: AppContainer,
    private val handle: SavedStateHandle,
    val packageName: String,
) : ViewModel() {
    private val repository = container.settingsRepository
    private val gson = Gson()

    val providers: StateFlow<List<ServiceProvider>> = repository.providers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val promptProfiles: StateFlow<List<PromptProfile>> = repository.promptProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Whether a saved mapping exists (enables "Remove mapping"). */
    val hasSavedMapping: StateFlow<Boolean> = repository.appPromptMappings
        .map { mappings -> mappings.any { it.packageName == packageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private val _draft = MutableStateFlow(
        handle.get<String>(KEY_DRAFT)?.let { runCatching { gson.fromJson(it, AppPromptMapping::class.java) }.getOrNull() }
            ?: AppPromptMapping(packageName = packageName)
    )
    val draft: StateFlow<AppPromptMapping> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(handle.get<String>(KEY_DRAFT) != null)

    /** Save stays disabled until the stored mapping has been read into the draft. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        if (!_loaded.value) {
            viewModelScope.launch {
                repository.appPromptMappings.first().firstOrNull { it.packageName == packageName }
                    ?.let { existing -> update { existing } }
                _loaded.value = true
            }
        }
    }

    fun update(transform: (AppPromptMapping) -> AppPromptMapping) {
        val next = _draft.updateAndGet(transform)
        handle[KEY_DRAFT] = gson.toJson(next)
    }

    fun save(onSaved: () -> Unit) {
        val current = _draft.value
        val normalized = current.copy(
            packageName = packageName,
            profileId = current.profileId?.trim()?.takeIf { it.isNotBlank() },
            appPromptAppend = current.appPromptAppend?.trim()?.takeIf { it.isNotBlank() },
            sttOverride = current.sttOverride.normalized(),
            textOverride = current.textOverride.normalized(),
        )
        viewModelScope.launch {
            repository.upsertAppPromptMapping(normalized)
            onSaved()
        }
    }

    fun remove(onRemoved: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAppPromptMapping(packageName)
            onRemoved()
        }
    }

    private fun ProviderModelOverride?.normalized(): ProviderModelOverride? {
        val p = this?.providerId?.trim().orEmpty()
        val m = this?.modelId?.trim().orEmpty()
        return if (p.isBlank() && m.isBlank()) null else ProviderModelOverride(providerId = p, modelId = m)
    }

    private companion object {
        const val KEY_DRAFT = "app_mapping_draft"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
