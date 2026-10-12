package com.github.shekohex.whisperpp.ui.enhancement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Global base prompt (edited as a saved draft) and the prompt profile list. */
class PromptsViewModel(container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    private val repository = container.settingsRepository

    val basePrompt: StateFlow<String?> = repository.globalBasePrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val promptProfiles: StateFlow<List<PromptProfile>> = repository.promptProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Unsaved base prompt edit; `null` while the user has not typed anything. */
    val draft: StateFlow<String?> = handle.getStateFlow(KEY_DRAFT, null)

    fun setDraft(value: String) {
        handle[KEY_DRAFT] = value
    }

    fun saveBasePrompt(onSaved: () -> Unit) {
        val value = draft.value ?: return
        viewModelScope.launch {
            repository.saveGlobalBasePrompt(value)
            handle[KEY_DRAFT] = null
            onSaved()
        }
    }

    fun deleteProfile(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePromptProfile(id)
            onDeleted()
        }
    }

    private companion object {
        const val KEY_DRAFT = "base_prompt_draft"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
