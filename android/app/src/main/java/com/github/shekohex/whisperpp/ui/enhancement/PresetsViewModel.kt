package com.github.shekohex.whisperpp.ui.enhancement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_CLEANUP
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Default transform presets for enhancement and command mode. */
class PresetsViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.settingsRepository

    val enhancementPresetId: StateFlow<String?> = repository.enhancementPresetId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TRANSFORM_PRESET_ID_CLEANUP)

    val commandPresetId: StateFlow<String?> = repository.commandPresetId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TRANSFORM_PRESET_ID_TONE_REWRITE)

    fun setEnhancementPreset(id: String) {
        viewModelScope.launch { repository.setEnhancementPresetId(id) }
    }

    fun setCommandPreset(id: String) {
        viewModelScope.launch { repository.setCommandPresetId(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
