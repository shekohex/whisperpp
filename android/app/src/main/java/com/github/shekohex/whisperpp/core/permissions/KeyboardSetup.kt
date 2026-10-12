package com.github.shekohex.whisperpp.core.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager

/** System entry points for enabling and selecting the Whisper++ keyboard (§4.6). */
object KeyboardSetup {
    private const val TAG = "KeyboardSetup"

    fun openInputMethodSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Input method settings unavailable", e)
        }
    }

    fun showInputMethodPicker(context: Context) {
        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
}
