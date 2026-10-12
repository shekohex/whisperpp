package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.annotation.RawRes
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.core.audio.AmplitudeMeter
import com.github.shekohex.whisperpp.core.audio.AudioFocusController
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.core.audio.SoundCues
import com.github.shekohex.whisperpp.core.audio.SystemAudioFocusGateway
import com.github.shekohex.whisperpp.core.permissions.MicPermission
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.dictation.EnhancementRunner
import com.github.shekohex.whisperpp.dictation.FocusIdentity
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.dictation.AudioRecorderSource
import com.github.shekohex.whisperpp.domain.dictation.DictationSession
import com.github.shekohex.whisperpp.domain.dictation.TextCommitter
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeStyle
import com.github.shekohex.whisperpp.ui.keyboard.KeyboardModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val MIGRATIONS_WAIT_MS = 2_000L

/**
 * The keyboard's object graph: builds every component the IME service forwards platform events to, in
 * dependency order. Holds no behaviour of its own; [ImeController] reacts to the platform.
 */
class ImeComponents(private val host: ImeHost, private val scope: CoroutineScope) {
    private val context: Context = host.context
    private val container = context.appContainer
    private val dataStore = container.settingsDataStore
    private val providerFactory = container.providerFactory
    val repository = container.settingsRepository
    private val dictionaryRepository = container.dictionaryRepository

    val textCommitter = TextCommitter { host.inputConnection }
    val focusIdentity = FocusIdentity()
    val ui = KeyboardUiState()
    val commandState = CommandModeState()
    val micPermissionGate = MicPermissionGate(
        isGranted = { MicPermission.isGranted(context) },
        onMissingChanged = { ui.micPermissionMissing.value = it },
    )
    val sendBlock = ExternalSendBlock(scope, dataStore, container.sendPolicyRepository) { host.editorInfo }
    val autoLearnObserver: AutoLearnObserver = createAutoLearnObserver()
    private val postProcessor = TranscriptPostProcessor.create(scope, dataStore, dictionaryRepository)
    val audioRecorder = AudioRecorder(context, scope, AmplitudeMeter())
    val recordingFiles = RecordingFiles(directory = { context.externalCacheDir ?: context.cacheDir })
    val audioFocus = AudioFocusController(
        SystemAudioFocusGateway(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager),
        repository.pauseMediaWhileRecording.stateIn(scope, SharingStarted.Eagerly, true),
    )
    val soundCues = SoundCues(context, scope, repository.soundEffectsEnabled)
    val feedback = KeyboardFeedback(context, scope, dataStore, soundCues) { host.decorView }
    private val hasCredential: (String) -> Boolean = { !container.secretsStore.getProviderApiKey(it).isNullOrBlank() }
    private val configLoader = RuntimeConfigLoader(dataStore = dataStore, repository = repository, hasCredential = hasCredential)
    val disclosureGate = FirstUseDisclosureGate(context.resources, dataStore, scope)

    /** Keyboard mode chip (§4.10); the explicit choice ends when the window hides or the app changes. */
    val keyboardModes = KeyboardModeController(
        repository = container.modesRepository,
        providers = repository.providers,
        profiles = repository.promptProfiles,
        prefs = dataStore.data,
        hasCredential = hasCredential,
        scope = scope,
    )
    /** The focused editor, as last announced by `onStartInput`. */
    var lastFocusKey: FocusKey? = null

    val undo = KeyboardUndoState(textCommitter) { lastFocusKey }
    val notices = EnhancementNoticeState(scope)
    val language = KeyboardLanguageMenu(context, scope, dataStore)
    private val clipboard = ClipboardAccess(context)
    private val transcriber = SttTranscriber(context, providerFactory)
    private val awaitMigrations: suspend () -> Boolean = { container.awaitMigrations(MIGRATIONS_WAIT_MS) }
    private val startPlanner: DictationStartPlanner = createStartPlanner()
    val session: DictationSession = DictationSession(
        DictationSession.Deps(
            recorder = AudioRecorderSource(audioRecorder),
            focus = audioFocus,
            cues = soundCues::play,
            transcriber = transcriber::transcribe,
            enhancer = createEnhancementStep(),
            committer = textCommitter,
            planner = startPlanner::plan,
            sendAllowed = { !sendBlock.shouldBlock() },
            commandModeActive = { commandState.active },
            clock = SystemClock::elapsedRealtime,
            scope = scope,
            postProcess = postProcessor::process,
            clipboardText = clipboard::readTrimmedText,
            history = container.historyRecorder,
        )
    )
    val router: DictationIntentRouter = DictationIntentRouter(
        DictationIntentRouter.Deps(
            context = context,
            scope = scope,
            dataStore = dataStore,
            session = session,
            textCommitter = textCommitter,
            startPlanner = startPlanner,
            disclosures = disclosureGate,
            ui = ui,
            undo = undo,
            notices = notices,
            feedback = feedback,
            soundCues = soundCues,
            clipboard = clipboard,
            shouldBlockExternalSend = sendBlock::shouldBlock,
            isCommandModeActive = { commandState.active },
            awaitMigrations = awaitMigrations,
            currentPackageName = { host.editorInfo?.packageName },
            currentFocus = { lastFocusKey },
        )
    )
    val commands: CommandModeController = createCommandModeController()
    val sessionEvents = SessionEventHandler(
        SessionEventHandler.Deps(
            context = context,
            ui = ui,
            feedback = feedback,
            clipboard = clipboard,
            undo = undo,
            notices = notices,
            analytics = DictationAnalyticsReporter(scope, container.analyticsRepository),
            autoLearn = autoLearnObserver,
            completion = createCompletionHandler(),
            blockedNotice = ::blockedNotice,
        )
    )

