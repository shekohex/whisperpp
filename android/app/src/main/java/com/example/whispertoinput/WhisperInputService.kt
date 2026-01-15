/*
 * This file is part of Whisper To Input, see <https://github.com/j3soon/whisper-to-input>.
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

package com.example.whispertoinput

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.ContextThemeWrapper
import android.content.Intent
import android.os.IBinder
import android.os.LocaleList
import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.util.Log
import androidx.appcompat.widget.PopupMenu
import android.widget.Toast
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.whispertoinput.keyboard.KeyboardState
import com.example.whispertoinput.keyboard.WhisperKeyboard
import com.example.whispertoinput.recorder.RecorderManager
import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val RECORDED_AUDIO_FILENAME_M4A = "recorded.m4a"
private const val RECORDED_AUDIO_FILENAME_OGG = "recorded.ogg"
private const val AUDIO_MEDIA_TYPE_M4A = "audio/mp4"
private const val AUDIO_MEDIA_TYPE_OGG = "audio/ogg"
private const val IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL = 28
private const val MENU_OPEN_SETTINGS = 1
private const val MENU_AUTO_RECORDING_START = 2
private const val MENU_AUTO_SWITCH_BACK = 3
private const val MENU_SWITCH_IME = 4
private const val TAG = "WhisperInputService"

class WhisperInputService : InputMethodService() {
    private val whisperKeyboard: WhisperKeyboard = WhisperKeyboard()
    private val whisperTranscriber: WhisperTranscriber = WhisperTranscriber()
    private lateinit var smartFixer: SmartFixer
    private var recorderManager: RecorderManager? = null
    private var recordedAudioFilename: String = ""
    private var audioMediaType: String = AUDIO_MEDIA_TYPE_M4A
    private var useOggFormat: Boolean = false
    private var isFirstTime: Boolean = true
    private var keyboardState: KeyboardState = KeyboardState.Ready

    private fun transcriptionCallback(text: String?, contextPrompt: String?) {
        if (!text.isNullOrEmpty()) {
            Log.d(TAG, "Transcription received length=${text.length}")
            
            CoroutineScope(Dispatchers.Main).launch {
                val processedText = try {
                    val smartFixEnabled = dataStore.data.map { preferences: Preferences ->
                        preferences[SMART_FIX_ENABLED] ?: false
                    }.first()
                    
                    if (smartFixEnabled) {
                        setKeyboardState(KeyboardState.SmartFixing)
                    }
                    
                    withContext(Dispatchers.IO) {
                        smartFixer.fix(text, contextPrompt)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Smart Fix failed in callback", e)
                    Toast.makeText(this@WhisperInputService, "Smart Fix Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    text // fallback
                }
                
                Log.d(TAG, "Final text length=${processedText.length}")
                currentInputConnection?.commitText(processedText, 1)
                
                val autoSwitchBack = dataStore.data.map { preferences: Preferences ->
                    preferences[AUTO_SWITCH_BACK] ?: false
                }.first()
                if (autoSwitchBack) {
                    onSwitchIme()
                }
                setKeyboardState(KeyboardState.Ready)
            }
        } else {
            Log.d(TAG, "Transcription empty")
            Toast.makeText(this, getString(R.string.toast_transcription_empty), Toast.LENGTH_SHORT).show()
            setKeyboardState(KeyboardState.Ready)
        }
    }

    private fun transcriptionExceptionCallback(message: String) {
        Log.e(TAG, "Transcription error: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setKeyboardState(KeyboardState.Ready)
    }

    private suspend fun updateAudioFormat() {
        val backend = dataStore.data.map { preferences: Preferences ->
            preferences[SPEECH_TO_TEXT_BACKEND] ?: getString(R.string.settings_option_openai_api)
        }.first()

        useOggFormat = backend == getString(R.string.settings_option_nvidia_nim)
        if (useOggFormat) {
            recordedAudioFilename = "${externalCacheDir?.absolutePath}/${RECORDED_AUDIO_FILENAME_OGG}"
            audioMediaType = AUDIO_MEDIA_TYPE_OGG
        } else {
            recordedAudioFilename = "${externalCacheDir?.absolutePath}/${RECORDED_AUDIO_FILENAME_M4A}"
            audioMediaType = AUDIO_MEDIA_TYPE_M4A
        }
    }

    private fun updateLanguageLabel() {
        CoroutineScope(Dispatchers.Main).launch {
            val languageCode = dataStore.data.map { preferences: Preferences ->
                preferences[LANGUAGE_CODE] ?: getString(R.string.settings_option_openai_api_default_language)
            }.first()
            val (label, _) = formatLanguageLabel(languageCode)
            whisperKeyboard.setLanguageLabel(label)
        }
    }

    private fun formatLanguageLabel(languageCode: String): Pair<String, Boolean> {
        val trimmed = languageCode.trim()
        if (trimmed.isEmpty() || trimmed.equals("auto", true)) {
            return Pair(getString(R.string.language_auto_label), true)
        }
        return Pair(trimmed.uppercase(Locale.getDefault()), false)
    }

    private fun setKeyboardState(state: KeyboardState) {
        keyboardState = state
        whisperKeyboard.render(state)
    }

    private fun onMicAction() {
        when (keyboardState) {
            KeyboardState.Ready -> startRecording()
            KeyboardState.Recording -> pauseRecording()
            KeyboardState.Paused -> resumeRecording()
            KeyboardState.Transcribing, KeyboardState.SmartFixing -> Unit
        }
    }

    private fun onSendAction() {
        if (keyboardState == KeyboardState.Paused) {
            startTranscription("")
        }
    }

    private fun onCancelAction() {
        when (keyboardState) {
            KeyboardState.Recording, KeyboardState.Paused -> confirmCancelAction { cancelRecording() }
            KeyboardState.Transcribing, KeyboardState.SmartFixing -> confirmCancelAction { cancelTranscription() }
            KeyboardState.Ready -> Unit
        }
    }

    private fun startRecording() {
        if (!recorderManager!!.allPermissionsGranted(this)) {
            launchMainActivity()
            setKeyboardState(KeyboardState.Ready)
            return
        }

        Log.d(TAG, "Start recording to $recordedAudioFilename")
        setKeyboardState(KeyboardState.Recording)
        recorderManager!!.start(this, recordedAudioFilename, useOggFormat)
    }

    private fun pauseRecording() {
        recorderManager!!.pause()
        setKeyboardState(KeyboardState.Paused)
    }

    private fun resumeRecording() {
        recorderManager!!.resume()
        setKeyboardState(KeyboardState.Recording)
    }

    private fun cancelRecording() {
        Log.d(TAG, "Cancel recording")
        recorderManager!!.stop()
        setKeyboardState(KeyboardState.Ready)
    }

    private fun startTranscription(attachToEnd: String) {
        Log.d(TAG, "Start transcription attachToEnd=$attachToEnd")
        recorderManager!!.stop()
        setKeyboardState(KeyboardState.Transcribing)

        CoroutineScope(Dispatchers.Main).launch {
            val useContext = dataStore.data.map { preferences: Preferences ->
                preferences[USE_CONTEXT] ?: false
            }.first()

            val contextPrompt = if (useContext) {
                // Get up to 500 characters before the cursor
                currentInputConnection?.getTextBeforeCursor(500, 0)?.toString()
            } else {
                null
            }

            whisperTranscriber.startAsync(
                this@WhisperInputService,
                recordedAudioFilename,
                audioMediaType,
                attachToEnd,
                contextPrompt,
                { transcriptionCallback(it, contextPrompt) },
                { transcriptionExceptionCallback(it) }
            )
        }
    }

    private fun cancelTranscription() {
        Log.d(TAG, "Cancel transcription")
        whisperTranscriber.stop()
        setKeyboardState(KeyboardState.Ready)
    }

    private fun confirmCancelAction(onConfirm: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val shouldConfirm = dataStore.data.map { preferences: Preferences ->
                preferences[CANCEL_CONFIRMATION] ?: true
            }.first()
            if (!shouldConfirm) {
                onConfirm()
                return@launch
            }
            val themedContext = ContextThemeWrapper(this@WhisperInputService, R.style.Theme_WhisperToInput)
            val dialog = android.app.AlertDialog.Builder(themedContext)
                .setTitle(getString(R.string.cancel_confirmation_title))
                .setMessage(getString(R.string.cancel_confirmation_message))
                .setPositiveButton(getString(R.string.cancel_confirmation_confirm)) { _, _ ->
                    onConfirm()
                }
                .setNegativeButton(getString(R.string.cancel_confirmation_dismiss), null)
                .create()
            
            // Set the window token and type for IME compatibility
            dialog.window?.let { window ->
                val lp = window.attributes
                lp.token = this@WhisperInputService.window?.window?.attributes?.token
                lp.type = android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD
                window.attributes = lp
            }
            
            dialog.show()
        }
    }

    override fun onCreateInputView(): View {
        // Initialize members with regard to this context
        recorderManager = RecorderManager(this)
        smartFixer = SmartFixer(this)

        // Preload conversion table
        ChineseUtils.preLoad(true, TransType.SIMPLE_TO_TAIWAN)
        ChineseUtils.preLoad(true, TransType.TAIWAN_TO_SIMPLE)

        // Initialize audio format based on backend setting
        CoroutineScope(Dispatchers.Main).launch {
            updateAudioFormat()
        }

        // Should offer ime switch?
        val shouldOfferImeSwitch: Boolean =
            if (Build.VERSION.SDK_INT >= IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL) {
                shouldOfferSwitchingToNextInputMethod()
            } else {
                val inputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                val token: IBinder? = window?.window?.attributes?.token
                inputMethodManager.shouldOfferSwitchingToNextInputMethod(token)
            }

        // Sets up recorder manager
        recorderManager!!.setOnUpdateMicrophoneAmplitude { amplitude ->
            onUpdateMicrophoneAmplitude(amplitude)
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_WhisperToInput)
        val dynamicContext = DynamicColors.wrapContextIfAvailable(themedContext)
        val themedInflater = layoutInflater.cloneInContext(dynamicContext)
        val keyboardView = whisperKeyboard.setup(
            themedInflater,
            shouldOfferImeSwitch,
            { onMicAction() },
            { onCancelAction() },
            { onSendAction() },
            { onDeleteText() },
            { onSwitchIme() },
            { anchor -> onOpenSettings(anchor) },
            { anchor -> onLanguageClick(anchor) },
        )
        updateLanguageLabel()
        setKeyboardState(KeyboardState.Ready)
        return keyboardView
    }

    private fun onUpdateMicrophoneAmplitude(amplitude: Int) {
        whisperKeyboard.updateMicrophoneAmplitude(amplitude)
    }

    private fun onDeleteText() {
        val inputConnection = currentInputConnection ?: return
        val selectedText = inputConnection.getSelectedText(0)

        // Deletes cursor pointed text, or all selected texts
        if (TextUtils.isEmpty(selectedText)) {
            inputConnection.deleteSurroundingText(1, 0)
        } else {
            inputConnection.commitText("", 1)
        }
    }

    private fun onSwitchIme() {
        // Before API Level 28, switchToPreviousInputMethod() was not available
        if (Build.VERSION.SDK_INT >= IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL) {
            switchToPreviousInputMethod()
        } else {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val token: IBinder? = window?.window?.attributes?.token
            inputMethodManager.switchToLastInputMethod(token)
        }

    }

    private fun onOpenSettings(anchor: View) {
        showSettingsMenu(anchor)
    }

    private fun onLanguageClick(anchor: View) {
        showLanguageMenu(anchor)
    }

    private fun showLanguageMenu(anchor: View) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentLanguage = dataStore.data.map { it[LANGUAGE_CODE] ?: "auto" }.first()
            
            // Get system languages
            val systemLocales = LocaleList.getDefault()
            val languages = mutableListOf<Pair<String, String>>()
            languages.add("auto" to getString(R.string.language_auto_label))
            languages.add("en" to "English")
            languages.add("ar" to "Arabic")
            
            for (i in 0 until systemLocales.size()) {
                val locale = systemLocales.get(i)
                val code = locale.language
                val name = locale.displayLanguage.replaceFirstChar { it.uppercase() }
                if (languages.none { it.first == code }) {
                    languages.add(code to name)
                }
            }

            val themedContext = ContextThemeWrapper(this@WhisperInputService, R.style.Theme_WhisperToInput)
            val menu = PopupMenu(themedContext, anchor)
            val groupId = 1
            languages.forEachIndexed { index, (code, name) ->
                menu.menu.add(groupId, index, index, name).apply {
                    isCheckable = true
                    isChecked = code == currentLanguage
                }
            }
            menu.menu.setGroupCheckable(groupId, true, true)

            menu.setOnMenuItemClickListener { item ->
                val selected = languages[item.itemId]
                CoroutineScope(Dispatchers.Main).launch {
                    dataStore.edit { it[LANGUAGE_CODE] = selected.first }
                    updateLanguageLabel()
                }
                true
            }
            menu.show()
        }
    }

    private fun showSettingsMenu(anchor: View) {
        CoroutineScope(Dispatchers.Main).launch {
            val autoRecordingStart = dataStore.data.map { preferences: Preferences ->
                preferences[AUTO_RECORDING_START] ?: true
            }.first()
            val autoSwitchBack = dataStore.data.map { preferences: Preferences ->
                preferences[AUTO_SWITCH_BACK] ?: false
            }.first()
            val themedContext = ContextThemeWrapper(this@WhisperInputService, R.style.Theme_WhisperToInput)
            val menu = PopupMenu(themedContext, anchor)
            menu.menu.add(0, MENU_OPEN_SETTINGS, 0, getString(R.string.menu_open_settings))
            menu.menu.add(0, MENU_AUTO_RECORDING_START, 1, getString(R.string.menu_auto_recording_start)).apply {
                isCheckable = true
                isChecked = autoRecordingStart
            }
            menu.menu.add(0, MENU_AUTO_SWITCH_BACK, 2, getString(R.string.menu_auto_switch_back)).apply {
                isCheckable = true
                isChecked = autoSwitchBack
            }
            menu.menu.add(0, MENU_SWITCH_IME, 3, getString(R.string.menu_switch_ime))
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_OPEN_SETTINGS -> {
                        launchMainActivity()
                        true
                    }
                    MENU_AUTO_RECORDING_START -> {
                        val newValue = !item.isChecked
                        item.isChecked = newValue
                        CoroutineScope(Dispatchers.Main).launch {
                            dataStore.edit { settings ->
                                settings[AUTO_RECORDING_START] = newValue
                            }
                        }
                        true
                    }
                    MENU_AUTO_SWITCH_BACK -> {
                        val newValue = !item.isChecked
                        item.isChecked = newValue
                        CoroutineScope(Dispatchers.Main).launch {
                            dataStore.edit { settings ->
                                settings[AUTO_SWITCH_BACK] = newValue
                            }
                        }
                        true
                    }
                    MENU_SWITCH_IME -> {
                        onSwitchIme()
                        true
                    }
                    else -> false
                }
            }
            menu.show()
        }
    }

    private fun onEnter() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
    }

    private fun onSpaceBar() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(" ", 1)
    }

    private fun shouldShowRetry(): Boolean {
        val exists = File(recordedAudioFilename).exists()
        return exists
    }

    // Opens up app MainActivity
    private fun launchMainActivity() {
        val dialogIntent = Intent(this, MainActivity::class.java)
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(dialogIntent)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        whisperTranscriber.stop()
        setKeyboardState(KeyboardState.Ready)
        recorderManager!!.stop()

        // If this is the first time calling onWindowShown, it means this IME is just being switched to.
        // Automatically starts recording after switching to Whisper Input. (if settings enabled)
        // Dispatch a coroutine to do this task.
        CoroutineScope(Dispatchers.Main).launch {
            // Update audio format based on current backend setting
            updateAudioFormat()
            updateLanguageLabel()
            if (!isFirstTime) return@launch
            isFirstTime = false
            val isAutoStartRecording = dataStore.data.map { preferences: Preferences ->
                preferences[AUTO_RECORDING_START] ?: true
            }.first()
            if (isAutoStartRecording) {
                startRecording()
            }
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        whisperTranscriber.stop()
        setKeyboardState(KeyboardState.Ready)
        recorderManager!!.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        whisperTranscriber.stop()
        setKeyboardState(KeyboardState.Ready)
        recorderManager!!.stop()
    }
}
