/*
 * This file is part of Whisper++, see <https://github.com/j3soon/whisper-to-input>.
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

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.IBinder
import android.os.LocaleList
import android.view.HapticFeedbackConstants
import android.media.AudioManager
import android.media.MediaPlayer
import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.WindowCompat
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.recorder.RecorderManager
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardScreen
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
private const val TAG = "WhisperInputService"

class WhisperInputService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val whisperTranscriber: WhisperTranscriber = WhisperTranscriber()
    private lateinit var smartFixer: SmartFixer
    private var recorderManager: RecorderManager? = null
    private var recordedAudioFilename: String = ""
    private var audioMediaType: String = AUDIO_MEDIA_TYPE_M4A
    private var useOggFormat: Boolean = false
    private var isFirstTime: Boolean = true
    
    private lateinit var repository: SettingsRepository

    // Lifecycle & SavedState & ViewModelStore
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store
    
    // Compose State
    private var keyboardState = mutableStateOf(KeyboardState.Ready)
    private var languageLabel = mutableStateOf("")
    private var microphoneAmplitude = mutableStateOf(0)
    private var shouldOfferImeSwitch = mutableStateOf(false)
    private var recordingTimeMs = mutableStateOf(0L)
    private var showLongPressHint = mutableStateOf(false)
    private var timerJob: Job? = null
    private val activeMediaPlayers = mutableListOf<MediaPlayer>()

    override fun onCreate() {

        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        repository = SettingsRepository(dataStore)
        recorderManager = RecorderManager(this)
        smartFixer = SmartFixer(this)
    }

    private fun transcriptionCallback(text: String?, contextPrompt: String?) {
        if (!text.isNullOrEmpty()) {
            Log.d(TAG, "Transcription received length=${text.length}")
            playCustomSound(R.raw.rec_done)
            
            CoroutineScope(Dispatchers.Main).launch {
                val processedText = try {
                    val prefs = dataStore.data.first()
                    val smartFixEnabled = prefs[SMART_FIX_ENABLED] ?: false
                    
                    if (smartFixEnabled) {
                        setKeyboardState(KeyboardState.SmartFixing)
                        performFeedback()
                        
                        val providerId = prefs[SMART_FIX_BACKEND] ?: ""
                        val modelId = prefs[SMART_FIX_MODEL] ?: ""
                        val providers = repository.providers.first()
                        val provider = providers.find { it.id == providerId }
                        
                        if (provider != null) {
                            val temperature = if (provider.temperature > 0) provider.temperature else prefs[SMART_FIX_TEMPERATURE] ?: 0.0f
                            val promptTemplate = if (provider.prompt.isNotEmpty()) provider.prompt else prefs[SMART_FIX_PROMPT] ?: ""

                            Log.d(TAG, "Smart Fix using Provider: ${provider.name}, Model: $modelId")

                            withContext(Dispatchers.IO) {
                                smartFixer.fix(text, contextPrompt, provider, modelId, temperature, promptTemplate)
                            }
                        } else {
                            text
                        }
                    } else {
                        text
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Smart Fix failed", e)
                    text // fallback
                }
                
                if (!processedText.isNullOrEmpty()) {
                    currentInputConnection?.commitText(processedText, 1)
                }
                
                val autoSwitchBack = dataStore.data.map { it[AUTO_SWITCH_BACK] ?: false }.first()
                if (autoSwitchBack && !processedText.isNullOrEmpty()) {
                    delay(150)
                    onSwitchIme()
                }
                setKeyboardState(KeyboardState.Ready)
                performFeedback()
            }
        } else {
            setKeyboardState(KeyboardState.Ready)
            if (text != null) {
                Toast.makeText(this, getString(R.string.toast_transcription_empty), Toast.LENGTH_SHORT).show()
                performFeedback()
            }
        }
    }

    private fun transcriptionExceptionCallback(message: String) {
        Log.e(TAG, "Transcription error: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setKeyboardState(KeyboardState.Ready)
    }

    private fun startTranscription(attachToEnd: String) {
        performFeedback(customSoundId = R.raw.rec_stop)
        recorderManager?.stop()
        setKeyboardState(KeyboardState.Transcribing)

        CoroutineScope(Dispatchers.Main).launch {
            val prefs = dataStore.data.first()
            val useContext = prefs[USE_CONTEXT] ?: false
            val contextPrompt = if (useContext) currentInputConnection?.getTextBeforeCursor(500, 0)?.toString() else null
            
            val currentLanguage = prefs[LANGUAGE_CODE] ?: "auto"
            val defaultBackendId = prefs[SPEECH_TO_TEXT_BACKEND] ?: ""
            val providers = repository.providers.first()
            
            val languageProvider = if (currentLanguage != "auto") {
                providers.find { it.languageCode.equals(currentLanguage, ignoreCase = true) }
            } else null
            
            val provider = languageProvider ?: providers.find { it.id == defaultBackendId }
            
            if (provider == null) {
                Toast.makeText(this@WhisperInputService, "No Provider Selected", Toast.LENGTH_LONG).show()
                setKeyboardState(KeyboardState.Ready)
                return@launch
            }
            
            val modelId = if (provider.models.isNotEmpty()) provider.models.first().id else prefs[MODEL] ?: "whisper-1"
            val staticPrompt = if (provider.prompt.isNotEmpty()) provider.prompt else prefs[PROMPT] ?: ""
            val temperature = provider.temperature
            val postprocessing = prefs[POSTPROCESSING] ?: getString(R.string.settings_option_no_conversion)
            val addTrailingSpace = prefs[ADD_TRAILING_SPACE] ?: false
            val timeout = provider.timeout

            whisperTranscriber.startAsync(
                this@WhisperInputService, recordedAudioFilename, audioMediaType,
                attachToEnd, contextPrompt, provider, modelId, postprocessing,
                addTrailingSpace, timeout, staticPrompt, temperature,
                { transcriptionCallback(it, contextPrompt) },
                { transcriptionExceptionCallback(it) }
            )
        }
    }

    private fun decorViewOwners(decor: View) {
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
    }

    override fun onCreateInputView(): View {
        ChineseUtils.preLoad(true, TransType.SIMPLE_TO_TAIWAN)
        ChineseUtils.preLoad(true, TransType.TAIWAN_TO_SIMPLE)
        CoroutineScope(Dispatchers.Main).launch { updateAudioFormat() }

        shouldOfferImeSwitch.value = if (Build.VERSION.SDK_INT >= IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL) {
            shouldOfferSwitchingToNextInputMethod()
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("DEPRECATION")
            imm.shouldOfferSwitchingToNextInputMethod(window?.window?.attributes?.token)
        }

        recorderManager?.setOnUpdateMicrophoneAmplitude { microphoneAmplitude.value = it }

        // Ensure the window's decor view has the owners for components that search up the tree
        window?.window?.let { win ->
            decorViewOwners(win.decorView)
            
            // Edge-to-Edge Support
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.navigationBarColor = Color.TRANSPARENT
            
            val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightNavigationBars = !isNightMode
        }

        return ComposeView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@WhisperInputService)
            setViewTreeSavedStateRegistryOwner(this@WhisperInputService)
            setViewTreeViewModelStoreOwner(this@WhisperInputService)

            setContent {
                WhisperToInputTheme {
                    KeyboardScreen(
                        state = keyboardState.value,
                        languageLabel = languageLabel.value,
                        amplitude = microphoneAmplitude.value,
                        recordingTimeMs = recordingTimeMs.value,
                        showLongPressHint = showLongPressHint.value,
                        onMicAction = { onMicAction() },
                        onCancelAction = { onCancelAction() },
                        onDiscardAction = { discardRecording() },
                        onSendAction = { onSendAction() },
                        onDeleteAction = { onDeleteText() },
                        onOpenSettings = { launchMainActivity() },
                        onLanguageClick = { showLanguageMenu(this) },
                        onDismissHint = { showLongPressHint.value = false },
                        onLockAction = { lockRecording() },
                        onUnlockAction = { unlockRecording() }
                    )
                }
            }
        }
    }

    private suspend fun updateAudioFormat() {
        recordedAudioFilename = "${externalCacheDir?.absolutePath}/${RECORDED_AUDIO_FILENAME_M4A}"
        audioMediaType = AUDIO_MEDIA_TYPE_M4A
    }

    private fun updateLanguageLabel() {
        CoroutineScope(Dispatchers.Main).launch {
            val languageCode = dataStore.data.map { it[LANGUAGE_CODE] ?: "auto" }.first()
            languageLabel.value = if (languageCode == "auto") getString(R.string.language_auto_label) else languageCode.uppercase()
        }
    }

    private fun setKeyboardState(state: KeyboardState) {
        keyboardState.value = state
    }

    private fun onMicAction() {
        when (keyboardState.value) {
            KeyboardState.Ready -> {
                performFeedback(customSoundId = R.raw.rec_start)
                startRecording()
            }
            KeyboardState.Recording -> {
                performFeedback(customSoundId = R.raw.rec_pause)
                pauseRecording()
            }
            KeyboardState.RecordingLocked -> {
                performFeedback(customSoundId = R.raw.rec_pause)
                pauseRecording()
            }
            KeyboardState.Paused -> {
                performFeedback(customSoundId = R.raw.rec_start)
                resumeRecording()
            }
            else -> Unit
        }
    }

    private fun onSendAction() {
        if (keyboardState.value == KeyboardState.Paused) startTranscription("")
        else performFeedback()
    }

    private fun lockRecording() {
        setKeyboardState(KeyboardState.RecordingLocked)
    }

    private fun unlockRecording() {
        performFeedback(customSoundId = R.raw.rec_pause)
        pauseRecording()
    }

    private fun onCancelAction() {
        performFeedback(customSoundId = R.raw.rec_pause)
        when (keyboardState.value) {
            KeyboardState.Recording, KeyboardState.RecordingLocked, KeyboardState.Paused -> confirmCancelAction { cancelRecording() }
            KeyboardState.Transcribing, KeyboardState.SmartFixing -> confirmCancelAction { cancelTranscription() }
            else -> Unit
        }
    }

    private fun startRecording() {
        if (!recorderManager!!.allPermissionsGranted(this)) {
            launchMainActivity()
            setKeyboardState(KeyboardState.Ready)
            return
        }
        showLongPressHint.value = false
        setKeyboardState(KeyboardState.Recording)
        recordingTimeMs.value = 0L
        startTimer()
        recorderManager!!.start(this, recordedAudioFilename, useOggFormat)
    }

    private fun pauseRecording() {
        stopTimer()
        if (recordingTimeMs.value < 500L) {
            showLongPressHint.value = true
            recorderManager!!.stop()
            recordingTimeMs.value = 0L
            setKeyboardState(KeyboardState.Ready)
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                val autoTranscribe = dataStore.data.map { it[AUTO_TRANSCRIBE_ON_PAUSE] ?: true }.first()
                if (autoTranscribe) {
                    startTranscription("")
                } else {
                    recorderManager!!.pause()
                    setKeyboardState(KeyboardState.Paused)
                }
            }
        }
    }

    private fun resumeRecording() {
        recorderManager!!.resume()
        startTimer()
        setKeyboardState(KeyboardState.Recording)
    }

    private fun cancelRecording() {
        recorderManager!!.stop()
        stopTimer()
        recordingTimeMs.value = 0L
        showLongPressHint.value = false
        setKeyboardState(KeyboardState.Ready)
    }

    private fun discardRecording() {
        playCustomSound(R.raw.rec_pause)
        recorderManager!!.stop()
        stopTimer()
        recordingTimeMs.value = 0L
        setKeyboardState(KeyboardState.Ready)
    }

    private fun startTimer() {
        timerJob?.cancel()
        val startTime = System.currentTimeMillis() - recordingTimeMs.value
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                recordingTimeMs.value = System.currentTimeMillis() - startTime
                delay(33)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun cancelTranscription() {
        whisperTranscriber.stop()
        setKeyboardState(KeyboardState.Ready)
    }

    private fun confirmCancelAction(onConfirm: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val shouldConfirm = dataStore.data.map { it[CANCEL_CONFIRMATION] ?: true }.first()
            if (!shouldConfirm) {
                onConfirm()
                return@launch
            }
            val themedContext = ContextThemeWrapper(this@WhisperInputService, R.style.Theme_WhisperToInput)
            val dialog = androidx.appcompat.app.AlertDialog.Builder(themedContext)
                .setTitle(getString(R.string.cancel_confirmation_title))
                .setMessage(getString(R.string.cancel_confirmation_message))
                .setPositiveButton(getString(R.string.cancel_confirmation_confirm)) { _, _ -> onConfirm() }
                .setNegativeButton(getString(R.string.cancel_confirmation_dismiss), null)
                .create()
            dialog.window?.let { window ->
                val lp = window.attributes
                lp.token = this@WhisperInputService.window?.window?.attributes?.token
                lp.type = android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD
                window.attributes = lp
            }
            dialog.show()
        }
    }

    private fun playCustomSound(resId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val soundEnabled = dataStore.data.map { it[SOUND_EFFECTS_ENABLED] ?: true }.first()
            if (soundEnabled) {
                withContext(Dispatchers.Main) {
                    try {
                        val mp = MediaPlayer.create(this@WhisperInputService, resId) ?: return@withContext
                        synchronized(activeMediaPlayers) { activeMediaPlayers.add(mp) }
                        mp.setOnCompletionListener { player ->
                            synchronized(activeMediaPlayers) { activeMediaPlayers.remove(player) }
                            player.release()
                        }
                        mp.start()
                    } catch (e: Exception) { Log.e(TAG, "Sound error", e) }
                }
            }
        }
    }

    private fun performFeedback(isDelete: Boolean = false, customSoundId: Int? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            val prefs = dataStore.data.first()
            if (prefs[HAPTIC_FEEDBACK_ENABLED] ?: true) {
                val effect = if (isDelete) HapticFeedbackConstants.KEYBOARD_RELEASE else HapticFeedbackConstants.KEYBOARD_TAP
                window?.window?.decorView?.performHapticFeedback(effect)
            }
            if (customSoundId != null) playCustomSound(customSoundId)
            else if (prefs[SOUND_EFFECTS_ENABLED] ?: true) {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.playSoundEffect(if (isDelete) AudioManager.FX_KEYPRESS_DELETE else AudioManager.FX_KEYPRESS_STANDARD)
            }
        }
    }

    private fun onDeleteText() {
        performFeedback(isDelete = true)
        val ic = currentInputConnection ?: return
        if (TextUtils.isEmpty(ic.getSelectedText(0))) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        else ic.commitText("", 1)
    }

    private fun onSwitchIme() {
        if (Build.VERSION.SDK_INT >= IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL) switchToPreviousInputMethod()
        else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("DEPRECATION")
            imm.switchToLastInputMethod(window?.window?.attributes?.token)
        }
    }

    private fun showLanguageMenu(anchor: View) {
        CoroutineScope(Dispatchers.Main).launch {
            val current = dataStore.data.map { it[LANGUAGE_CODE] ?: "auto" }.first()
            val systemLocales = LocaleList.getDefault()
            val languages = mutableListOf("auto" to getString(R.string.language_auto_label), "en" to "English", "ar" to "Arabic")
            for (i in 0 until systemLocales.size()) {
                val locale = systemLocales.get(i)
                if (languages.none { it.first == locale.language }) languages.add(locale.language to locale.displayLanguage.replaceFirstChar { it.uppercase() })
            }
            val menu = PopupMenu(ContextThemeWrapper(this@WhisperInputService, R.style.Theme_WhisperToInput), anchor)
            languages.forEachIndexed { i, (code, name) ->
                menu.menu.add(1, i, i, name).apply { isCheckable = true; isChecked = code == current }
            }
            menu.menu.setGroupCheckable(1, true, true)
            menu.setOnMenuItemClickListener { item ->
                val selected = languages[item.itemId].first
                CoroutineScope(Dispatchers.Main).launch {
                    dataStore.edit { it[LANGUAGE_CODE] = selected }
                    updateLanguageLabel()
                }
                true
            }
            menu.show()
        }
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyboardState.value != KeyboardState.Ready) {
                Log.d(TAG, "Intercepting Back Key Down: ${keyboardState.value}")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyboardState.value != KeyboardState.Ready) {
                Log.d(TAG, "Intercepting Back Key Up: ${keyboardState.value}")
                onCancelAction()
                return true
            } else {
                requestHideSelf(0)
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        CoroutineScope(Dispatchers.Main).launch {
            updateAudioFormat()
            updateLanguageLabel()
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        Log.d(TAG, "onWindowHidden: Stopping all active tasks")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        whisperTranscriber.stop()
        recorderManager?.stop()
        stopTimer()
        releaseAllMediaPlayers()
        if (keyboardState.value != KeyboardState.Ready) setKeyboardState(KeyboardState.Ready)
    }

    private fun releaseAllMediaPlayers() {
        synchronized(activeMediaPlayers) {
            activeMediaPlayers.forEach { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                } catch (_: Exception) {}
            }
            activeMediaPlayers.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        whisperTranscriber.stop()
        recorderManager?.stop()
        releaseAllMediaPlayers()
    }
}