    fun currentFocusKey(): FocusKey? = FocusKey.from(host.editorInfo, focusIdentity.currentId)

    fun blockedNotice(): String = context.getString(sendBlock.noticeRes())

    private fun createStartPlanner(): DictationStartPlanner = DictationStartPlanner(
        DictationStartPlanner.Deps(
            config = configLoader,
            providers = providerFactory,
            awaitMigrations = awaitMigrations,
            awaitSendPolicy = { sendBlock.rules.first { it != null } },
            isSendBlocked = sendBlock::shouldBlock,
            checkMicPermission = {
                // The finger is no longer on the button after the permission screen: record hands-free,
                // through the normal start path so policy and disclosure are checked again.
                micPermissionGate.check(MicPermissionGate.ACTION_DICTATION) { router.onPressStart(locked = true) }
            },
            recordingFile = recordingFiles::newFile,
            scriptConversion = transcriber::scriptConversion,
            audioMediaType = SttTranscriber.MEDIA_TYPE_WAV,
            onBlocked = { feedback.perform() },
            onSetupRequired = context::showSetupRequiredToast,
            describeWarnings = { warnings, channel -> runtimeWarningNotice(context, warnings, channel) },
            onNotice = { notices.show(it, EnhancementNoticeStyle.INFO) },
            onStreamingUnsupported = { context.showShortToast(context.getString(R.string.dictation_streaming_unsupported)) },
            vocabulary = { dictionaryRepository.vocabulary.first() },
        )
    )

    private fun createEnhancementStep(): DictationEnhancementStep = DictationEnhancementStep(
        DictationEnhancementStep.Deps(
            context = context,
            config = configLoader,
            enhancementPresetId = repository.enhancementPresetId,
            providers = providerFactory,
            runner = EnhancementRunner(),
            disclosures = disclosureGate,
            blockedNotice = { if (sendBlock.shouldBlock()) blockedNotice() else null },
            onWarning = { notices.show(it, EnhancementNoticeStyle.INFO) },
            onRequestSent = { feedback.perform() },
            defaultPrompt = { readRawResourceOrEmpty(R.raw.enhancement_prompt) },
            systemPreamble = { readRawResourceOrEmpty(R.raw.enhancement_system_preamble) },
            vocabulary = { dictionaryRepository.vocabulary.first() },
        )
    )

    private fun readRawResourceOrEmpty(@RawRes resId: Int): String {
        return runCatching {
            context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    private fun createCommandModeController(): CommandModeController = CommandModeController(
        CommandModeController.Deps(
            context = context,
            scope = scope,
            state = commandState,
            capture = CommandCapture(
                recorder = audioRecorder,
                audioFocus = audioFocus,
                feedback = feedback,
                recordingFile = recordingFiles::newFile,
                isDictationBusy = { session.isBusy },
                onStartFailed = { kind -> context.showShortToast(context.getString(recorderErrorMessageRes(kind))) },
            ),
            clipboard = clipboard,
            transcriber = transcriber,
            config = configLoader,
            providers = providerFactory,
            repository = repository,
            disclosures = disclosureGate,
            micPermission = micPermissionGate,
            inputConnection = { host.inputConnection },
            editorInfo = { host.editorInfo },
            currentFocus = ::currentFocusKey,
            isDictationBusy = { session.isBusy },
            isDictationDisclosureActive = { router.isDisclosureActive },
            shouldBlockExternalSend = sendBlock::shouldBlock,
            blockedNotice = ::blockedNotice,
        )
    )

    private fun createCompletionHandler(): DictationCompletionHandler = DictationCompletionHandler(
        DictationCompletionHandler.Deps(
            scope = scope,
            dataStore = dataStore,
            repository = repository,
            session = session,
            textCommitter = textCommitter,
            autoLearn = autoLearnObserver,
            lastFocus = { lastFocusKey },
            imeOptions = { host.editorInfo?.imeOptions ?: 0 },
            shouldBlockExternalSend = sendBlock::shouldBlock,
            switchIme = host::switchToPreviousIme,
        )
    )

    /** Auto-Learn capture (§4.11 step 1); gates are read when a dictation lands, field text is never logged. */
    private fun createAutoLearnObserver(): AutoLearnObserver {
        val enabled = repository.autoLearnSettings.map { it.enabled }
            .stateIn(scope, SharingStarted.Eagerly, false)
        return AutoLearnObserver(
            scope = scope,
            inputConnection = { host.inputConnection },
            currentFocus = ::currentFocusKey,
            gates = { event ->
                AutoLearnCaptureStateMachine.Gates(
                    enabled = enabled.value,
                    secureField = SecureFieldDetector.detect(host.editorInfo).isSecure,
                    blockedApp = sendBlock.rules.value?.get(event.focus.packageName) == true,
                    autoSend = event.autoSend == true,
                )
            },
            onRevision = container.autoLearnCoordinator::onRevisionCaptured,
        )
    }
}
