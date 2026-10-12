package com.github.shekohex.whisperpp.ime

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.github.shekohex.whisperpp.keyboard.KeyboardState

/** Compose state of the keyboard that is not owned by a more specific holder; projected from the session. */
class KeyboardUiState {
    val keyboardState = mutableStateOf(KeyboardState.Ready)
    val micLevel = mutableFloatStateOf(0f)
    val recordingTimeMs = mutableLongStateOf(0L)
    val showLongPressHint = mutableStateOf(false)
    val tapToRecord = mutableStateOf(true)
    val commandModeEnabled = mutableStateOf(false)
    val showModeOnKeyboard = mutableStateOf(true)
    val sessionStarting = mutableStateOf(false)
    val pendingCommitText = mutableStateOf<String?>(null)
    val micPermissionMissing = mutableStateOf(false)

    /** Confirm action of the open "discard recording?" sheet; null when it is closed. */
    val pendingCancelConfirm = mutableStateOf<(() -> Unit)?>(null)

    /** Back closes the topmost in-keyboard sheet first (set by KeyboardScreen). */
    var overlayBack: (() -> Unit)? = null
}
