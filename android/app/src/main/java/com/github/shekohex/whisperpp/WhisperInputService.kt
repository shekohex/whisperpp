/*
 * This file is part of Whisper++, see <https://github.com/shekohex/whisperpp>.
 *
 * Copyright (c) 2023-2025 Yan-Bin Diau, Johnson Sun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.shekohex.whisperpp

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.github.shekohex.whisperpp.ime.ImeComponents
import com.github.shekohex.whisperpp.ime.ImeController
import com.github.shekohex.whisperpp.ime.ImeHost
import com.github.shekohex.whisperpp.ime.ImeViewLifecycle
import com.github.shekohex.whisperpp.ime.applyEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus

private const val IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL = 28
private const val TAG = "WhisperInputService"

/** Thin Android adapter: IME lifecycle, wiring of the `ime/` components and forwarding of platform events. */
class WhisperInputService : InputMethodService() {
    private val serviceScope = MainScope() + Dispatchers.Main.immediate
    private lateinit var components: ImeComponents
    private lateinit var controller: ImeController

    // Owners for the current input view only; replaced on every onCreateInputView().
    private var viewLifecycle: ImeViewLifecycle? = null
    // Saved state carried from a destroyed input view to its replacement (e.g. across rotation).
    private var savedViewState: Bundle? = null
    private var inputViewGeneration: Int = 0

    private val host = object : ImeHost {
        override val context: Context
            get() = this@WhisperInputService
        override val inputConnection: InputConnection?
            get() = currentInputConnection
        override val editorInfo: EditorInfo?
            get() = currentInputEditorInfo
        override val decorView: View?
            get() = window?.window?.decorView

        override fun deleteBackward() = sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)

        override fun switchToPreviousIme() {
            if (Build.VERSION.SDK_INT >= IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL) switchToPreviousInputMethod()
            else {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                @Suppress("DEPRECATION")
                imm.switchToLastInputMethod(window?.window?.attributes?.token)
            }
        }

        override fun setKeepScreenOn(keepOn: Boolean) {
            val flags = window?.window ?: return
            if (keepOn) flags.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else flags.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun hideKeyboard() = requestHideSelf(0)
    }

    override fun onCreate() {
        super.onCreate()
        logLifecycle("onCreate")
        components = ImeComponents(host, serviceScope)
        controller = ImeController(host, serviceScope, components)
        controller.start()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        logLifecycle("onStartInput restarting=$restarting")
        controller.onStartInput(attribute, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        viewLifecycle?.onShown()
        logLifecycle("onStartInputView restarting=$restarting")
        controller.onStartInputView(info)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        viewLifecycle?.onHidden()
        logLifecycle("onFinishInputView finishingInput=$finishingInput")
        controller.onFinishInputView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val generationBefore = inputViewGeneration
        super.onConfigurationChanged(newConfig)
        // uiMode / orientation / density / locale changes need a fresh composition with new resources.
        // The framework already rebuilds the view when it restarts the IME for this config; only do it
        // ourselves when it did not, so the view is never built twice.
        if (inputViewGeneration == generationBefore) {
            setInputView(onCreateInputView())
            if (isInputViewShown) viewLifecycle?.onShown()
        }
        logLifecycle("onConfigurationChanged rebuiltByFramework=${inputViewGeneration != generationBefore}")
    }

    /**
     * The framework hides the soft input view while a hardware keyboard is attached. This keyboard has
     * no keys to replace — it is a voice bar — so it must stay visible whenever the editor wants input.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        val shown = super.onEvaluateInputViewShown() || isInputViewHiddenOnlyByHardwareKeyboard()
        logLifecycle("onEvaluateInputViewShown -> $shown")
        return shown
    }

    private fun isInputViewHiddenOnlyByHardwareKeyboard(): Boolean {
        val config = resources.configuration
        return config.keyboard != Configuration.KEYBOARD_NOKEYS &&
            config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    }

    /** Extract (fullscreen) mode in landscape would replace the bar with an empty extract view. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val viewOwners = replaceViewLifecycle()
        inputViewGeneration += 1
        logLifecycle("onCreateInputView generation=$inputViewGeneration")
        window?.window?.applyEdgeToEdge(resources.configuration)
        return controller.createInputView(viewOwners)
    }

    /** Destroys the previous input view's owners (saving its state) and creates fresh ones. */
    private fun replaceViewLifecycle(): ImeViewLifecycle {
        viewLifecycle?.let { previous ->
            val outState = Bundle()
            previous.destroy(outState)
            savedViewState = outState
        }
        return ImeViewLifecycle(savedViewState).also { viewLifecycle = it }
    }

    private fun logLifecycle(event: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "lifecycle: $event view=${viewLifecycle?.currentState ?: "none"}")
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onFieldUpdated()
    }

    override fun onUpdateExtractedText(token: Int, text: ExtractedText?) {
        super.onUpdateExtractedText(token, text)
        controller.onFieldUpdated()
    }

    override fun onFinishInput() {
        controller.onFieldLeft()
        super.onFinishInput()
    }

    override fun onUnbindInput() {
        controller.onFieldLeft()
        super.onUnbindInput()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && controller.onBackDown()) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            controller.onBackUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        logLifecycle("onWindowShown")
        controller.onWindowShown()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        logLifecycle("onWindowHidden")
        controller.onWindowHidden()
    }

    override fun onDestroy() {
        controller.onFieldLeft()
        super.onDestroy()
        logLifecycle("onDestroy")
        components.disclosureGate.cancel()
        viewLifecycle?.destroy()
        viewLifecycle = null
        savedViewState = null
        controller.onDestroy()
        serviceScope.cancel()
    }
}
