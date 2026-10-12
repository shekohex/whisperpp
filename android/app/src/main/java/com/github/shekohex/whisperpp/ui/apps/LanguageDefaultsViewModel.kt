package com.github.shekohex.whisperpp.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.LanguageProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Per-language STT and text defaults; every change is saved immediately. */
class LanguageDefaultsViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.settingsRepository

    val providers: StateFlow<List<ServiceProvider>> = repository.providers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val languageDefaults: StateFlow<List<LanguageProfile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Replaces the rule for [updated]'s language (case-insensitive) or appends a new one. */
    fun upsert(updated: LanguageProfile) {
        val code = updated.languageCode.trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            val next = repository.profiles.first().toMutableList()
            val index = next.indexOfFirst { it.languageCode.equals(code, ignoreCase = true) }
            val effective = updated.copy(languageCode = code)
            if (index >= 0) next[index] = effective else next.add(effective)
            repository.saveProfiles(next)
        }
    }

    fun delete(languageCode: String, onDeleted: () -> Unit) {
        val code = languageCode.trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            repository.saveProfiles(repository.profiles.first().filterNot { it.languageCode.equals(code, ignoreCase = true) })
            onDeleted()
        }
    }

    /** Adds an empty rule for [languageCode]; reports `false` when the language already exists. */
    fun add(languageCode: String, onResult: (added: Boolean) -> Unit) {
        val code = languageCode.trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            val current = repository.profiles.first()
            if (current.any { it.languageCode.equals(code, ignoreCase = true) }) {
                onResult(false)
                return@launch
            }
            repository.saveProfiles(
                current + LanguageProfile(
                    languageCode = code,
                    transcriptionProviderId = "",
                    transcriptionModelId = "",
                    enhancementProviderId = "",
                    enhancementModelId = "",
                )
            )
            onResult(true)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
