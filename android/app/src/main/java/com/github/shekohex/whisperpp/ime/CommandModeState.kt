package com.github.shekohex.whisperpp.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.shekohex.whisperpp.command.CommandStage
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE

/** What the keyboard shows for command mode. Written by [CommandModeController] only. */
class CommandModeState {
    var active by mutableStateOf(false)
        internal set
    var stage by mutableStateOf(CommandStage.WAITING)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var undoAvailable by mutableStateOf(false)
        private set
    var undoRemainingMs by mutableLongStateOf(0L)
        private set
    var selectedPresetId by mutableStateOf(TRANSFORM_PRESET_ID_TONE_REWRITE)
        internal set
    var clipboardPreview by mutableStateOf("")
        private set
    var clipboardCharCount by mutableIntStateOf(0)
        private set
    var clipboardIsLarge by mutableStateOf(false)
        private set
    var clipboardUnavailableReason by mutableStateOf<String?>(null)
        private set
    var clipboardAttemptsRemaining by mutableIntStateOf(CLIPBOARD_ATTEMPTS)
        internal set

    fun setStage(stage: CommandStage, error: String? = null) {
        this.stage = stage
        errorMessage = error
    }

    fun setUndo(available: Boolean, remainingMs: Long) {
        undoAvailable = available
        undoRemainingMs = remainingMs
    }

    fun showClipboard(preview: CommandClipboardPreview) {
        clipboardPreview = preview.snippet
        clipboardCharCount = preview.charCount
        clipboardIsLarge = preview.isLarge
        clipboardUnavailableReason = null
    }

    fun clearClipboard(unavailableReason: String? = null) {
        clipboardPreview = ""
        clipboardCharCount = 0
        clipboardIsLarge = false
        clipboardUnavailableReason = unavailableReason
    }

    companion object {
        const val CLIPBOARD_ATTEMPTS = 2
    }
}
