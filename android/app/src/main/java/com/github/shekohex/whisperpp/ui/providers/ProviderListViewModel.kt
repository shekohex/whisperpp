package com.github.shekohex.whisperpp.ui.providers

import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.PreferenceKeys.SPEECH_TO_TEXT_BACKEND
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.ui.settings.common.PreferencesViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ProviderListViewModel(private val container: AppContainer) : PreferencesViewModel(container.settingsDataStore) {
    private val repository = container.settingsRepository

    /** `null` until the first read completes, so the empty state is never shown spuriously. */
    val providers: StateFlow<List<ServiceProvider>?> = repository.providers.stateInScreen(null)

    fun selectActive(provider: ServiceProvider) = set(SPEECH_TO_TEXT_BACKEND, provider.id)

    /**
     * Deletes [provider] unless an active selection still uses it; returns the blocking selection
     * names in that case so the screen can explain why.
     */
    fun delete(provider: ServiceProvider): List<String> {
        val refs = prefs.value?.let { selectionReferencesProvider(provider.id, it) }.orEmpty()
        if (refs.isNotEmpty()) return refs
        viewModelScope.launch {
            val remaining = repository.providers.first().filterNot { it.id == provider.id }
            repository.saveProviders(remaining)
            container.secretsStore.clearProviderApiKey(provider.id)
            if (prefs.value?.get(SPEECH_TO_TEXT_BACKEND) == provider.id) {
                edit { it.remove(SPEECH_TO_TEXT_BACKEND) }
            }
        }
        return emptyList()
    }

    /** Adds a copy of [preset] and reports its new id so the editor can open for the API key. */
    fun addPreset(preset: ServiceProvider, onAdded: (String) -> Unit) {
        viewModelScope.launch {
            val newProvider = preset.copy(id = UUID.randomUUID().toString())
            repository.saveProviders(repository.providers.first() + newProvider)
            onAdded(newProvider.id)
        }
    }
}
