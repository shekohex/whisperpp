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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.analytics.AnalyticsModelSample
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.DictationAnalyticsSession
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.command.CommandController
import com.github.shekohex.whisperpp.command.CommandDisclosureDecision
import com.github.shekohex.whisperpp.command.CommandDisclosureGateCoordinator
import com.github.shekohex.whisperpp.command.CommandListeningStartResult
import com.github.shekohex.whisperpp.command.CommandPostRecordingResult
import com.github.shekohex.whisperpp.command.CommandRuntimeConfig
import com.github.shekohex.whisperpp.command.CommandStage
import com.github.shekohex.whisperpp.command.ResolvedSelection
import com.github.shekohex.whisperpp.command.SelectionResolver
import com.github.shekohex.whisperpp.command.SelectionSnapshot
import com.github.shekohex.whisperpp.command.TransformPromptBuilder
import com.github.shekohex.whisperpp.command.replaceAndSelect
import com.github.shekohex.whisperpp.core.audio.AmplitudeMeter
import com.github.shekohex.whisperpp.core.audio.AudioFocusController
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.core.audio.SoundCues
import com.github.shekohex.whisperpp.core.audio.SystemAudioFocusGateway
import com.github.shekohex.whisperpp.core.permissions.MicPermission
import com.github.shekohex.whisperpp.core.text.TranscriptFilter
import com.github.shekohex.whisperpp.core.text.WordReplacements
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_SEND_AFTER_INSERT
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_SWITCH_BACK
import com.github.shekohex.whisperpp.data.PreferenceKeys.CANCEL_CONFIRMATION
import com.github.shekohex.whisperpp.data.PreferenceKeys.DISCLOSURE_SHOWN_DICTATION_AUDIO
import com.github.shekohex.whisperpp.data.PreferenceKeys.FILTER_FILLERS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.FORMAT_PARAGRAPHS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.HAPTIC_FEEDBACK_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.LANGUAGE_CODE
import com.github.shekohex.whisperpp.data.PreferenceKeys.POSTPROCESSING
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_TEMPERATURE
import com.github.shekohex.whisperpp.data.PreferenceKeys.SOUND_EFFECTS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.data.dictionary.DictionaryRepository
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.dictation.EnhancementRunner
import com.github.shekohex.whisperpp.dictation.FocusIdentity
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.dictation.AudioRecorderSource
import com.github.shekohex.whisperpp.domain.dictation.AutoSend
import com.github.shekohex.whisperpp.domain.dictation.CompletionStats
import com.github.shekohex.whisperpp.domain.dictation.DictationOptions
import com.github.shekohex.whisperpp.domain.dictation.DictationSession
import com.github.shekohex.whisperpp.domain.dictation.SessionEvent
import com.github.shekohex.whisperpp.domain.dictation.SessionState
import com.github.shekohex.whisperpp.domain.dictation.TextCommitter
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.domain.providers.toProviderUserMessage
import com.github.shekohex.whisperpp.domain.dictation.toKeyboardState
import com.github.shekohex.whisperpp.ime.AutoLearnCaptureStateMachine
import com.github.shekohex.whisperpp.ime.AutoLearnObserver
import com.github.shekohex.whisperpp.ime.DictationEnhancementStep
import com.github.shekohex.whisperpp.ime.DictationStartPlanner
import com.github.shekohex.whisperpp.ime.FirstUseDisclosureGate
import com.github.shekohex.whisperpp.ime.ImeViewLifecycle
import com.github.shekohex.whisperpp.ime.MicPermissionBus
import com.github.shekohex.whisperpp.ime.MicPermissionGate
import com.github.shekohex.whisperpp.ime.PermissionRequestActivity
import com.github.shekohex.whisperpp.ime.AUTO_LANGUAGE_CODE
import com.github.shekohex.whisperpp.ime.keyboardLanguageCodes
import com.github.shekohex.whisperpp.ime.keyboardLanguageDisplayName
import com.github.shekohex.whisperpp.ime.keyboardLanguageShortLabel
import com.github.shekohex.whisperpp.ime.runtimeWarningNotice
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardLanguageOption
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isRecording
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import com.github.shekohex.whisperpp.privacy.SendPolicyRepository
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeStyle
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeUiState
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardModeController
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardScreen
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

private const val RECORDING_FILE_PREFIX = "rec_"
private const val AUDIO_MEDIA_TYPE_WAV = "audio/wav"
private const val IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL = 28
private const val TAG = "WhisperInputService"
private const val MIGRATIONS_WAIT_MS = 2_000L
private const val PRIVACY_SAFETY_DESTINATION = "privacy_safety"
private const val RECORDING_TICK_MS = 100L
private const val AUTO_SWITCH_BACK_DELAY_MS = 150L

class WhisperInputService : InputMethodService() {
    private val dataStore: DataStore<Preferences>
        get() = appContainer.settingsDataStore
    private val providerFactory: ProviderFactory
        get() = appContainer.providerFactory
    private val serviceScope = MainScope() + Dispatchers.Main.immediate
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioFocus: AudioFocusController
    private lateinit var soundCues: SoundCues
    private lateinit var session: DictationSession
    private val textCommitter = TextCommitter { currentInputConnection }
    // First-use dictation disclosure shown before a start; null until DataStore delivered.
    private lateinit var dictationDisclosureShown: StateFlow<Boolean?>
    private var dictationDisclosureJob: Job? = null
    
    private lateinit var repository: SettingsRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var sendPolicyRepository: SendPolicyRepository
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var autoLearnObserver: AutoLearnObserver
    // Latest dictionary rules and filter switches, read synchronously by the post-processing step.
    private lateinit var wordReplacements: StateFlow<WordReplacements>
    private lateinit var transcriptFilterOptions: StateFlow<TranscriptFilter.Options>

    // Owners for the current input view only; replaced on every onCreateInputView().
    private var viewLifecycle: ImeViewLifecycle? = null
    // Saved state carried from a destroyed input view to its replacement (e.g. across rotation).
    private var savedViewState: Bundle? = null
    private var inputViewGeneration: Int = 0
    // Per-app send policy rules; null until DataStore delivered the first value.
    private lateinit var sendPolicyRules: StateFlow<Map<String, Boolean>?>
    
