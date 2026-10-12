package com.github.shekohex.whisperpp.ui.enhancement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Create/edit a prompt profile. Name and append are drafts kept in [SavedStateHandle]. */
class PromptEditViewModel(
    container: AppContainer,
    private val handle: SavedStateHandle,
    profileId: String?,
) : ViewModel() {
    private val repository = container.settingsRepository

    val isNew: Boolean = profileId.isNullOrBlank()
    private val effectiveId: String = profileId?.takeUnless { it.isBlank() }
        ?: handle.get<String>(KEY_NEW_ID)
        ?: UUID.randomUUID().toString().also { handle[KEY_NEW_ID] = it }

    val name: StateFlow<String> = handle.getStateFlow(KEY_NAME, "")
    val append: StateFlow<String> = handle.getStateFlow(KEY_APPEND, "")

    private val _notFound = MutableStateFlow(false)

    /** True when editing an id that no longer exists; the screen closes itself. */
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    init {
        if (!isNew && handle.get<Boolean>(KEY_LOADED) != true) {
            viewModelScope.launch {
                val existing = repository.promptProfiles.first().firstOrNull { it.id == effectiveId }
                if (existing == null) {
                    _notFound.value = true
                } else {
                    handle[KEY_NAME] = existing.name
                    handle[KEY_APPEND] = existing.promptAppend
                    handle[KEY_LOADED] = true
                }
            }
        }
    }

    fun setName(value: String) {
        handle[KEY_NAME] = value
    }

    fun setAppend(value: String) {
        handle[KEY_APPEND] = value
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.upsertPromptProfile(PromptProfile(id = effectiveId, name = name.value, promptAppend = append.value))
            onSaved()
        }
    }

    private companion object {
        const val KEY_NAME = "prompt_name"
        const val KEY_APPEND = "prompt_append"
        const val KEY_LOADED = "prompt_loaded"
        const val KEY_NEW_ID = "prompt_new_id"
    }
}
