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

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
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
import android.view.inputmethod.EditorInfo
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
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.DictationAnalyticsSession
import com.github.shekohex.whisperpp.analytics.analyticsDataStore
import com.github.shekohex.whisperpp.data.EffectiveRuntimeConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeSelectionResolver
import com.github.shekohex.whisperpp.data.RuntimeWarning
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.dictation.DictationController
import com.github.shekohex.whisperpp.dictation.EnhancementOutcome
import com.github.shekohex.whisperpp.dictation.EnhancementRunner
import com.github.shekohex.whisperpp.dictation.FailureReason
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.dictation.OpenAiRealtimeSttClient
import com.github.shekohex.whisperpp.dictation.SkipReason
import com.github.shekohex.whisperpp.command.CommandController
import com.github.shekohex.whisperpp.command.CommandStage
import com.github.shekohex.whisperpp.command.ResolvedSelection
import com.github.shekohex.whisperpp.command.SelectionResolver
import com.github.shekohex.whisperpp.command.SelectionSnapshot
import com.github.shekohex.whisperpp.command.TransformPromptBuilder
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isRecording
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import com.github.shekohex.whisperpp.privacy.SendPolicyRepository
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import com.github.shekohex.whisperpp.recorder.RecorderManager
import com.github.shekohex.whisperpp.ui.keyboard.FirstUseDisclosureUiState
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeStyle
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeUiState
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardScreen
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.File
import java.time.LocalDate
import java.util.Locale

private const val RECORDED_AUDIO_FILENAME_WAV = "recorded.wav"
private const val AUDIO_MEDIA_TYPE_WAV = "audio/wav"
private const val IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL = 28
private const val TAG = "WhisperInputService"
private const val PRIVACY_SAFETY_DESTINATION = "privacy_safety"

private enum class FirstUseDisclosureMode {
    DICTATION_AUDIO,
    ENHANCEMENT_TEXT,
    COMMAND_TEXT,
}

private enum class FirstUseDisclosureDecision {
    CONTINUE,
    CANCEL,
    OPEN_SETTINGS,
}