    // Compose State
    private var keyboardState = mutableStateOf(KeyboardState.Ready)
    private var languageLabel = mutableStateOf("")
    private val selectedLanguage = mutableStateOf(AUTO_LANGUAGE_CODE)
    private val languageOptions = mutableStateOf<List<KeyboardLanguageOption>>(emptyList())
    // Confirm action of the open "discard recording?" sheet; null when it is closed.
    private val pendingCancelConfirm = mutableStateOf<(() -> Unit)?>(null)
    // Back closes the topmost in-keyboard sheet first (set by KeyboardScreen).
    private var overlayBack: (() -> Unit)? = null
    private var micLevel = mutableStateOf(0f)
    private var shouldOfferImeSwitch = mutableStateOf(false)
    private var recordingTimeMs = mutableStateOf(0L)
    private var showLongPressHint = mutableStateOf(false)
    private val tapToRecord = mutableStateOf(true)
    private var undoAvailable = mutableStateOf(false)
    private var undoQuickActionVisible = mutableStateOf(false)
    private var enhancementNotice = mutableStateOf<EnhancementNoticeUiState?>(null)
    private var enhancementUndoAvailable = mutableStateOf(false)
    private var enhancementNoticeJob: Job? = null
    private var externalSendBlockReason = mutableStateOf<SecureFieldDetector.Reason?>(null)
    private var externalSendBlockedByAppPolicy = mutableStateOf(false)
    private var externalSendBlockedPackageName = mutableStateOf<String?>(null)
    private var secureFieldExplanationDontShowAgain = mutableStateOf(false)
    private val micPermissionMissing = mutableStateOf(false)
    private val micPermissionGate = MicPermissionGate(
        isGranted = { MicPermission.isGranted(this) },
        onMissingChanged = { micPermissionMissing.value = it },
    )
    private lateinit var disclosureGate: FirstUseDisclosureGate
    private lateinit var configLoader: RuntimeConfigLoader
    private lateinit var startPlanner: DictationStartPlanner
    private val pendingCommitText = mutableStateOf<String?>(null)
    private val sessionStarting = mutableStateOf(false)
    private var recordingCounter = 0

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

    /** Bumped on every command capture start/cancel; a start that returns stale must not touch shared state. */
    private var commandCaptureGeneration = 0

    /** The exact file the current command capture records into. */
    private var commandAudioFile: File? = null

    private val focusIdentity = FocusIdentity()
    // Keyboard mode chip (§4.10); the explicit choice ends when the window hides or the app changes.
    private lateinit var keyboardModes: KeyboardModeController
    private var lastFocusKey: FocusKey? = null

