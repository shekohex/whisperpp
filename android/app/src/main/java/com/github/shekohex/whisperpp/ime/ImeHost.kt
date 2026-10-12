package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/** What the keyboard components need from the Android `InputMethodService` that hosts them. */
interface ImeHost {
    /** The service itself; valid from `onCreate` until `onDestroy`. */
    val context: Context
    val inputConnection: InputConnection?
    val editorInfo: EditorInfo?
    val decorView: View?

    /** Sends a backspace key press to the focused editor. */
    fun deleteBackward()

    /** Switches to the previously used input method. */
    fun switchToPreviousIme()

    /** Keeps the screen on while the keyboard window is showing; cleared with `false`. */
    fun setKeepScreenOn(keepOn: Boolean)

    /** Hides the keyboard window. */
    fun hideKeyboard()
}
