package com.github.shekohex.whisperpp.domain.dictation

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** How to "press send" in an editor after a dictation was inserted (VoiceInk auto-send, §5 item 11). */
sealed interface AutoSend {
    /**
     * `performEditorAction([actionId])`; when it is rejected and [enterFallback] is set, an Enter key
     * press instead. Multiline editors never get the fallback (Enter would only add a newline).
     */
    data class EditorAction(val actionId: Int, val enterFallback: Boolean) : AutoSend

    /** The editor has no send-like action (NONE, NO_ENTER_ACTION, or multiline without one): leave the text alone. */
    data object Skip : AutoSend

    companion object {
        /** Delay so the editor applies the insert before the send (VoiceInk uses 150 ms). */
        const val DELAY_MS = 150L

        private val SEND_LIKE_ACTIONS = setOf(
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_DONE,
        )

        /** Decision from the editor's `imeOptions` and `inputType`. */
        fun forEditor(imeOptions: Int, inputType: Int): AutoSend {
            if (inputType == InputType.TYPE_NULL) return Skip
            // The editor asked for Enter not to trigger its action.
            if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return Skip
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val multiline = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            if (action in SEND_LIKE_ACTIONS) return EditorAction(action, enterFallback = !multiline)
            if (action == EditorInfo.IME_ACTION_NONE) return Skip
            // A multiline field without an explicit send-like action has nothing to press.
            if (multiline) return Skip
            return EditorAction(EditorInfo.IME_ACTION_SEND, enterFallback = true)
        }
    }
}