    override fun onCreate() {
        super.onCreate()
        logLifecycle("onCreate")
        val container = appContainer
        repository = container.settingsRepository
        analyticsRepository = container.analyticsRepository
        sendPolicyRepository = container.sendPolicyRepository
        sendPolicyRules = sendPolicyRepository.getAllRulesFlow()
            .stateIn(serviceScope, SharingStarted.Eagerly, null)
        dictionaryRepository = container.dictionaryRepository
        autoLearnObserver = createAutoLearnObserver()
        wordReplacements = dictionaryRepository.replacements
            .map(WordReplacements::compile)
            .flowOn(Dispatchers.Default)
            .stateIn(serviceScope, SharingStarted.Eagerly, WordReplacements.EMPTY)
        transcriptFilterOptions = dataStore.data
            .map { prefs ->
                TranscriptFilter.Options(
                    removeFillers = prefs[FILTER_FILLERS_ENABLED] ?: false,
                    formatParagraphs = prefs[FORMAT_PARAGRAPHS_ENABLED] ?: false,
                )
            }
            .stateIn(serviceScope, SharingStarted.Eagerly, TranscriptFilter.Options())
        audioRecorder = AudioRecorder(this, serviceScope, AmplitudeMeter())
        serviceScope.launch(Dispatchers.IO) { deleteStaleRecordings() }
        audioFocus = AudioFocusController(
            SystemAudioFocusGateway(getSystemService(Context.AUDIO_SERVICE) as AudioManager),
            repository.pauseMediaWhileRecording.stateIn(serviceScope, SharingStarted.Eagerly, true),
        )
        soundCues = SoundCues(this, serviceScope, repository.soundEffectsEnabled)
        serviceScope.launch { repository.tapToRecord.collect { tapToRecord.value = it } }
        configLoader = RuntimeConfigLoader(
            dataStore = dataStore,
            repository = repository,
            hasCredential = { !container.secretsStore.getProviderApiKey(it).isNullOrBlank() },
        )
        disclosureGate = FirstUseDisclosureGate(resources, dataStore, serviceScope)
        keyboardModes = KeyboardModeController(
            repository = container.modesRepository,
            providers = repository.providers,
            profiles = repository.promptProfiles,
            prefs = dataStore.data,
            hasCredential = { !container.secretsStore.getProviderApiKey(it).isNullOrBlank() },
            scope = serviceScope,
        )
        startPlanner = createStartPlanner()
        dictationDisclosureShown = dataStore.data
            .map { it[DISCLOSURE_SHOWN_DICTATION_AUDIO] ?: false }
            .stateIn(serviceScope, SharingStarted.Eagerly, null)
        session = DictationSession(
            DictationSession.Deps(
                recorder = AudioRecorderSource(audioRecorder),
                focus = audioFocus,
                cues = soundCues::play,
                transcriber = ::transcribe,
                enhancer = createEnhancementStep(),
                committer = textCommitter,
                planner = startPlanner::plan,
                sendAllowed = { !shouldBlockExternalSend() },
                commandModeActive = { isCommandModeActive.value },
                clock = SystemClock::elapsedRealtime,
                scope = serviceScope,
                postProcess = ::postProcessTranscript,
                clipboardText = ::commandClipboardText,
                history = container.historyRecorder,
            )
        )
        observeAudioCore()
        observeSession()
        refreshUndoState()

        serviceScope.launch {
            // The retry runs from onStartInputView once the host app regains focus.
            MicPermissionBus.results.collect {
                micPermissionGate.onResult(it.granted, it.retryAction)
            }
        }
        serviceScope.launch {
            sendPolicyRules.collect { rules ->
                if (rules != null) onExternalSendBlockMayHaveChanged(currentInputEditorInfo)
            }
        }
        serviceScope.launch {
            dataStore.data.map { it[VERBOSE_NETWORK_LOGS_ENABLED] ?: false }.collect { enabled ->
                appContainer.httpLoggingInterceptor.setEnabled(enabled)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        logLifecycle("onStartInput restarting=$restarting")
        // The single place a focus identity is minted: once per editor, not per restart or view start.
        val focusKey = FocusKey.from(attribute, focusIdentity.onStartInput(restarting))
        keyboardModes.onPackageChanged(attribute?.packageName)
        if (focusKey != lastFocusKey) {
            lastFocusKey = focusKey
            session.onFocusChanged(focusKey)
            autoLearnObserver.onFocusChanged(focusKey)
            if (isCommandModeActive.value) {
                cancelCommandMode(showError = true, message = getString(R.string.command_error_focus_changed))
            }
        }
        showUndoQuickActionIfAvailable()
        refreshEnhancementUndoState()
        onExternalSendBlockMayHaveChanged(attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        viewLifecycle?.onShown()
        logLifecycle("onStartInputView restarting=$restarting")
        showUndoQuickActionIfAvailable()
        refreshEnhancementUndoState()
        onExternalSendBlockMayHaveChanged(info)
        micPermissionGate.onInputViewStarted()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        viewLifecycle?.onHidden()
        logLifecycle("onFinishInputView finishingInput=$finishingInput")
        // Transient overlays belong to the view that is going away.
        disclosureGate.cancel()
        showLongPressHint.value = false
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

    private fun createStartPlanner() = DictationStartPlanner(
        DictationStartPlanner.Deps(
            config = configLoader,
            providers = providerFactory,
            awaitMigrations = { appContainer.awaitMigrations(MIGRATIONS_WAIT_MS) },
            awaitSendPolicy = { sendPolicyRules.first { it != null } },
            isSendBlocked = ::shouldBlockExternalSend,
            checkMicPermission = {
                // The finger is no longer on the button after the permission screen: record hands-free,
                // through the normal start path so policy and disclosure are checked again.
                micPermissionGate.check(MicPermissionGate.ACTION_DICTATION) { onPressStart(locked = true) }
            },
            recordingFile = ::recordingFile,
            scriptConversion = ::scriptConversion,
            audioMediaType = AUDIO_MEDIA_TYPE_WAV,
            onBlocked = ::handleBlockedExternalSendAction,
            onSetupRequired = ::showSetupRequiredToast,
            describeWarnings = { warnings, channel -> runtimeWarningNotice(this, warnings, channel) },
            onNotice = { showEnhancementNotice(it, EnhancementNoticeStyle.INFO) },
            onStreamingUnsupported = { showToast(getString(R.string.dictation_streaming_unsupported)) },
            vocabulary = { dictionaryRepository.vocabulary.first() },
        )
    )

    private fun createEnhancementStep() = DictationEnhancementStep(
        DictationEnhancementStep.Deps(
            context = this,
            config = configLoader,
            enhancementPresetId = repository.enhancementPresetId,
            providers = providerFactory,
            runner = EnhancementRunner(),
            disclosures = disclosureGate,
            blockedNotice = { if (shouldBlockExternalSend()) blockedNotice() else null },
            onWarning = { showEnhancementNotice(it, EnhancementNoticeStyle.INFO) },
            onRequestSent = { performFeedback() },
            defaultPrompt = ::readEnhancementDefaultPromptOrEmpty,
            systemPreamble = ::readEnhancementSystemPreambleOrEmpty,
            vocabulary = { dictionaryRepository.vocabulary.first() },
        )
    )

    private fun recordingDir(): File = externalCacheDir ?: cacheDir

    /** A distinct file per take, so the late teardown of a previous capture can never delete this one. */
    private fun recordingFile(): File =
        File(recordingDir(), "$RECORDING_FILE_PREFIX${SystemClock.elapsedRealtime()}_${++recordingCounter}.wav")

    /** Takes left behind by a killed process; nothing records yet when this runs. */
    private fun deleteStaleRecordings() {
        recordingDir().listFiles { file -> file.name.startsWith(RECORDING_FILE_PREFIX) || file.name == "recorded.wav" }
            ?.forEach { it.delete() }
    }

    /**
     * Filter → word replacements (WP-2.4) with the mode's formatting switches over the global ones; the
     * session adds the "Add trailing space" space afterwards.
     */
    private fun postProcessTranscript(text: String, options: DictationOptions): String {
        val global = transcriptFilterOptions.value
        val filterOptions = global.copy(
            removeFillers = options.removeFillers ?: global.removeFillers,
            formatParagraphs = options.formatParagraphs ?: global.formatParagraphs,
        )
        val filtered = TranscriptFilter.apply(text, filterOptions)
        return wordReplacements.value.apply(filtered)
    }

    /** Batch transcription via [ProviderFactory]; failures carry a user-facing message. */
    private suspend fun transcribe(file: File, request: TranscriptionRequest): Result<String> {
        return providerFactory.transcribe(file, request).fold(
            onSuccess = { Result.success(it.text) },
            onFailure = { Result.failure(IOException(it.toProviderUserMessage(this), it)) },
        )
    }

    /** The post-processing preference stores the localized option label. */
    private fun scriptConversion(stored: String?): ScriptConversion = when (stored) {
        getString(R.string.settings_option_to_simplified) -> ScriptConversion.TO_SIMPLIFIED
        getString(R.string.settings_option_to_traditional) -> ScriptConversion.TO_TRADITIONAL
        else -> ScriptConversion.NONE
    }

    private fun observeSession() {
        serviceScope.launch {
            session.state.collectLatest { state ->
                keyboardState.value = state.toKeyboardState()
                sessionStarting.value = state is SessionState.Starting
                when (state) {
                    is SessionState.Recording -> {
                        showLongPressHint.value = false
                        // Re-collected on every transition, so pausing stops the tick (B-07).
                        while (true) {
                            recordingTimeMs.value = state.elapsedMs(SystemClock.elapsedRealtime())
                            if (state.paused) break
                            delay(RECORDING_TICK_MS)
                        }
                    }
                    is SessionState.Transcribing, is SessionState.Committing, is SessionState.Enhancing -> Unit
                    else -> recordingTimeMs.value = 0L
                }
            }
        }
        serviceScope.launch { session.events.collect(::onSessionEvent) }
        serviceScope.launch { session.pendingCommit.collect { pendingCommitText.value = it } }
    }

    private fun onSessionEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.Notice -> showToast(getString(event.resId))
            is SessionEvent.NoticeText -> showToast(event.text)
            SessionEvent.ShortPressHint -> showLongPressHint.value = true
            is SessionEvent.RecordingFailed -> {
                if (event.kind != AudioRecorder.ErrorKind.CANCELLED) showToast(recorderErrorMessage(event.kind))
            }
            is SessionEvent.TranscriptionFailed -> {
                Log.e(TAG, "Transcription failed")
                showToast(getString(R.string.dictation_transcription_failed, event.message))
                performFeedback()
            }
            SessionEvent.TranscriptionEmpty -> {
                showToast(getString(R.string.toast_transcription_empty))
                performFeedback()
            }
            SessionEvent.SendBlocked -> showToast(blockedNotice())
            is SessionEvent.Committed -> {
                showUndoQuickActionIfAvailable()
                performFeedback()
            }
            is SessionEvent.CommitBlockedByFocus -> performFeedback()
            is SessionEvent.CopyToClipboard -> {
                copyToClipboard(event.text)
                showToast(getString(R.string.dictation_copied_mode))
            }
            is SessionEvent.CommitFailed -> {
                val message = when (event.reason) {
                    SessionEvent.CommitFailed.Reason.NO_FIELD -> R.string.dictation_copied_no_field
                    SessionEvent.CommitFailed.Reason.BLOCKED -> R.string.dictation_copied_blocked
                    SessionEvent.CommitFailed.Reason.INSERT_FAILED -> R.string.dictation_insert_failed
                }
                // A rejected insert stays in the pending-commit bar (Insert here / Copy) instead.
                if (event.reason != SessionEvent.CommitFailed.Reason.INSERT_FAILED) copyToClipboard(event.text)
                showToast(getString(message))
            }
            is SessionEvent.EnhancementNotice -> showEnhancementNotice(
                event.text,
                if (event.isError) EnhancementNoticeStyle.ERROR else EnhancementNoticeStyle.INFO,
            )
            SessionEvent.EnhancementApplied -> refreshEnhancementUndoState()
            is SessionEvent.EnhancementKeptRaw -> showEnhancementNotice(
                getString(R.string.keyboard_enhancement_kept_raw, event.label),
                EnhancementNoticeStyle.INFO,
            )
            is SessionEvent.Completed -> {
                val analytics = DictationAnalyticsSession(sessionId = 0L).apply {
                    recordRawTranscript(event.rawText)
                    recordFinalInsertedText(event.finalText)
                }
                analytics.finalize(event.durationMs, event.stats.toAnalyticsSample())?.let(::persistDictationAnalyticsOutcome)
                autoLearnObserver.onCompleted(event)
                afterDictationCompleted(event)
            }
            is SessionEvent.Discarded -> {
                DictationAnalyticsSession(sessionId = 0L).cancel(event.durationMs)?.let(::persistDictationAnalyticsOutcome)
            }
        }
    }

    /**
     * Auto-send (WP-2.4) for the field the run inserted into, then the optional switch back. Sends only
     * if that field is still focused, sending is still allowed and no newer run started meanwhile.
     */
    private fun afterDictationCompleted(event: SessionEvent.Completed) {
        val focus = event.focus
        serviceScope.launch {
            val autoSend = event.inserted && (event.autoSend ?: shouldAutoSend(focus.packageName))
            if (lastFocusKey == focus && autoSend) {
                autoLearnObserver.onAutoSend(focus)
                delay(AutoSend.DELAY_MS)
                val stillTarget = lastFocusKey == focus && session.latestRunId == event.runId && !session.isBusy
                if (stillTarget && !shouldBlockExternalSend()) {
                    textCommitter.performAutoSend(focus, currentInputEditorInfo?.imeOptions ?: 0)
                }
            }
            switchBackIfEnabled()
        }
    }

    /** Fallback when the run carries no mode decision: the per-app override wins over the global switch. */
    private suspend fun shouldAutoSend(packageName: String): Boolean {
        val override = repository.appPromptMappings.first().find { it.packageName == packageName }?.autoSend
        return override ?: (dataStore.data.first()[AUTO_SEND_AFTER_INSERT] ?: false)
    }

    private suspend fun switchBackIfEnabled() {
        if (dataStore.data.first()[AUTO_SWITCH_BACK] != true) return
        delay(AUTO_SWITCH_BACK_DELAY_MS)
        if (!session.isBusy) onSwitchIme()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateInputView(): View {
        val viewOwners = replaceViewLifecycle()
        inputViewGeneration += 1
        logLifecycle("onCreateInputView generation=$inputViewGeneration")

        shouldOfferImeSwitch.value = if (Build.VERSION.SDK_INT >= IME_SWITCH_OPTION_AVAILABILITY_API_LEVEL) {
            shouldOfferSwitchingToNextInputMethod()
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("DEPRECATION")
            imm.shouldOfferSwitchingToNextInputMethod(window?.window?.attributes?.token)
        }

        window?.window?.let { win ->
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
            // Decor view too: dialogs/popups/sheets look the owners up from the window root.
            viewOwners.attach(this, window?.window?.decorView)

            setContent {
                WhisperToInputTheme {
                    KeyboardScreen(
                        state = keyboardState.value,
                        languageLabel = languageLabel.value,
                        level = { micLevel.value },
                        recordingTimeMs = { recordingTimeMs.value },
                        showLongPressHint = showLongPressHint.value,
                        tapToRecord = tapToRecord.value,
                        isStarting = sessionStarting.value,
                        undoAvailable = undoAvailable.value,
                        undoQuickActionVisible = undoQuickActionVisible.value,
                        enhancementNotice = enhancementNotice.value,
                        enhancementUndoAvailable = enhancementUndoAvailable.value,
                        onRecordIntent = ::onRecordIntent,
                        onCancelAction = { onCancelAction() },
                        onDeleteAction = { onDeleteText() },
                        onUndoAction = { onUndoAction() },
                        onEnhancementUndoAction = {
                            performFeedback(isDelete = true)
                            val applied = textCommitter.applyEnhancementUndo(lastFocusKey)
                            refreshEnhancementUndoState()
                            if (!applied) {
                                showEnhancementNotice(
                                    getString(R.string.keyboard_enhancement_undo_unavailable),
                                    EnhancementNoticeStyle.INFO,
                                )
                            }
                        },
                        onDismissEnhancementNotice = { dismissEnhancementNotice() },
                        onOpenSettings = { launchMainActivity() },
                        onOpenSettingsDestination = { launchMainActivity(it) },
                        languageOptions = languageOptions.value,
                        selectedLanguage = selectedLanguage.value,
                        onLanguageSelected = { selectLanguage(it) },
                        cancelConfirmationVisible = pendingCancelConfirm.value != null,
                        onCancelConfirmed = {
                            val confirm = pendingCancelConfirm.value
                            pendingCancelConfirm.value = null
                            confirm?.invoke()
                        },
                        onCancelConfirmDismissed = { pendingCancelConfirm.value = null },
                        onOverlayBackChanged = { overlayBack = it },
                        modeState = keyboardModes.uiState.collectAsState().value,
                        onModeSelected = { keyboardModes.select(it) },
                        onDismissHint = { showLongPressHint.value = false },
                        externalSendBlockedReason = externalSendBlockReason.value,
                        externalSendBlockedByAppPolicy = externalSendBlockedByAppPolicy.value,
                        blockedPackageName = externalSendBlockedPackageName.value,
                        showSecureFieldExplanation = !secureFieldExplanationDontShowAgain.value,
                        firstUseDisclosure = disclosureGate.uiState.value,
                        onBlockedAction = { handleBlockedExternalSendAction() },
                        onDontShowSecureFieldExplanationAgain = { saveSecureFieldDontShowAgain() },
                        onFirstUseDisclosureContinue = { disclosureGate.onContinue() },
                        onFirstUseDisclosureCancel = { disclosureGate.cancel() },
                        onFirstUseDisclosureOpenPrivacySafety = { onFirstUseDisclosureOpenPrivacySafety() },
                        micPermissionMissing = micPermissionMissing.value,
                        onAllowMicPermission = { launchMicPermissionRequest() },
                        pendingCommitText = pendingCommitText.value,
                        onPendingCommitInsert = { session.onPendingCommitInsert() },
                        onPendingCommitCopy = { copyPendingCommit() },
                        onPendingCommitDiscard = { session.onPendingCommitDiscard() },

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

    private fun updateLanguageLabel() {
        serviceScope.launch {
            val languageCode = dataStore.data.map { it[LANGUAGE_CODE] ?: AUTO_LANGUAGE_CODE }.first()
            val autoLabel = getString(R.string.language_auto_label)
            selectedLanguage.value = languageCode
            languageLabel.value = keyboardLanguageShortLabel(languageCode, autoLabel)
            val systemLocales = LocaleList.getDefault()
            val systemLanguages = (0 until systemLocales.size()).map { systemLocales.get(it).language }
            languageOptions.value = keyboardLanguageCodes(systemLanguages).map { code ->
                KeyboardLanguageOption(code, keyboardLanguageDisplayName(code, autoLabel))
            }
        }
    }

    private fun refreshUndoState() {
        val available = textCommitter.isUndoAvailable(lastFocusKey)
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
        enhancementUndoAvailable.value = textCommitter.isEnhancementUndoAvailable(lastFocusKey)
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
        enhancementNoticeJob = serviceScope.launch {
            delay(2600)
            enhancementNotice.value = null
            enhancementNoticeJob = null
        }
    }

    private fun readEnhancementSystemPreambleOrEmpty(): String {
        return runCatching {
            resources.openRawResource(R.raw.enhancement_system_preamble).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    private fun readEnhancementDefaultPromptOrEmpty(): String {
        return runCatching {
            resources.openRawResource(R.raw.enhancement_prompt).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    private fun currentExternalSendBlock(editorInfo: EditorInfo? = currentInputEditorInfo): SecureFieldDetector.Result? {
        val detection = SecureFieldDetector.detect(editorInfo)
        return if (detection.isSecure) detection else null
    }

    private fun refreshExternalSendBlock(editorInfo: EditorInfo?) {
        externalSendBlockReason.value = currentExternalSendBlock(editorInfo)?.reason
        val packageName = editorInfo?.packageName?.trim().orEmpty().ifBlank { null }
        val blockedByPolicy = packageName != null && sendPolicyRules.value?.get(packageName) == true
        externalSendBlockedByAppPolicy.value = blockedByPolicy
        externalSendBlockedPackageName.value = if (blockedByPolicy) packageName else null
    }

    private fun onExternalSendBlockMayHaveChanged(editorInfo: EditorInfo?) {
        refreshExternalSendBlock(editorInfo)
        if (isExternalSendBlocked() && session.isBusy) {
            session.onExternalSendBlocked()
        }
    }

    private fun isExternalSendBlocked(): Boolean {
        return externalSendBlockReason.value != null || externalSendBlockedByAppPolicy.value
    }

    private fun blockedNotice(): String {
        return if (externalSendBlockedByAppPolicy.value) {
            getString(R.string.keyboard_sending_blocked_app)
        } else {
            getString(R.string.secure_field_sending_stopped)
        }
    }

    private fun observeAudioCore() {
        serviceScope.launch {
            audioRecorder.level.collect { level ->
                micLevel.value = level
            }
        }
        serviceScope.launch {
            audioRecorder.events.collect { event ->
                if (event is AudioRecorder.Event.Error) onRecorderCaptureError(event.kind)
            }
        }
    }

    /**
     * Command-mode capture. Focus order on start: acquire focus → start cue → capture. Releases focus on
     * failure. A start cancelled while the recorder was opening returns stale and leaves the shared focus
     * and recorder alone: by then they may belong to a dictation run (F-08).
     */
    private suspend fun startCapture(): Result<Unit> {
        val generation = ++commandCaptureGeneration
        audioFocus.acquire()
        performFeedback(cue = SoundCues.Cue.START)
        val file = recordingFile()
        commandAudioFile = file
        val result = audioRecorder.start(file)
        if (generation != commandCaptureGeneration) {
            if (result.isSuccess && !session.isBusy) audioRecorder.cancel()
            return Result.failure(AudioRecorder.RecorderException(AudioRecorder.ErrorKind.CANCELLED, "Command capture cancelled"))
        }
        result.onFailure { error ->
            audioFocus.release()
            val kind = (error as? AudioRecorder.RecorderException)?.kind
            if (kind != AudioRecorder.ErrorKind.CANCELLED) {
                Toast.makeText(this, recorderErrorMessage(kind), Toast.LENGTH_SHORT).show()
            }
        }
        return result
    }

    private suspend fun stopCapture(): Result<File> {
        val generation = commandCaptureGeneration
        val result = audioRecorder.stop()
        if (generation == commandCaptureGeneration) audioFocus.release()
        return result
    }

    private fun cancelCapture() {
        commandCaptureGeneration++
        audioRecorder.cancel()
        audioFocus.release()
    }

    private fun recorderErrorMessage(kind: AudioRecorder.ErrorKind?): String {
        return when (kind) {
            AudioRecorder.ErrorKind.PERMISSION_DENIED -> getString(R.string.recording_error_permission)
            AudioRecorder.ErrorKind.MIC_UNAVAILABLE -> getString(R.string.recording_error_mic_unavailable)
            else -> getString(R.string.recording_error_failed)
        }
    }

    /** Dictation capture errors are handled by [DictationSession]; this covers command listening. */
    private fun onRecorderCaptureError(kind: AudioRecorder.ErrorKind) {
        if (kind != AudioRecorder.ErrorKind.CAPTURE_FAILED && kind != AudioRecorder.ErrorKind.WRITE_FAILED) return
        if (isCommandModeActive.value && commandStage.value == CommandStage.LISTENING) {
            cancelCommandMode(showError = true, message = recorderErrorMessage(kind))
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    }

    private fun copyPendingCommit() {
        val text = session.pendingCommit.value ?: return
        copyToClipboard(text)
        session.onPendingCommitDiscard()
        showToast(getString(R.string.dictation_pending_copied))
    }

    private fun handleBlockedExternalSendAction() {
        performFeedback()
    }

    private fun saveSecureFieldDontShowAgain() {
        serviceScope.launch {
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

    private fun onFirstUseDisclosureOpenPrivacySafety() {
        launchMainActivity(PRIVACY_SAFETY_DESTINATION)
        disclosureGate.onOpenSettings()
    }

    private suspend fun resolveCommandRuntimeConfig(): CommandRuntimeConfig? {
        val message = when (val resolution = CommandRuntimeConfig.resolve(configLoader.load(currentInputEditorInfo?.packageName))) {
            is CommandRuntimeConfig.Resolution.Ready -> return resolution.config
            CommandRuntimeConfig.Resolution.MissingStt -> R.string.command_error_setup_stt
            CommandRuntimeConfig.Resolution.MissingText -> R.string.command_error_setup_text
        }
        cancelCommandMode(showError = true, message = getString(message))
        return null
    }

    private fun showSetupRequiredToast() {
        Toast.makeText(this, getString(R.string.toast_setup_required_stt), Toast.LENGTH_SHORT).show()
    }

    // region Dictation intents (thin forwarders to DictationSession)

    private fun onRecordIntent(intent: RecordIntent) {
        when (intent) {
            is RecordIntent.PressStart -> onPressStart(intent.locked)
            RecordIntent.PressRelease -> session.onPressRelease()
            RecordIntent.Lock -> session.onLock()
            RecordIntent.Cancel -> discardRecording()
            RecordIntent.Stop -> onSendAction()
            RecordIntent.Pause -> session.onPause()
            RecordIntent.Resume -> session.onResume()
            RecordIntent.HoldHint -> showLongPressHint.value = true
            // Haptics are played by the button itself.
            is RecordIntent.Haptic -> Unit
        }
    }

    private fun onPressStart(locked: Boolean = false) {
        if (isCommandModeActive.value) return
        if (shouldBlockExternalSend()) {
            handleBlockedExternalSendAction()
            return
        }
        clearUndoQuickAction()
        clearEnhancementUndoState()
        if (dictationDisclosureShown.value == true) {
            session.onPressStart(locked)
            return
        }
        if (dictationDisclosureJob?.isActive == true) return
        dictationDisclosureJob = serviceScope.launch {
            // The finger leaves the button to answer the disclosure, so a confirmed start is hands-free.
            if (confirmDictationDisclosure()) session.onPressStart(locked = true)
        }
    }

    private suspend fun confirmDictationDisclosure(): Boolean {
        if (!appContainer.awaitMigrations(MIGRATIONS_WAIT_MS)) {
            showSetupRequiredToast()
            return false
        }
        val selection = startPlanner.resolveStt(currentInputEditorInfo?.packageName)
        if (selection == null) {
            showSetupRequiredToast()
            return false
        }
        val disclosure = PrivacyDisclosureFormatter.disclosureForDictation(
            provider = selection.provider,
            selectedModelId = selection.modelId,
            useContext = selection.prefs[USE_CONTEXT] ?: false,
        )
        return disclosureGate.await(FirstUseDisclosureGate.Mode.DICTATION_AUDIO, disclosure) ==
            FirstUseDisclosureGate.Decision.CONTINUE
    }

    private fun onSendAction() {
        if (shouldBlockExternalSend()) {
            handleBlockedExternalSendAction()
            return
        }
        clearUndoQuickAction()
        session.onStop()
    }

    private fun onCancelAction() {
        clearUndoQuickAction()
        performFeedback(cue = SoundCues.Cue.PAUSE)
        if (session.isBusy) confirmCancelAction { session.onCancel() }
    }

    private fun discardRecording() {
        soundCues.play(SoundCues.Cue.PAUSE)
        clearUndoQuickAction()
        session.onCancel()
    }

    private fun onUndoAction() {
        performFeedback(isDelete = true)
        if (!textCommitter.undoLastInsertion(lastFocusKey)) {
            showToast(getString(R.string.dictation_undo_unavailable))
        }
        refreshUndoState()
    }

    // endregion

    /** Auto-Learn capture (§4.11 step 1); gates are read when a dictation lands, field text is never logged. */
    private fun createAutoLearnObserver(): AutoLearnObserver {
        val enabled = repository.autoLearnSettings.map { it.enabled }
            .stateIn(serviceScope, SharingStarted.Eagerly, false)
        return AutoLearnObserver(
            scope = serviceScope,
            inputConnection = { currentInputConnection },
            currentFocus = ::currentFocusKey,
            gates = { event ->
                AutoLearnCaptureStateMachine.Gates(
                    enabled = enabled.value,
                    secureField = SecureFieldDetector.detect(currentInputEditorInfo).isSecure,
                    blockedApp = sendPolicyRules.value?.get(event.focus.packageName) == true,
                    autoSend = event.autoSend == true,
                )
            },
            onRevision = appContainer.autoLearnCoordinator::onRevisionCaptured,
        )
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
        autoLearnObserver.onFieldUpdated()
    }

    override fun onUpdateExtractedText(token: Int, text: ExtractedText?) {
        super.onUpdateExtractedText(token, text)
        autoLearnObserver.onFieldUpdated()
    }

    override fun onFinishInput() {
        autoLearnObserver.onFieldLeft()
        super.onFinishInput()
    }

    private fun currentFocusKey(): FocusKey? {
        return FocusKey.from(currentInputEditorInfo, focusIdentity.currentId)
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
            commandUndoJob = serviceScope.launch {
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
        // Any command capture still starting is stale from here on, even when the recorder is left alone.
        commandCaptureGeneration++
        commandAudioFile = null
        // The recorder and clients are shared with dictation; never tear down a dictation run (F-08).
        if (!session.isBusy) {
            cancelCapture()
        }

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
            serviceScope.launch {
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
        if (session.isBusy || dictationDisclosureJob?.isActive == true) {
            return
        }
        if (shouldBlockExternalSend()) {
            return
        }

        resetCommandRunState()
        isCommandModeActive.value = true
        setCommandStage(CommandStage.WAITING, null)
        commandSilenceRetryUsed = false

        commandRunFocusKey = currentFocusKey()
        commandController.startRun(commandRunFocusKey)
        refreshCommandUndoState()

        serviceScope.launch {
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
        serviceScope.launch {
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
        commandSilenceRetryUsed = false
        startCommandListening()
    }

    private fun onCommandClipboardRetry() {
        if (!isCommandModeActive.value) return
        if (commandStage.value != CommandStage.CLIPBOARD_CONFIRM) return
        refreshCommandClipboardPreview()
    }

    private fun startCommandListening() {
        if (!isCommandModeActive.value) return
        commandJob?.cancel()
        commandJob = serviceScope.launch {
            if (!micPermissionGate.check(MicPermissionGate.ACTION_COMMAND, retry = null)) {
                cancelCommandMode(showError = true, message = getString(R.string.command_error_permissions))
                return@launch
            }

            val runtimeConfig = resolveCommandRuntimeConfig() ?: return@launch
            val disclosure = PrivacyDisclosureFormatter.disclosureForCommand(
                sttProvider = runtimeConfig.sttProvider,
                sttModelId = runtimeConfig.sttModelId,
                textProvider = runtimeConfig.textProvider,
                textModelId = runtimeConfig.textModelId,
                useContext = runtimeConfig.useContext,
            )

            val startResult = CommandDisclosureGateCoordinator(::shouldBlockExternalSend).startListening(
                awaitDisclosure = {
                    when (
                        disclosureGate.await(
                            mode = FirstUseDisclosureGate.Mode.COMMAND_TEXT,
                            disclosure = disclosure,
                        )
                    ) {
                        FirstUseDisclosureGate.Decision.CONTINUE -> CommandDisclosureDecision.CONTINUE
                        FirstUseDisclosureGate.Decision.CANCEL -> CommandDisclosureDecision.CANCEL
                        FirstUseDisclosureGate.Decision.OPEN_SETTINGS -> CommandDisclosureDecision.OPEN_SETTINGS
                    }
                },
                startRecording = {
                    commandSpokenInstruction = null
                    if (startCapture().isSuccess) {
                        setCommandStage(CommandStage.LISTENING, null)
                    } else {
                        cancelCommandMode(showError = false)
                    }
                },
            )

            when (startResult) {
                CommandListeningStartResult.STARTED -> Unit
                CommandListeningStartResult.CANCELLED,
                CommandListeningStartResult.OPEN_SETTINGS,
                -> cancelCommandMode(showError = false)
                CommandListeningStartResult.BLOCKED -> cancelCommandMode(showError = true, message = blockedNotice())
            }
        }
    }

    private fun onCommandStopListening() {
        if (!isCommandModeActive.value) return
        if (commandStage.value != CommandStage.LISTENING) return

        commandJob?.cancel()
        commandJob = serviceScope.launch {
            val runtimeConfig = resolveCommandRuntimeConfig() ?: return@launch
            val result = try {
                CommandDisclosureGateCoordinator(::shouldBlockExternalSend).finishListening(
                    stopRecording = {
                        setCommandStage(CommandStage.PROCESSING, null)
                        stopCapture()
                    },
                    transcribeInstruction = { transcribeCommandInstruction(runtimeConfig) },
                    restartListening = {},
                    transform = { instruction ->
                        commandSpokenInstruction = instruction
                        runCommandTransformOrError(
                            spokenInstruction = instruction,
                            runtimeConfig = runtimeConfig,
                        )
                    },
                )
            } catch (e: CommandTranscriptionException) {
                // F-07: a failed transcription is an error, not silence.
                cancelCommandMode(showError = true, message = getString(R.string.command_error_transcription_failed, e.message.orEmpty()))
                return@launch
            }

            when (result) {
                CommandPostRecordingResult.TRANSFORMED -> Unit
                CommandPostRecordingResult.RETRY_LISTENING -> {
                    if (!commandSilenceRetryUsed) {
                        commandSilenceRetryUsed = true
                        startCommandListening()
                    } else {
                        cancelCommandMode(showError = false)
                    }
                }
                CommandPostRecordingResult.BLOCKED -> cancelCommandMode(showError = true, message = blockedNotice())
            }
        }
    }

    private class CommandTranscriptionException(message: String) : Exception(message)

    private suspend fun transcribeCommandInstruction(runtimeConfig: CommandRuntimeConfig): String {
        val provider = runtimeConfig.sttProvider
        val request = TranscriptionRequest(
            provider = provider,
            modelId = runtimeConfig.sttModelId,
            languageCode = runtimeConfig.languageCode,
            prompt = provider.prompt.ifEmpty { runtimeConfig.prefs[PROMPT] ?: "" },
            temperature = provider.temperature,
            timeoutMs = provider.timeout.toLong(),
            mediaType = AUDIO_MEDIA_TYPE_WAV,
            scriptConversion = scriptConversion(runtimeConfig.prefs[POSTPROCESSING]),
        )
        val file = commandAudioFile ?: throw CommandTranscriptionException("No recording")
        commandAudioFile = null
        val result = try {
            transcribe(file, request)
        } finally {
            file.delete()
        }
        return result.getOrElse { throw CommandTranscriptionException(it.message ?: it::class.java.simpleName) }
    }

    private suspend fun runCommandTransformOrError(
        spokenInstruction: String,
        runtimeConfig: CommandRuntimeConfig,
    ) {
        val confirmedText = commandConfirmedText?.trim().orEmpty()
        val snapshot = commandSelectionSnapshot
        val focusKey = commandRunFocusKey
        if (confirmedText.isBlank() || snapshot == null || focusKey == null) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_missing_text))
            return
        }

        val textProvider = runtimeConfig.textProvider
        if (textProvider.endpoint.isBlank()) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_missing_endpoint))
            return
        }
        if (textProvider.authMode == ProviderAuthMode.API_KEY && !providerFactory.credentials(textProvider).hasApiKey) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_missing_api_key))
            return
        }
        val textModel = textProvider.models.find { it.id == runtimeConfig.textModelId }
        val enhancer = textModel?.let { providerFactory.enhancement(textProvider, it) }
            ?.getOrElse { error ->
                cancelCommandMode(showError = true, message = error.toProviderUserMessage(this))
                return
            }
        if (enhancer == null) {
            cancelCommandMode(showError = true, message = getString(R.string.command_error_setup_text))
            return
        }

        val presetInstruction = presetById(commandSelectedPresetId.value)?.promptInstruction
        val promptTemplate = TransformPromptBuilder.buildTemplate(
            basePrompt = runtimeConfig.effective.prompt,
            presetInstruction = presetInstruction,
            spokenInstruction = spokenInstruction,
        )

        val temperature = if (textProvider.temperature > 0) {
            textProvider.temperature
        } else {
            runtimeConfig.prefs[ENHANCEMENT_TEMPERATURE] ?: 0.0f
        }
        val contextPrompt = if (runtimeConfig.useContext) currentInputConnection?.getTextBeforeCursor(500, 0)?.toString() else null

        setCommandStage(CommandStage.PROCESSING, null)

        // Cancelling commandJob cancels the request (G-01).
        val transformed = enhancer.enhance(
            EnhancementRequest(
                systemPrompt = promptTemplate,
                userText = confirmedText,
                contextPrompt = contextPrompt,
                temperature = temperature,
                thinking = ThinkingConfig.from(textProvider, textModel),
                timeoutMs = textProvider.timeout.toLong(),
            )
        ).getOrElse { error ->
            setCommandStage(CommandStage.ERROR, error.toProviderUserMessage(this))
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
        val replaced = ic.replaceAndSelect(start, end, trimmed)

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

        serviceScope.launch {
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
        commandJob = serviceScope.launch {
            val runtimeConfig = resolveCommandRuntimeConfig() ?: return@launch
            runCommandTransformOrError(
                spokenInstruction = instruction,
                runtimeConfig = runtimeConfig,
            )
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

        val restored = ic.replaceAndSelect(start, appliedEnd, original)

        refreshCommandUndoState()
        if (!restored) {
            return
        }
    }

    private fun persistDictationAnalyticsOutcome(outcome: DictationAnalyticsSession.Outcome) {
        serviceScope.launch(Dispatchers.IO) { analyticsRepository.recordOutcome(outcome) }
    }

    private fun CompletionStats.toAnalyticsSample() = AnalyticsModelSample(
        providerId = providerId,
        modelId = sttModelId,
        transcriptionLatencyMs = transcriptionLatencyMs,
        enhancementProviderId = enhancementProviderId,
        enhancementModelId = enhancementModelId,
        enhancementLatencyMs = enhancementLatencyMs,
    )

    private fun confirmCancelAction(onConfirm: () -> Unit) {
        serviceScope.launch {
            if (dataStore.data.first()[CANCEL_CONFIRMATION] == false) {
                onConfirm()
            } else {
                pendingCancelConfirm.value = onConfirm
            }
        }
    }

    private fun performFeedback(isDelete: Boolean = false, cue: SoundCues.Cue? = null) {
        // Cues play synchronously so the start cue precedes capture.
        if (cue != null) soundCues.play(cue)
        serviceScope.launch {
            val prefs = dataStore.data.first()
            if (prefs[HAPTIC_FEEDBACK_ENABLED] ?: true) {
                val effect = if (isDelete) HapticFeedbackConstants.KEYBOARD_RELEASE else HapticFeedbackConstants.KEYBOARD_TAP
                window?.window?.decorView?.performHapticFeedback(effect)
            }
            if (cue == null && (prefs[SOUND_EFFECTS_ENABLED] ?: true)) {
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

    private fun selectLanguage(code: String) {
        serviceScope.launch {
            dataStore.edit { it[LANGUAGE_CODE] = code }
            updateLanguageLabel()
        }
    }

    private fun launchMicPermissionRequest() {
        startActivity(PermissionRequestActivity.intent(this, micPermissionGate.pendingAction))
    }

    private fun launchMainActivity(destination: String? = null) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!destination.isNullOrBlank()) {
            intent.putExtra(EXTRA_SETTINGS_DESTINATION, destination)
        }
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && overlayBack != null) return true
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyboardState.value != KeyboardState.Ready) {
                Log.d(TAG, "Intercepting Back Key Down: ${keyboardState.value}")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val closeOverlay = overlayBack
        if (keyCode == KeyEvent.KEYCODE_BACK && closeOverlay != null) {
            closeOverlay()
            return true
        }
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
        logLifecycle("onWindowShown")
        session.onWindowShown()
        serviceScope.launch {
            updateLanguageLabel()
            secureFieldExplanationDontShowAgain.value = dataStore.data
                .map { it[SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN] ?: false }
                .first()
            refreshExternalSendBlock(currentInputEditorInfo)
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        pendingCancelConfirm.value = null
        logLifecycle("onWindowHidden")
        keyboardModes.onWindowHidden()
        disclosureGate.cancel()
        dictationDisclosureJob?.cancel()
        session.onWindowHidden()
        autoLearnObserver.onFieldLeft()
        if (isCommandModeActive.value) {
            resetCommandRunState()
            isCommandModeActive.value = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logLifecycle("onDestroy")
        disclosureGate.cancel()
        viewLifecycle?.destroy()
        viewLifecycle = null
        savedViewState = null
        session.release()
        autoLearnObserver.cancel()
        resetCommandRunState()
        commandUndoJob?.cancel()
        commandUndoJob = null
        audioRecorder.release()
        audioFocus.release()
        soundCues.release()
        serviceScope.cancel()
    }
}