class WhisperInputService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val whisperTranscriber: WhisperTranscriber = WhisperTranscriber()
    private lateinit var smartFixer: SmartFixer
    private var recorderManager: RecorderManager? = null
    private var recordedAudioFilename: String = ""
    private var audioMediaType: String = AUDIO_MEDIA_TYPE_WAV
    private var isFirstTime: Boolean = true
    
    private lateinit var repository: SettingsRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var secretsStore: SecretsStore
    private lateinit var sendPolicyRepository: SendPolicyRepository

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
    private var undoAvailable = mutableStateOf(false)
    private var undoQuickActionVisible = mutableStateOf(false)
    private var enhancementNotice = mutableStateOf<EnhancementNoticeUiState?>(null)
    private var enhancementUndoAvailable = mutableStateOf(false)
    private var enhancementNoticeJob: Job? = null
    private var externalSendBlockReason = mutableStateOf<SecureFieldDetector.Reason?>(null)
    private var externalSendBlockedByAppPolicy = mutableStateOf(false)
    private var externalSendBlockedPackageName = mutableStateOf<String?>(null)
    private var secureFieldExplanationDontShowAgain = mutableStateOf(false)
    private var firstUseDisclosure = mutableStateOf<FirstUseDisclosureUiState?>(null)
    private var pendingFirstUseDisclosureMode: FirstUseDisclosureMode? = null
    private var pendingFirstUseDisclosureContinuation: CancellableContinuation<FirstUseDisclosureDecision>? = null
    private var networkLoggingPreferenceJob: Job? = null
    private var timerJob: Job? = null
    private val activeMediaPlayers = mutableListOf<MediaPlayer>()

    private val commandController = CommandController()
    private val selectionResolver = SelectionResolver()
    private var isCommandModeActive = mutableStateOf(false)
    private var commandStage = mutableStateOf(CommandStage.WAITING)
    private var commandErrorMessage = mutableStateOf<String?>(null)
    private var commandUndoAvailable = mutableStateOf(false)
    private var commandUndoRemainingMs = mutableStateOf(0L)
    private var commandSelectedPresetId = mutableStateOf(TRANSFORM_PRESET_ID_TONE_REWRITE)
    private var commandClipboardPreview = mutableStateOf("")
    private var commandClipboardCharCount = mutableStateOf(0)
    private var commandClipboardIsLarge = mutableStateOf(false)
    private var commandClipboardUnavailableReason = mutableStateOf<String?>(null)
    private var commandClipboardAttemptsRemaining = mutableStateOf(2)
    private var commandSilenceRetryUsed = false

    private var commandRunFocusKey: FocusKey? = null
    private var commandSelectionSnapshot: SelectionSnapshot? = null
    private var commandConfirmedText: String? = null
    private var commandOriginalTextForUndo: String? = null
    private var commandSpokenInstruction: String? = null
    private var commandJob: Job? = null
    private var commandUndoJob: Job? = null

    private var focusInstanceId: Long = 0L
    private lateinit var dictationController: DictationController
    private val enhancementRunner = EnhancementRunner()

    private var activeDictationSttProviderId: String? = null
    private var activeDictationSttModelId: String? = null

    private var realtimeSttClient: OpenAiRealtimeSttClient? = null
    private var realtimeToken: DictationController.SendToken? = null
    private var realtimeFinalTranscript: String? = null
    private var realtimeBestEffortTranscript: String? = null
    private var pendingRealtimeProvider: ServiceProvider? = null
    private var pendingRealtimeModelId: String? = null
    private var dictationAnalyticsSession: DictationAnalyticsSession? = null

    override fun onCreate() {

        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        repository = SettingsRepository(dataStore)
        analyticsRepository = AnalyticsRepository(analyticsDataStore)
        secretsStore = SecretsStore(this)
        sendPolicyRepository = SendPolicyRepository(dataStore)
        runBlocking {
            repository.migrateProviderSchemaV2IfNeeded(this@WhisperInputService)
        }
        recorderManager = RecorderManager(this)
        smartFixer = SmartFixer(this)

        dictationController = DictationController(
            DictationController.Deps(
                getKeyboardState = { keyboardState.value },
                setKeyboardState = { setKeyboardState(it) },
                toast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
                copyToClipboard = { copyToClipboard(it) },
                clearComposing = { clearComposingText() },
                getInputConnection = { currentInputConnection },
                isInsertAllowed = { externalSendBlockReason.value == null },
                startRecording = { startRecording(it) },
                pauseRecording = { pauseRecording(targetState = it) },
                resumeRecording = { resumeRecording(targetState = it) },
                stopRecording = { stopRecordingForSend() },
                cancelTranscription = { cancelTranscriptionWork() },
            )
        )
        refreshUndoState()

        networkLoggingPreferenceJob = CoroutineScope(Dispatchers.Main).launch {
            dataStore.data.map { it[VERBOSE_NETWORK_LOGS_ENABLED] ?: false }.collect { enabled ->
                whisperTranscriber.setNetworkLoggingEnabled(enabled)
                smartFixer.setNetworkLoggingEnabled(enabled)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        focusInstanceId += 1
        dictationController.onFocusChanged(FocusKey.from(attribute, focusInstanceId))
        if (isCommandModeActive.value) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_focus_changed))
        }
        showUndoQuickActionIfAvailable()
        refreshEnhancementUndoState()
        refreshExternalSendBlock(attribute)
        if (isExternalSendBlocked() && isExternalSendingActive()) {
            dictationController.onWindowHidden(toastMessage = blockedNotice())
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        focusInstanceId += 1
        dictationController.onFocusChanged(FocusKey.from(info, focusInstanceId))
        if (isCommandModeActive.value) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_focus_changed))
        }
        showUndoQuickActionIfAvailable()
        refreshEnhancementUndoState()
        refreshExternalSendBlock(info)
        if (isExternalSendBlocked() && isExternalSendingActive()) {
            dictationController.onWindowHidden(toastMessage = blockedNotice())
        }
    }

    private fun transcriptionCallback(token: DictationController.SendToken, text: String?, contextPrompt: String?) {
        val effectiveText = if (!text.isNullOrEmpty()) {
            text
        } else {
            val fallback = realtimeFinalTranscript
            if (token.streamingPartialsEnabled && !fallback.isNullOrBlank()) fallback else text
        }

        val rawText = effectiveText?.takeIf { it.isNotBlank() }
        if (rawText == null) {
            if (text != null) {
                dictationController.onTranscriptionError(token, getString(R.string.toast_transcription_empty))
                performFeedback()
            }
            closeRealtimeSession()
            clearActiveDictationSelection()
            return
        }

        Log.d(TAG, "Transcription received length=${rawText.length}")
        playCustomSound(R.raw.rec_done)

        CoroutineScope(Dispatchers.Main).launch {
            val prefs = dataStore.data.first()
            val providers = repository.providers.first()
            val languageCode = prefs[LANGUAGE_CODE] ?: "auto"
            val autoSwitchBack = prefs[AUTO_SWITCH_BACK] ?: false

            val captured = dictationController.insertRawAndCaptureSegment(token, rawText)
            showUndoQuickActionIfAvailable()

            if (captured == null && isExternalSendBlocked()) {
                showEnhancementNotice(blockedNotice(), EnhancementNoticeStyle.INFO)
            }

            closeRealtimeSession()
            clearActiveDictationSelection()

            if (captured == null) {
                if (autoSwitchBack && keyboardState.value == KeyboardState.Ready) {
                    delay(150)
                    onSwitchIme()
                }
                performFeedback()
                return@launch
            }

            activeDictationAnalyticsSession(token)?.recordRawTranscript(rawText)

            val smartFixEnabled = prefs[SMART_FIX_ENABLED] ?: false
            if (!smartFixEnabled || isBlankOrPunctuationOnly(rawText)) {
                completeDictationAnalyticsSession(token = token, rawText = rawText)
                if (autoSwitchBack && keyboardState.value == KeyboardState.Ready) {
                    delay(150)
                    onSwitchIme()
                }
                performFeedback()
                return@launch
            }

            if (keyboardState.value == KeyboardState.Ready) {
                setKeyboardState(KeyboardState.SmartFixing)
            }

            val effective = resolveEffectiveRuntimeConfig(
                packageName = captured.focusKey.packageName,
                languageCode = languageCode,
                prefs = prefs,
                providers = providers,
            )

            val textProviderId = effective.textProviderId
            val textModelId = effective.textModelId
            val provider = providers.find { it.id == textProviderId }
            val providerLabel = provider?.name?.trim().orEmpty().ifBlank { textProviderId.ifBlank { "Unknown provider" } }
            val modelLabel = textModelId.ifBlank { "Unknown model" }

            if (shouldBlockExternalSend()) {
                completeDictationAnalyticsSession(token = token, rawText = rawText)
                showEnhancementNotice(
                    "Enhancement skipped for $providerLabel ($modelLabel): ${blockedNotice()}",
                    EnhancementNoticeStyle.INFO,
                )
                if (keyboardState.value == KeyboardState.SmartFixing) {
                    setKeyboardState(KeyboardState.Ready)
                }
                if (autoSwitchBack && keyboardState.value == KeyboardState.Ready) {
                    delay(150)
                    onSwitchIme()
                }
                performFeedback()
                return@launch
            }

            val textWarnings = effective.warnings.filter { it.channel == RuntimeChannel.TEXT }
            if (textWarnings.isNotEmpty()) {
                showEnhancementNotice(formatRuntimeWarnings(textWarnings), EnhancementNoticeStyle.INFO)
            }

            if (provider == null || textModelId.isBlank() || provider.models.none { it.id == textModelId }) {
                completeDictationAnalyticsSession(token = token, rawText = rawText)
                showEnhancementNotice(
                    "Enhancement unavailable for ${textProviderId.ifBlank { "Unknown provider" }} (${textModelId.ifBlank { "Unknown model" }})",
                    EnhancementNoticeStyle.ERROR,
                )
                if (keyboardState.value == KeyboardState.SmartFixing) {
                    setKeyboardState(KeyboardState.Ready)
                }
                if (autoSwitchBack && keyboardState.value == KeyboardState.Ready) {
                    delay(150)
                    onSwitchIme()
                }
                performFeedback()
                return@launch
            }

            val useContext = prefs[USE_CONTEXT] ?: false
            val disclosure = PrivacyDisclosureFormatter.disclosureForEnhancement(
                provider = provider,
                selectedModelId = textModelId,
                useContext = useContext,
            )
            val disclosureDecision = awaitFirstUseDisclosure(
                mode = FirstUseDisclosureMode.ENHANCEMENT_TEXT,
                disclosure = disclosure,
            )
            if (disclosureDecision != FirstUseDisclosureDecision.CONTINUE) {
                completeDictationAnalyticsSession(token = token, rawText = rawText)
                if (keyboardState.value == KeyboardState.SmartFixing) {
                    setKeyboardState(KeyboardState.Ready)
                }
                if (autoSwitchBack && keyboardState.value == KeyboardState.Ready) {
                    delay(150)
                    onSwitchIme()
                }
                return@launch
            }

            if (provider.endpoint.isBlank()) {
                completeDictationAnalyticsSession(token = token, rawText = rawText)
                showEnhancementNotice(
                    "Enhancement unavailable for ${provider.name} ($textModelId): missing endpoint",
                    EnhancementNoticeStyle.ERROR,
                )
                if (keyboardState.value == KeyboardState.SmartFixing) {
                    setKeyboardState(KeyboardState.Ready)
                }
                if (autoSwitchBack && keyboardState.value == KeyboardState.Ready) {
                    delay(150)
                    onSwitchIme()
                }
                performFeedback()
                return@launch
            }

            val providerWithApiKey = provider.copy(
                apiKey = secretsStore.getProviderApiKey(provider.id).orEmpty()
            )
            if (providerWithApiKey.authMode == ProviderAuthMode.API_KEY && providerWithApiKey.apiKey.isBlank()) {
                completeDictationAnalyticsSession(token = token, rawText = rawText)
                showEnhancementNotice(
                    "Enhancement unavailable for ${provider.name} ($textModelId): missing API key",
                    EnhancementNoticeStyle.ERROR,
                )
                if (keyboardState.value == KeyboardState.SmartFixing) {
                    setKeyboardState(KeyboardState.Ready)
                }
                if (autoSwitchBack && keyboardState.value == KeyboardState.Ready) {
                    delay(150)
                    onSwitchIme()
                }
                performFeedback()
                return@launch
            }

            val temperature = if (provider.temperature > 0) provider.temperature else prefs[SMART_FIX_TEMPERATURE] ?: 0.0f

            val enhancementPresetId = repository.enhancementPresetId.first().orEmpty()
            val enhancementPresetInstruction = presetById(enhancementPresetId)?.promptInstruction
            val promptTemplate = buildEnhancementPromptTemplate(
                basePrompt = effective.prompt,
                presetInstruction = enhancementPresetInstruction,
            )

            performFeedback()

            val outcome = enhancementRunner.run(
                rawText = rawText,
                enhance = {
                    withContext(Dispatchers.IO) {
                        smartFixer.fix(
                            text = rawText,
                            contextInformation = contextPrompt,
                            provider = providerWithApiKey,
                            modelId = textModelId,
                            temperature = temperature,
                            promptTemplate = promptTemplate,
                        )
                    }
                },
            )

            if (keyboardState.value == KeyboardState.SmartFixing) {
                setKeyboardState(KeyboardState.Ready)
            }

            when (outcome) {
                is EnhancementOutcome.Succeeded -> {
                    val replaced = dictationController.replaceCapturedSegment(captured, outcome.enhancedText)
                    if (replaced) {
                        completeDictationAnalyticsSession(
                            token = token,
                            rawText = rawText,
                            finalInsertedText = outcome.enhancedText,
                        )
                        refreshEnhancementUndoState()
                    } else {
                        completeDictationAnalyticsSession(token = token, rawText = rawText)
                        showEnhancementNotice(
                            "Enhancement ready for ${provider.name} ($textModelId), but focus changed; kept raw",
                            EnhancementNoticeStyle.INFO,
                        )
                    }
                }

                is EnhancementOutcome.Skipped -> {
                    completeDictationAnalyticsSession(token = token, rawText = rawText)
                    when (outcome.reason) {
                        SkipReason.Empty,
                        SkipReason.PunctuationOnly,
                        -> Unit
                    }
                }

                is EnhancementOutcome.Failed -> {
                    completeDictationAnalyticsSession(token = token, rawText = rawText)
                    when (val reason = outcome.reason) {
                        FailureReason.Cancelled -> Unit
                        FailureReason.Timeout -> {
                            showEnhancementNotice(
                                "Enhancement timed out for ${provider.name} ($textModelId)",
                                EnhancementNoticeStyle.ERROR,
                            )
                        }

                        is FailureReason.Transient -> {
                            showEnhancementNotice(
                                "Enhancement failed for ${provider.name} ($textModelId): ${reason.message}",
                                EnhancementNoticeStyle.ERROR,
                            )
                        }

                        is FailureReason.NonTransient -> {
                            showEnhancementNotice(
                                "Enhancement failed for ${provider.name} ($textModelId): ${reason.message}",
                                EnhancementNoticeStyle.ERROR,
                            )
                        }
                    }
                }
            }

            if (autoSwitchBack && keyboardState.value == KeyboardState.Ready) {
                delay(150)
                onSwitchIme()
            }
            performFeedback()
        }
    }

    private fun transcriptionExceptionCallback(token: DictationController.SendToken, message: String) {
        Log.e(TAG, "Transcription error: $message")
        val bestEffort = if (token.streamingPartialsEnabled) {
            realtimeFinalTranscript ?: realtimeBestEffortTranscript
        } else {
            null
        }

        if (!bestEffort.isNullOrBlank()) {
            val captured = dictationController.insertRawAndCaptureSegment(token, bestEffort)
            if (captured != null) {
                completeDictationAnalyticsSession(token = token, rawText = bestEffort)
            }
            showUndoQuickActionIfAvailable()
            performFeedback()
        } else {
            dictationController.onTranscriptionError(token, message)
            performFeedback()
        }

        closeRealtimeSession()
        clearActiveDictationSelection()
    }

    private fun startTranscription(token: DictationController.SendToken, attachToEnd: String) {
        if (shouldBlockExternalSend()) {
            dictationController.onTranscriptionError(token, blockedNotice())
            return
        }

        performFeedback(customSoundId = R.raw.rec_stop)

        CoroutineScope(Dispatchers.Main).launch {
            val prefs = dataStore.data.first()
            val useContext = prefs[USE_CONTEXT] ?: false
            val contextPrompt = if (useContext) currentInputConnection?.getTextBeforeCursor(500, 0)?.toString() else null
            val languageCode = prefs[LANGUAGE_CODE] ?: "auto"
            
            val providers = repository.providers.first()

            val providerId = activeDictationSttProviderId.orEmpty()
            val modelId = activeDictationSttModelId.orEmpty()
            val provider = providers.find { it.id == providerId }
            if (provider == null || modelId.isBlank() || provider.models.none { it.id == modelId }) {
                dictationController.onTranscriptionError(token, "Setup required: select STT provider + model in Settings")
                clearActiveDictationSelection()
                return@launch
            }

            val providerWithApiKey = provider.copy(
                apiKey = secretsStore.getProviderApiKey(provider.id).orEmpty()
            )
            
            val staticPrompt = if (provider.prompt.isNotEmpty()) provider.prompt else prefs[PROMPT] ?: ""
            val temperature = provider.temperature
            val postprocessing = prefs[POSTPROCESSING] ?: getString(R.string.settings_option_no_conversion)
            val addTrailingSpace = prefs[ADD_TRAILING_SPACE] ?: false
            val timeout = provider.timeout

            if (shouldBlockExternalSend()) {
                dictationController.onTranscriptionError(token, blockedNotice())
                return@launch
            }

            whisperTranscriber.startAsync(
                this@WhisperInputService, recordedAudioFilename, audioMediaType,
                attachToEnd, contextPrompt, languageCode, providerWithApiKey, modelId, postprocessing,
                addTrailingSpace, timeout, staticPrompt, temperature,
                { transcriptionCallback(token, it, contextPrompt) },
                { transcriptionExceptionCallback(token, it) }
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
                        undoAvailable = undoAvailable.value,
                        undoQuickActionVisible = undoQuickActionVisible.value,
                        enhancementNotice = enhancementNotice.value,
                        enhancementUndoAvailable = enhancementUndoAvailable.value,
                        onMicAction = { onMicAction() },
                        onCancelAction = { onCancelAction() },
                        onDiscardAction = { discardRecording() },
                        onSendAction = { onSendAction() },
                        onDeleteAction = { onDeleteText() },
                        onUndoAction = { onUndoAction() },
                        onEnhancementUndoAction = {
                            performFeedback(isDelete = true)
                            val applied = dictationController.applyEnhancementUndo()
                            refreshEnhancementUndoState()
                            if (!applied) {
                                showEnhancementNotice("Can't undo enhancement here", EnhancementNoticeStyle.INFO)
                            }
                        },
                        onDismissEnhancementNotice = { dismissEnhancementNotice() },
                        onOpenSettings = { launchMainActivity() },
                        onOpenSettingsDestination = { launchMainActivity(it) },
                        onLanguageClick = { showLanguageMenu(this) },
                        onDismissHint = { showLongPressHint.value = false },
                        onLockAction = { lockRecording() },
                        onUnlockAction = { unlockRecording() },
                        externalSendBlockedReason = externalSendBlockReason.value,
                        externalSendBlockedByAppPolicy = externalSendBlockedByAppPolicy.value,
                        blockedPackageName = externalSendBlockedPackageName.value,
                        showSecureFieldExplanation = !secureFieldExplanationDontShowAgain.value,
                        firstUseDisclosure = firstUseDisclosure.value,
                        onBlockedAction = { handleBlockedExternalSendAction() },
                        onDontShowSecureFieldExplanationAgain = { saveSecureFieldDontShowAgain() },
                        onFirstUseDisclosureContinue = { onFirstUseDisclosureContinue() },
                        onFirstUseDisclosureCancel = { onFirstUseDisclosureCancel() },
                        onFirstUseDisclosureOpenPrivacySafety = { onFirstUseDisclosureOpenPrivacySafety() },

                        commandModeActive = isCommandModeActive.value,
                        commandStage = commandStage.value,
                        commandErrorMessage = commandErrorMessage.value,
                        commandUndoAvailable = commandUndoAvailable.value,
                        commandUndoRemainingMs = commandUndoRemainingMs.value,
                        commandSelectedPresetId = commandSelectedPresetId.value,
                        commandClipboardPreview = commandClipboardPreview.value,
                        commandClipboardCharCount = commandClipboardCharCount.value,
                        commandClipboardIsLarge = commandClipboardIsLarge.value,
                        commandClipboardUnavailableReason = commandClipboardUnavailableReason.value,
                        commandClipboardAttemptsRemaining = commandClipboardAttemptsRemaining.value,
                        onCommandEnter = { onCommandEnter() },
                        onCommandCancel = { onCommandCancel() },
                        onCommandClipboardContinue = { onCommandClipboardContinue() },
                        onCommandClipboardRetry = { onCommandClipboardRetry() },
                        onCommandStopListening = { onCommandStopListening() },
                        onCommandRetry = { onCommandRetryTransform() },
                        onCommandUndo = { onCommandUndo() },
                        onCommandPresetSelected = { onCommandPresetSelected(it) },
                    )
                }
            }
        }
    }

    private suspend fun updateAudioFormat() {
        recordedAudioFilename = "${externalCacheDir?.absolutePath}/${RECORDED_AUDIO_FILENAME_WAV}"
        audioMediaType = AUDIO_MEDIA_TYPE_WAV
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

    private fun refreshUndoState() {
        val available = dictationController.isUndoAvailable()
        undoAvailable.value = available
        if (!available) {
            undoQuickActionVisible.value = false
        }
    }

    private fun showUndoQuickActionIfAvailable() {
        refreshUndoState()
        if (undoAvailable.value) {
            undoQuickActionVisible.value = true
        }
    }

    private fun clearUndoQuickAction() {
        undoQuickActionVisible.value = false
    }

    private fun refreshEnhancementUndoState() {
        enhancementUndoAvailable.value = dictationController.isEnhancementUndoAvailable()
    }

    private fun clearEnhancementUndoState() {
        enhancementUndoAvailable.value = false
    }

    private fun dismissEnhancementNotice() {
        enhancementNoticeJob?.cancel()
        enhancementNoticeJob = null
        enhancementNotice.value = null
    }

    private fun showEnhancementNotice(message: String, style: EnhancementNoticeStyle) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return
        enhancementNoticeJob?.cancel()
        enhancementNotice.value = EnhancementNoticeUiState(message = trimmed, style = style)
        enhancementNoticeJob = CoroutineScope(Dispatchers.Main).launch {
            delay(2600)
            enhancementNotice.value = null
            enhancementNoticeJob = null
        }
    }

    private fun clearActiveDictationSelection() {
        activeDictationSttProviderId = null
        activeDictationSttModelId = null
    }

    private fun formatRuntimeWarnings(warnings: List<RuntimeWarning>): String {
        return warnings
            .map { it.message.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(separator = " ")
    }

    private fun readSmartFixDefaultPromptOrEmpty(): String {
        return runCatching {
            resources.openRawResource(R.raw.smart_fix_prompt).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    private fun buildEnhancementPromptTemplate(
        basePrompt: String,
        presetInstruction: String?,
    ): String {
        val base = basePrompt.trim().ifBlank { readSmartFixDefaultPromptOrEmpty().trim() }
        val preset = presetInstruction?.trim().orEmpty()
        if (preset.isBlank()) {
            return base
        }

        return buildString {
            if (base.isNotEmpty()) {
                append(base)
                append("\n\n")
            }
            append("Preset instruction:\n")
            append(preset)
        }.trimEnd()
    }

    private suspend fun resolveEffectiveRuntimeConfig(
        packageName: String?,
        languageCode: String?,
        prefs: Preferences,
        providers: List<ServiceProvider>,
    ): EffectiveRuntimeConfig {
        val languageDefaults = repository.profiles.first()
        val appMappings = repository.appPromptMappings.first()
        val basePrompt = repository.globalBasePrompt.first()
        val promptProfiles = repository.promptProfiles.first()

        return RuntimeSelectionResolver.resolve(
            packageName = packageName,
            languageCode = languageCode,
            providers = providers,
            languageDefaults = languageDefaults,
            appMappings = appMappings,
            globalSttProviderId = prefs[ACTIVE_STT_PROVIDER_ID].orEmpty(),
            globalSttModelId = prefs[ACTIVE_STT_MODEL_ID].orEmpty(),
            globalTextProviderId = prefs[ACTIVE_TEXT_PROVIDER_ID].orEmpty(),
            globalTextModelId = prefs[ACTIVE_TEXT_MODEL_ID].orEmpty(),
            basePrompt = basePrompt,
            profiles = promptProfiles,
        )
    }

    private fun isBlankOrPunctuationOnly(text: String): Boolean {
        if (text.isBlank()) return true
        return text.all { ch ->
            ch.isWhitespace() || when (Character.getType(ch.code)) {
                Character.CONNECTOR_PUNCTUATION.toInt(),
                Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(),
                Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
                Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt(),
                -> true
                else -> false
            }
        }
    }

    private fun currentExternalSendBlock(editorInfo: EditorInfo? = currentInputEditorInfo): SecureFieldDetector.Result? {
        val detection = SecureFieldDetector.detect(editorInfo)
        return if (detection.isSecure) detection else null
    }

    private fun refreshExternalSendBlock(editorInfo: EditorInfo?) {
        externalSendBlockReason.value = currentExternalSendBlock(editorInfo)?.reason
        val packageName = editorInfo?.packageName?.trim().orEmpty().ifBlank { null }
        val blockedByPolicy = if (packageName == null) {
            false
        } else {
            runCatching {
                runBlocking {
                    sendPolicyRepository.isBlockedFlow(packageName).first()
                }
            }.getOrDefault(false)
        }
        externalSendBlockedByAppPolicy.value = blockedByPolicy
        externalSendBlockedPackageName.value = if (blockedByPolicy) packageName else null
    }

    private fun isExternalSendBlocked(): Boolean {
        return externalSendBlockReason.value != null || externalSendBlockedByAppPolicy.value
    }

    private fun isExternalSendingActive(): Boolean {
        return when (keyboardState.value) {
            KeyboardState.Recording,
            KeyboardState.RecordingLocked,
            KeyboardState.Paused,
            KeyboardState.PausedLocked,
            KeyboardState.Transcribing,
            KeyboardState.SmartFixing
            -> true
            KeyboardState.Ready -> false
        }
    }

    private fun stopExternalSendingForBlockedPolicy() {
        recorderManager?.stop()
        stopTimer()
        recordingTimeMs.value = 0L
        showLongPressHint.value = false
        whisperTranscriber.stop()
        smartFixer.cancel()
        closeRealtimeSession()
        setKeyboardState(KeyboardState.Ready)
        Toast.makeText(this, blockedNotice(), Toast.LENGTH_SHORT).show()
    }

    private fun blockedNotice(): String {
        return if (externalSendBlockedByAppPolicy.value) {
            "External sending blocked for this app"
        } else {
            getString(R.string.secure_field_sending_stopped)
        }
    }

    private fun stopRecordingForSend() {
        recorderManager?.stop()
        stopTimer()
        showLongPressHint.value = false
        realtimeSttClient?.commitAudio()
    }

    private fun clearComposingText() {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            ic.setComposingText("", 1)
            ic.finishComposingText()
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Whisper++", text))
    }

    private fun handleBlockedExternalSendAction() {
        performFeedback()
    }

    private fun saveSecureFieldDontShowAgain() {
        CoroutineScope(Dispatchers.Main).launch {
            dataStore.edit { prefs ->
                prefs[SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN] = true
            }
            secureFieldExplanationDontShowAgain.value = true
        }
    }

    private fun shouldBlockExternalSend(): Boolean {
        refreshExternalSendBlock(currentInputEditorInfo)
        return isExternalSendBlocked()
    }

    private fun disclosurePreferenceKey(mode: FirstUseDisclosureMode): Preferences.Key<Boolean> {
        return when (mode) {
            FirstUseDisclosureMode.DICTATION_AUDIO -> DISCLOSURE_SHOWN_DICTATION_AUDIO
            FirstUseDisclosureMode.ENHANCEMENT_TEXT -> DISCLOSURE_SHOWN_ENHANCEMENT_TEXT
            FirstUseDisclosureMode.COMMAND_TEXT -> DISCLOSURE_SHOWN_COMMAND_TEXT
        }
    }

    private fun mapDisclosureToUiState(
        disclosure: PrivacyDisclosureFormatter.ModeDisclosure,
    ): FirstUseDisclosureUiState {
        return FirstUseDisclosureUiState(
            title = disclosure.title,
            dataSent = disclosure.dataSent,
            endpointLines = disclosure.endpoints.map { endpoint ->
                endpoint.label?.let { "$it: ${endpoint.baseUrl}${endpoint.path}" }
                    ?: "Endpoint: ${endpoint.baseUrl}${endpoint.path}"
            },
            contextLine = disclosure.contextLine,
        )
    }

    private suspend fun awaitFirstUseDisclosure(
        mode: FirstUseDisclosureMode,
        disclosure: PrivacyDisclosureFormatter.ModeDisclosure,
    ): FirstUseDisclosureDecision {
        val alreadyShown = dataStore.data.map { prefs ->
            prefs[disclosurePreferenceKey(mode)] ?: false
        }.first()
        if (alreadyShown) {
            return FirstUseDisclosureDecision.CONTINUE
        }

        return suspendCancellableCoroutine { continuation ->
            pendingFirstUseDisclosureContinuation?.let { pending ->
                if (pending.isActive) {
                    pending.resume(FirstUseDisclosureDecision.CANCEL)
                }
            }
            pendingFirstUseDisclosureMode = mode
            pendingFirstUseDisclosureContinuation = continuation
            firstUseDisclosure.value = mapDisclosureToUiState(disclosure)

            continuation.invokeOnCancellation {
                if (pendingFirstUseDisclosureContinuation === continuation) {
                    pendingFirstUseDisclosureContinuation = null
                    pendingFirstUseDisclosureMode = null
                    firstUseDisclosure.value = null
                }
            }
        }
    }

    private fun resolveFirstUseDisclosure(decision: FirstUseDisclosureDecision) {
        val continuation = pendingFirstUseDisclosureContinuation
        pendingFirstUseDisclosureContinuation = null
        pendingFirstUseDisclosureMode = null
        firstUseDisclosure.value = null
        if (continuation != null && continuation.isActive) {
            continuation.resume(decision)
        }
    }

    private fun onFirstUseDisclosureContinue() {
        val mode = pendingFirstUseDisclosureMode ?: return
        CoroutineScope(Dispatchers.Main).launch {
            dataStore.edit { prefs ->
                prefs[disclosurePreferenceKey(mode)] = true
            }
            resolveFirstUseDisclosure(FirstUseDisclosureDecision.CONTINUE)
        }
    }

    private fun onFirstUseDisclosureCancel() {
        resolveFirstUseDisclosure(FirstUseDisclosureDecision.CANCEL)
    }

    private fun onFirstUseDisclosureOpenPrivacySafety() {
        launchMainActivity(PRIVACY_SAFETY_DESTINATION)
        resolveFirstUseDisclosure(FirstUseDisclosureDecision.OPEN_SETTINGS)
    }

    private fun onMicAction() {
        if (shouldBlockExternalSend()) {
            handleBlockedExternalSendAction()
            return
        }

        clearUndoQuickAction()
        clearEnhancementUndoState()

        when (keyboardState.value) {
            KeyboardState.Ready -> {
                CoroutineScope(Dispatchers.Main).launch {
                    val prefs = dataStore.data.first()
                    val providers = repository.providers.first()

                    val languageCode = prefs[LANGUAGE_CODE] ?: "auto"
                    val effective = resolveEffectiveRuntimeConfig(
                        packageName = currentInputEditorInfo?.packageName,
                        languageCode = languageCode,
                        prefs = prefs,
                        providers = providers,
                    )
                    val sttWarnings = effective.warnings.filter { it.channel == RuntimeChannel.STT }
                    if (sttWarnings.isNotEmpty()) {
                        showEnhancementNotice(formatRuntimeWarnings(sttWarnings), EnhancementNoticeStyle.INFO)
                    }

                    val providerId = effective.sttProviderId
                    val modelId = effective.sttModelId
                    val provider = providers.find { it.id == providerId }
                    if (provider == null || modelId.isBlank() || provider.models.none { it.id == modelId }) {
                        Toast.makeText(this@WhisperInputService, "Setup required: select STT provider + model in Settings", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val useContext = prefs[USE_CONTEXT] ?: false
                    val disclosure = PrivacyDisclosureFormatter.disclosureForDictation(
                        provider = provider,
                        selectedModelId = modelId,
                        useContext = useContext,
                    )
                    val disclosureDecision = awaitFirstUseDisclosure(
                        mode = FirstUseDisclosureMode.DICTATION_AUDIO,
                        disclosure = disclosure,
                    )
                    if (disclosureDecision != FirstUseDisclosureDecision.CONTINUE) {
                        return@launch
                    }
                    if (shouldBlockExternalSend()) {
                        return@launch
                    }
                    performFeedback(customSoundId = R.raw.rec_start)

                    closeRealtimeSession()

                    activeDictationSttProviderId = providerId
                    activeDictationSttModelId = modelId

                    val model = provider.models.find { it.id == modelId }
                    val wantsStreaming = model?.streamingPartialsSupported == true

                    val providerWithApiKey = provider.copy(
                        apiKey = secretsStore.getProviderApiKey(provider.id).orEmpty()
                    )
                    val supportsStreaming = wantsStreaming && supportsOpenAiRealtime(providerWithApiKey)

                    if (wantsStreaming && !supportsStreaming) {
                        Toast.makeText(
                            this@WhisperInputService,
                            "Streaming partials not supported for this provider",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    pendingRealtimeProvider = if (supportsStreaming) providerWithApiKey else null
                    pendingRealtimeModelId = if (supportsStreaming) modelId else null

                    realtimeToken = dictationController.onHoldStart(streamingPartialsEnabled = supportsStreaming)
                }
            }
            KeyboardState.Recording -> {
                performFeedback(customSoundId = R.raw.rec_pause)
                dictationController.onHoldRelease()
            }
            KeyboardState.RecordingLocked -> {
                performFeedback(customSoundId = R.raw.rec_pause)
                dictationController.onHoldRelease()
            }
            KeyboardState.Paused -> {
                performFeedback(customSoundId = R.raw.rec_start)
                dictationController.onResume()
            }
            KeyboardState.PausedLocked -> {
                performFeedback(customSoundId = R.raw.rec_start)
                dictationController.onResume()
            }
            else -> Unit
        }
    }

    private fun onSendAction() {
        if (shouldBlockExternalSend()) {
            handleBlockedExternalSendAction()
            return
        }

        clearUndoQuickAction()

        val token = dictationController.onSendRequested() ?: run {
            val cachedTranscript = realtimeFinalTranscript?.takeIf { it.isNotBlank() }
                ?: realtimeBestEffortTranscript?.takeIf { it.isNotBlank() }
            if (cachedTranscript != null) {
                completeDictationAnalyticsSession(rawText = cachedTranscript)
                closeRealtimeSession()
                clearActiveDictationSelection()
            }
            showUndoQuickActionIfAvailable()
            performFeedback()
            return
        }
        startTranscription(token, "")
    }

    private fun lockRecording() {
        dictationController.onLock()
    }

    private fun unlockRecording() {
        performFeedback(customSoundId = R.raw.rec_pause)
        dictationController.onUnlock()
    }

    private fun onCancelAction() {
        clearUndoQuickAction()
        performFeedback(customSoundId = R.raw.rec_pause)
        when (keyboardState.value) {
            KeyboardState.Recording,
            KeyboardState.RecordingLocked,
            KeyboardState.Paused,
            KeyboardState.PausedLocked,
            KeyboardState.Transcribing,
            KeyboardState.SmartFixing -> confirmCancelAction {
                recordTerminalDictationAnalyticsForCancellation()
                dictationController.onCancelConfirmed()
            }
            else -> Unit
        }
    }

    private fun startRecording(token: DictationController.SendToken) {
        realtimeToken = token

        if (shouldBlockExternalSend()) {
            setKeyboardState(KeyboardState.Ready)
            return
        }

        if (!recorderManager!!.allPermissionsGranted(this)) {
            launchMainActivity()
            setKeyboardState(KeyboardState.Ready)
            return
        }
        showLongPressHint.value = false
        setKeyboardState(KeyboardState.Recording)
        recordingTimeMs.value = 0L
        dictationAnalyticsSession = DictationAnalyticsSession(token.sessionId)
        startTimer()

        if (!token.streamingPartialsEnabled) {
            closeRealtimeSession()
            recorderManager!!.start(recordedAudioFilename)
            return
        }

        val provider = pendingRealtimeProvider
        val modelId = pendingRealtimeModelId
        if (provider == null || modelId.isNullOrBlank() || provider.apiKey.isBlank()) {
            closeRealtimeSession()
            recorderManager!!.start(recordedAudioFilename)
            return
        }

        val request = OpenAiRealtimeSttClient.buildRequest(
            providerEndpoint = provider.endpoint,
            modelId = modelId,
            apiKey = provider.apiKey,
        )
        if (request == null) {
            closeRealtimeSession()
            recorderManager!!.start(recordedAudioFilename)
            return
        }

        closeRealtimeClient()
        realtimeFinalTranscript = null

        val client = OpenAiRealtimeSttClient(
            okHttpClient = OpenAiRealtimeSttClient.defaultOkHttpClient(),
            request = request,
            listener = object : OpenAiRealtimeSttClient.Listener {
                override fun onConnected() = Unit

                override fun onPartialTranscript(text: String) {
                    realtimeBestEffortTranscript = text
                    val currentToken = realtimeToken ?: return
                    CoroutineScope(Dispatchers.Main).launch {
                        dictationController.onPartialTranscript(currentToken, text)
                    }
                }

                override fun onFinalTranscript(text: String) {
                    realtimeBestEffortTranscript = text
                    realtimeFinalTranscript = text
                }

                override fun onError(message: String) {
                    Log.e(TAG, "Realtime STT error: $message")
                }

                override fun onClosed() {
                    realtimeSttClient = null
                }
            },
        )

        realtimeSttClient = client
        client.connect()

        recorderManager!!.start(
            recordedAudioFilename,
            RecorderManager.StartOptions(
                onPcmChunk = { chunk ->
                    realtimeSttClient?.sendPcm16le(chunk.data)
                }
            )
        )
    }

    private fun pauseRecording(targetState: KeyboardState) {
        stopTimer()
        if (recordingTimeMs.value < 500L) {
            showLongPressHint.value = true
            recorderManager!!.stop()
            recordingTimeMs.value = 0L
            setKeyboardState(KeyboardState.Ready)
        } else {
            recorderManager!!.pause()
            setKeyboardState(targetState)
        }
    }

    private fun resumeRecording(targetState: KeyboardState) {
        recorderManager!!.resume()
        startTimer()
        setKeyboardState(targetState)
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
        clearUndoQuickAction()
        recordTerminalDictationAnalyticsForCancellation()
        dictationController.onCancelConfirmed()
    }

    private fun onUndoAction() {
        performFeedback(isDelete = true)
        dictationController.undoLastInsertion()
        refreshUndoState()
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

    private fun cancelTranscriptionWork() {
        whisperTranscriber.stop()
        smartFixer.cancel()
        closeRealtimeSession()
        clearActiveDictationSelection()
    }

    private fun currentFocusKey(): FocusKey? {
        return FocusKey.from(currentInputEditorInfo, focusInstanceId)
    }

    private fun refreshCommandUndoState() {
        val now = System.currentTimeMillis()
        val entry = commandController.getUndoEntry(now)
        if (entry == null) {
            commandUndoAvailable.value = false
            commandUndoRemainingMs.value = 0L
            commandUndoJob?.cancel()
            commandUndoJob = null
            return
        }
        commandUndoAvailable.value = true
        commandUndoRemainingMs.value = (entry.expiresAtMs - now).coerceAtLeast(0L)

        if (commandUndoJob == null) {
            commandUndoJob = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    delay(250)
                    val nowTick = System.currentTimeMillis()
                    val tickEntry = commandController.getUndoEntry(nowTick)
                    if (tickEntry == null) {
                        commandUndoAvailable.value = false
                        commandUndoRemainingMs.value = 0L
                        commandUndoJob = null
                        return@launch
                    }
                    commandUndoAvailable.value = true
                    commandUndoRemainingMs.value = (tickEntry.expiresAtMs - nowTick).coerceAtLeast(0L)
                }
            }
        }
    }

    private fun setCommandStage(stage: CommandStage, error: String? = null) {
        commandStage.value = stage
        commandErrorMessage.value = error
    }

    private fun resetCommandRunState() {
        commandJob?.cancel()
        commandJob = null
        recorderManager?.stop()
        whisperTranscriber.stop()
        smartFixer.cancel()

        commandRunFocusKey = null
        commandSelectionSnapshot = null
        commandConfirmedText = null
        commandOriginalTextForUndo = null
        commandSpokenInstruction = null
        commandSilenceRetryUsed = false

        commandClipboardPreview.value = ""
        commandClipboardCharCount.value = 0
        commandClipboardIsLarge.value = false
        commandClipboardUnavailableReason.value = null
        commandClipboardAttemptsRemaining.value = 2
    }

    private fun cancelCommandMode(showError: Boolean, message: String? = null) {
        resetCommandRunState()
        isCommandModeActive.value = false
        if (showError) {
            setCommandStage(CommandStage.ERROR, message)
            CoroutineScope(Dispatchers.Main).launch {
                delay(1600)
                if (!isCommandModeActive.value) {
                    setCommandStage(CommandStage.WAITING, null)
                }
            }
        } else {
            setCommandStage(CommandStage.WAITING, null)
        }
    }

    private fun commandClipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
        return text.takeIf { it.isNotBlank() }
    }

    private fun refreshCommandClipboardPreview() {
        val text = commandClipboardText()
        if (text == null) {
            commandClipboardPreview.value = ""
            commandClipboardCharCount.value = 0
            commandClipboardIsLarge.value = false
            commandClipboardUnavailableReason.value = getString(R.string.command_clipboard_unavailable)
            return
        }

        val count = text.length
        val snippetLimit = 180
        val snippet = if (count <= snippetLimit) text else text.substring(0, snippetLimit) + "…"
        val largeThreshold = 15_000
        commandClipboardPreview.value = snippet
        commandClipboardCharCount.value = count
        commandClipboardIsLarge.value = count >= largeThreshold
        commandClipboardUnavailableReason.value = null
    }

    private fun commandCursorSnapshot(): SelectionSnapshot? {
        val ic = currentInputConnection ?: return null
        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return null
        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        if (start < 0 || end < 0) return null
        val cursor = if (start == end) start else end
        return SelectionSnapshot(start = cursor, end = cursor)
    }

    private fun onCommandEnter() {
        if (keyboardState.value != KeyboardState.Ready) {
            return
        }
        if (shouldBlockExternalSend()) {
            return
        }

        resetCommandRunState()
        isCommandModeActive.value = true
        setCommandStage(CommandStage.WAITING, null)

        commandRunFocusKey = currentFocusKey()
        commandController.startRun(commandRunFocusKey)
        refreshCommandUndoState()

        CoroutineScope(Dispatchers.Main).launch {
            commandSelectedPresetId.value = repository.commandPresetId.first().orEmpty().ifBlank { TRANSFORM_PRESET_ID_TONE_REWRITE }
        }

        val resolved = selectionResolver.resolve(currentInputEditorInfo, currentInputConnection)
        when (resolved) {
            is ResolvedSelection.Selected -> {
                commandSelectionSnapshot = resolved.snapshot
                commandConfirmedText = resolved.text
                commandOriginalTextForUndo = resolved.text
                startCommandListening()
            }

            is ResolvedSelection.NeedsClipboard -> {
                commandSelectionSnapshot = resolved.snapshot
                setCommandStage(CommandStage.CLIPBOARD_CONFIRM, null)
                refreshCommandClipboardPreview()
            }

            is ResolvedSelection.None -> {
                commandSelectionSnapshot = commandCursorSnapshot()
                setCommandStage(CommandStage.CLIPBOARD_CONFIRM, null)
                refreshCommandClipboardPreview()
            }
        }
    }

    private fun onCommandCancel() {
        cancelCommandMode(showError = false)
    }

    private fun onCommandPresetSelected(id: String) {
        val normalized = id.trim()
        if (normalized.isBlank()) return
        commandSelectedPresetId.value = normalized
        CoroutineScope(Dispatchers.Main).launch {
            repository.setCommandPresetId(normalized)
        }
    }

    private fun onCommandClipboardContinue() {
        if (!isCommandModeActive.value) return
        if (commandStage.value != CommandStage.CLIPBOARD_CONFIRM) return

        val text = commandClipboardText()
        if (text == null) {
            val remaining = commandClipboardAttemptsRemaining.value - 1
            commandClipboardAttemptsRemaining.value = remaining
            refreshCommandClipboardPreview()
            if (remaining <= 0) {
                cancelCommandMode(showError = true, message = getString(R.string.command_error_clipboard_attempts_exhausted))
            }
            return
        }

        commandConfirmedText = text
        val snapshot = commandSelectionSnapshot
        commandOriginalTextForUndo = if (snapshot != null && snapshot.start != snapshot.end) text else ""
        startCommandListening()
    }

    private fun onCommandClipboardRetry() {
        if (!isCommandModeActive.value) return
        if (commandStage.value != CommandStage.CLIPBOARD_CONFIRM) return
        refreshCommandClipboardPreview()
    }

    private fun startCommandListening() {
        if (!isCommandModeActive.value) return
        if (shouldBlockExternalSend()) {
            cancelCommandMode(showError = true, message = blockedNotice())
            return
        }
        if (recorderManager?.allPermissionsGranted(this) != true) {
            launchMainActivity()
            cancelCommandMode(showError = true, message = getString(R.string.command_error_permissions))
            return
        }

        commandSpokenInstruction = null
        commandSilenceRetryUsed = false
        setCommandStage(CommandStage.LISTENING, null)
        recorderManager?.start(recordedAudioFilename)
    }

    private fun onCommandStopListening() {
        if (!isCommandModeActive.value) return
        if (commandStage.value != CommandStage.LISTENING) return

        recorderManager?.stop()
        setCommandStage(CommandStage.PROCESSING, null)

        commandJob?.cancel()
        commandJob = CoroutineScope(Dispatchers.Main).launch {
            val prefs = dataStore.data.first()
            val languageCode = prefs[LANGUAGE_CODE] ?: "auto"
            val providers = repository.providers.first()

            val effective = resolveEffectiveRuntimeConfig(
                packageName = currentInputEditorInfo?.packageName,
                languageCode = languageCode,
                prefs = prefs,
                providers = providers,
            )

            val providerId = effective.sttProviderId
            val modelId = effective.sttModelId
            val provider = providers.find { it.id == providerId }
            if (provider == null || modelId.isBlank() || provider.models.none { it.id == modelId }) {
                cancelCommandMode(showError = true, message = getString(R.string.command_error_setup_stt))
                return@launch
            }
            if (shouldBlockExternalSend()) {
                cancelCommandMode(showError = true, message = blockedNotice())
                return@launch
            }

            val providerWithApiKey = provider.copy(apiKey = secretsStore.getProviderApiKey(provider.id).orEmpty())
            val staticPrompt = if (providerWithApiKey.prompt.isNotEmpty()) providerWithApiKey.prompt else prefs[PROMPT] ?: ""
            val temperature = providerWithApiKey.temperature
            val postprocessing = prefs[POSTPROCESSING] ?: getString(R.string.settings_option_no_conversion)
            val timeout = providerWithApiKey.timeout
            val instruction = runCatching {
                transcribeInstruction(
                    languageCode = languageCode,
                    provider = providerWithApiKey,
                    modelId = modelId,
                    postprocessing = postprocessing,
                    timeout = timeout,
                    staticPrompt = staticPrompt,
                    temperature = temperature,
                )
            }.getOrNull()?.trim().orEmpty()

            if (instruction.isBlank()) {
                if (!commandSilenceRetryUsed) {
                    commandSilenceRetryUsed = true
                    startCommandListening()
                    return@launch
                }
                cancelCommandMode(showError = false)
                return@launch
            }

            commandSpokenInstruction = instruction
            runCommandTransformOrError(instruction)
        }
    }

    private suspend fun transcribeInstruction(
        languageCode: String,
        provider: ServiceProvider,
        modelId: String,
        postprocessing: String,
        timeout: Int,
        staticPrompt: String,
        temperature: Float,
    ): String? {
        return suspendCancellableCoroutine { continuation ->
            var resumed = false

            whisperTranscriber.startAsync(
                context = this@WhisperInputService,
                filename = recordedAudioFilename,
                mediaType = audioMediaType,
                attachToEnd = "",
                contextPrompt = null,
                languageCode = languageCode,
                provider = provider,
                modelId = modelId,
                postprocessing = postprocessing,
                addTrailingSpace = false,
                timeout = timeout,
                staticPrompt = staticPrompt,
                temperature = temperature,
                callback = { result ->
                    if (resumed) return@startAsync
                    resumed = true
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                },
                exceptionCallback = { message ->
                    if (resumed) return@startAsync
                    resumed = true
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(IllegalStateException(message)))
                    }
                }
            )

            continuation.invokeOnCancellation {
                whisperTranscriber.stop()
            }
        }
    }

    private suspend fun runCommandTransformOrError(spokenInstruction: String) {
        val confirmedText = commandConfirmedText?.trim().orEmpty()
        val snapshot = commandSelectionSnapshot
        val focusKey = commandRunFocusKey
        if (confirmedText.isBlank() || snapshot == null || focusKey == null) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_missing_text))
            return
        }

        if (shouldBlockExternalSend()) {
            cancelCommandMode(showError = true, message = blockedNotice())
            return
        }

        val prefs = dataStore.data.first()
        val providers = repository.providers.first()
        val languageCode = prefs[LANGUAGE_CODE] ?: "auto"
        val effective = resolveEffectiveRuntimeConfig(
            packageName = currentInputEditorInfo?.packageName,
            languageCode = languageCode,
            prefs = prefs,
            providers = providers,
        )

        val overrideProviderId = prefs[COMMAND_TEXT_PROVIDER_ID]?.trim().orEmpty()
        val overrideModelId = prefs[COMMAND_TEXT_MODEL_ID]?.trim().orEmpty()
        val overridePresent = overrideProviderId.isNotBlank() || overrideModelId.isNotBlank()
        val providerId = if (overridePresent) overrideProviderId else effective.textProviderId
        val modelId = if (overridePresent) overrideModelId else effective.textModelId

        val provider = providers.find { it.id == providerId }
        if (provider == null || modelId.isBlank() || provider.models.none { it.id == modelId }) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_setup_text))
            return
        }

        val useContext = prefs[USE_CONTEXT] ?: false
        val disclosure = PrivacyDisclosureFormatter.disclosureForCommand(
            sttProvider = null,
            sttModelId = "",
            textProvider = provider,
            textModelId = modelId,
            useContext = useContext,
        )
        val disclosureDecision = awaitFirstUseDisclosure(
            mode = FirstUseDisclosureMode.COMMAND_TEXT,
            disclosure = disclosure,
        )
        if (disclosureDecision != FirstUseDisclosureDecision.CONTINUE) {
            cancelCommandMode(showError = false)
            return
        }

        if (provider.endpoint.isBlank()) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_missing_endpoint))
            return
        }

        val providerWithApiKey = provider.copy(apiKey = secretsStore.getProviderApiKey(provider.id).orEmpty())
        if (providerWithApiKey.authMode == ProviderAuthMode.API_KEY && providerWithApiKey.apiKey.isBlank()) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_missing_api_key))
            return
        }

        val presetInstruction = presetById(commandSelectedPresetId.value)?.promptInstruction
        val promptTemplate = TransformPromptBuilder.buildTemplate(
            basePrompt = effective.prompt,
            presetInstruction = presetInstruction,
            spokenInstruction = spokenInstruction,
        )

        val temperature = if (provider.temperature > 0) provider.temperature else prefs[SMART_FIX_TEMPERATURE] ?: 0.0f
        val contextPrompt = if (useContext) currentInputConnection?.getTextBeforeCursor(500, 0)?.toString() else null

        setCommandStage(CommandStage.PROCESSING, null)

        val transformed = try {
            withContext(Dispatchers.IO) {
                smartFixer.fix(
                    text = confirmedText,
                    contextInformation = contextPrompt,
                    provider = providerWithApiKey,
                    modelId = modelId,
                    temperature = temperature,
                    promptTemplate = promptTemplate,
                )
            }
        } catch (_: CancellationException) {
            return
        } catch (e: Exception) {
            setCommandStage(CommandStage.ERROR, e.message ?: getString(R.string.command_error_transform_failed))
            return
        }

        if (!isCommandModeActive.value) {
            return
        }

        applyCommandResultOrError(
            focusKey = focusKey,
            snapshot = snapshot,
            originalText = commandOriginalTextForUndo.orEmpty(),
            appliedText = transformed,
        )
    }

    private fun applyCommandResultOrError(
        focusKey: FocusKey,
        snapshot: SelectionSnapshot,
        originalText: String,
        appliedText: String,
    ) {
        if (currentFocusKey() != focusKey) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_focus_changed))
            return
        }

        val ic = currentInputConnection
        if (ic == null) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_no_input_connection))
            return
        }

        val trimmed = appliedText.trimEnd()
        if (trimmed.isBlank()) {
            setCommandStage(CommandStage.ERROR, getString(R.string.command_error_empty_result))
            return
        }

        val start = snapshot.start
        val end = snapshot.end
        val replaced = runCatching {
            ic.beginBatchEdit()
            try {
                if (!ic.setSelection(start, end)) return@runCatching false
                if (!ic.commitText(trimmed, 1)) return@runCatching false
                ic.setSelection(start, start + trimmed.length)
                true
            } finally {
                ic.endBatchEdit()
            }
        }.getOrDefault(false)

        if (!replaced) {
            setCommandStage(CommandStage.ERROR, getString(R.string.command_error_apply_failed))
            return
        }

        val now = System.currentTimeMillis()
        commandController.recordUndoAfterApply(
            focusKey = focusKey,
            snapshot = snapshot,
            originalText = originalText,
            appliedText = trimmed,
            nowMs = now,
        )
        refreshCommandUndoState()
        setCommandStage(CommandStage.DONE, null)

        CoroutineScope(Dispatchers.Main).launch {
            delay(1200)
            if (commandStage.value == CommandStage.DONE) {
                isCommandModeActive.value = false
                setCommandStage(CommandStage.WAITING, null)
            }
        }
    }

    private fun onCommandRetryTransform() {
        if (!isCommandModeActive.value) return
        if (commandStage.value != CommandStage.ERROR) return
        val instruction = commandSpokenInstruction ?: return
        commandJob?.cancel()
        commandJob = CoroutineScope(Dispatchers.Main).launch {
            runCommandTransformOrError(instruction)
        }
    }

    private fun onCommandUndo() {
        val now = System.currentTimeMillis()
        val entry = commandController.consumeUndoEntry(now) ?: run {
            refreshCommandUndoState()
            return
        }
        if (currentFocusKey() != entry.focusKey) {
            refreshCommandUndoState()
            return
        }
        val ic = currentInputConnection ?: run {
            refreshCommandUndoState()
            return
        }

        val start = entry.snapshot.start
        val appliedEnd = start + entry.appliedText.length
        val original = entry.originalText

        val restored = runCatching {
            ic.beginBatchEdit()
            try {
                if (!ic.setSelection(start, appliedEnd)) return@runCatching false
                if (!ic.commitText(original, 1)) return@runCatching false
                ic.setSelection(start, start + original.length)
                true
            } finally {
                ic.endBatchEdit()
            }
        }.getOrDefault(false)

        refreshCommandUndoState()
        if (!restored) {
            return
        }
    }

    private fun supportsOpenAiRealtime(provider: ServiceProvider): Boolean {
        if (provider.type != ProviderType.OPENAI) return false
        if (provider.endpoint.isBlank()) return false
        if (provider.apiKey.isBlank()) return false
        return OpenAiRealtimeSttClient.buildRequest(provider.endpoint, "test", provider.apiKey) != null
    }

    private fun closeRealtimeClient() {
        realtimeSttClient?.close()
        realtimeSttClient = null
    }

    private fun closeRealtimeSession() {
        closeRealtimeClient()
        realtimeToken = null
        realtimeSttClient = null
        realtimeFinalTranscript = null
        realtimeBestEffortTranscript = null
        pendingRealtimeProvider = null
        pendingRealtimeModelId = null
    }

    private fun activeDictationAnalyticsSession(
        token: DictationController.SendToken? = null,
    ): DictationAnalyticsSession? {
        val session = dictationAnalyticsSession ?: return null
        if (token != null && session.sessionId != token.sessionId) {
            return null
        }
        return session
    }

    private fun completeDictationAnalyticsSession(
        token: DictationController.SendToken? = null,
        rawText: String? = null,
        finalInsertedText: String? = null,
    ) {
        val session = activeDictationAnalyticsSession(token) ?: return
        rawText?.let(session::recordRawTranscript)
        finalInsertedText?.let(session::recordFinalInsertedText)
        val outcome = session.finalize(durationMs = recordingTimeMs.value) as? DictationAnalyticsSession.Outcome.Completed ?: return
        dictationAnalyticsSession = null
        persistDictationAnalyticsOutcome(outcome)
    }

    private fun recordTerminalDictationAnalyticsForCancellation() {
        val session = dictationAnalyticsSession ?: return
        val outcome = when (keyboardState.value) {
            KeyboardState.SmartFixing -> session.finalize(durationMs = recordingTimeMs.value)
            KeyboardState.Recording,
            KeyboardState.RecordingLocked,
            KeyboardState.Paused,
            KeyboardState.PausedLocked,
            KeyboardState.Transcribing
            -> session.cancel(durationMs = recordingTimeMs.value)
            KeyboardState.Ready -> null
        } ?: return
        dictationAnalyticsSession = null
        persistDictationAnalyticsOutcome(outcome)
    }

    private fun persistDictationAnalyticsOutcome(outcome: DictationAnalyticsSession.Outcome) {
        CoroutineScope(Dispatchers.IO).launch {
            when (outcome) {
                is DictationAnalyticsSession.Outcome.Completed -> {
                    analyticsRepository.recordCompletedSession(
                        durationMs = outcome.durationMs,
                        rawText = outcome.rawText,
                        finalInsertedText = outcome.finalInsertedText,
                        recordedOn = LocalDate.now(),
                    )
                }

                is DictationAnalyticsSession.Outcome.Cancelled -> {
                    analyticsRepository.recordCancelledSession(
                        durationMs = outcome.durationMs,
                        recordedOn = LocalDate.now(),
                    )
                }
            }
        }
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
        if (keyboardState.value.isRecording) {
            performFeedback(isDelete = true)
            return
        }
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

    private fun launchMainActivity(destination: String? = null) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!destination.isNullOrBlank()) {
            intent.putExtra(EXTRA_SETTINGS_DESTINATION, destination)
        }
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
            secureFieldExplanationDontShowAgain.value = dataStore.data
                .map { it[SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN] ?: false }
                .first()
            refreshExternalSendBlock(currentInputEditorInfo)
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        Log.d(TAG, "onWindowHidden")
        onFirstUseDisclosureCancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        releaseAllMediaPlayers()

        if (isExternalSendingActive()) {
            dictationController.onWindowHidden(toastMessage = "Dictation paused")
        } else {
            whisperTranscriber.stop()
            smartFixer.cancel()
            closeRealtimeSession()
            recorderManager?.stop()
            resetCommandRunState()
            isCommandModeActive.value = false
            stopTimer()
            if (keyboardState.value != KeyboardState.Ready) setKeyboardState(KeyboardState.Ready)
        }
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
        networkLoggingPreferenceJob?.cancel()
        onFirstUseDisclosureCancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        whisperTranscriber.stop()
        smartFixer.cancel()
        closeRealtimeSession()
        recorderManager?.stop()
        resetCommandRunState()
        commandUndoJob?.cancel()
        commandUndoJob = null
        releaseAllMediaPlayers()
    }
}
