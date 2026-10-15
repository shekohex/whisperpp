package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R
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
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_TEMPERATURE
import com.github.shekohex.whisperpp.data.PreferenceKeys.POSTPROCESSING
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPT
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.domain.providers.toProviderUserMessage
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Command mode (§4.10 transform-by-voice): pick the text to transform (selection or clipboard), listen
 * for a spoken instruction, run the transform and replace the text in the field, with a short undo.
 * The visible state lives in [CommandModeState]; recording goes through [CommandCapture].
 */
class CommandModeController(private val deps: Deps) {
    class Deps(
        val context: Context,
        val scope: CoroutineScope,
        val state: CommandModeState,
        val capture: CommandCapture,
        val clipboard: ClipboardAccess,
        val transcriber: SttTranscriber,
        val config: RuntimeConfigLoader,
        val providers: ProviderFactory,
        val repository: SettingsRepository,
        val disclosures: FirstUseDisclosureGate,
        val micPermission: MicPermissionGate,
        val inputConnection: () -> InputConnection?,
        val editorInfo: () -> EditorInfo?,
        val currentFocus: () -> FocusKey?,
        val isDictationBusy: () -> Boolean,
        val isDictationDisclosureActive: () -> Boolean,
        val shouldBlockExternalSend: () -> Boolean,
        val blockedNotice: () -> String,
    )

    private val state = deps.state
    private val undoTracker = CommandUndoTracker(deps.scope, state)
    private val selectionResolver = SelectionResolver()

    private var silenceRetryUsed = false
    private var runFocusKey: FocusKey? = null
    private var selectionSnapshot: SelectionSnapshot? = null
    private var confirmedText: String? = null
    private var originalTextForUndo: String? = null
    private var lastInstruction: String? = null
    private var commandJob: Job? = null

    val isActive: Boolean
        get() = state.active

    private fun string(@StringRes resId: Int): String = deps.context.getString(resId)

    /** Focus moved to another field: a running command cannot apply there any more. */
    fun onFocusChanged() {
        if (state.active) cancelCommandMode(showError = true, message = string(R.string.command_error_focus_changed))
    }

    /** Dictation capture errors are handled by the dictation session; this covers command listening. */
    fun onCaptureError(kind: AudioRecorder.ErrorKind) {
        if (kind != AudioRecorder.ErrorKind.CAPTURE_FAILED && kind != AudioRecorder.ErrorKind.WRITE_FAILED) return
        if (state.active && state.stage == CommandStage.LISTENING) {
            cancelCommandMode(showError = true, message = string(recorderErrorMessageRes(kind)))
        }
    }

    fun onWindowHidden() {
        if (state.active) {
            resetRunState()
            state.active = false
        }
    }

    fun release() {
        resetRunState()
        undoTracker.release()
    }

    fun onEnter() {
        if (deps.isDictationBusy() || deps.isDictationDisclosureActive()) {
            return
        }
        if (deps.shouldBlockExternalSend()) {
            return
        }

        resetRunState()
        state.active = true
        state.setStage(CommandStage.WAITING, null)
        silenceRetryUsed = false

        runFocusKey = deps.currentFocus()
        undoTracker.startRun(runFocusKey)
        undoTracker.refresh()

        deps.scope.launch {
            state.selectedPresetId = deps.repository.commandPresetId.first().orEmpty().ifBlank { TRANSFORM_PRESET_ID_TONE_REWRITE }
        }

        val resolved = selectionResolver.resolve(deps.editorInfo(), deps.inputConnection())
        when (resolved) {
            is ResolvedSelection.Selected -> {
                selectionSnapshot = resolved.snapshot
                confirmedText = resolved.text
                originalTextForUndo = resolved.text
                startListening()
            }

            is ResolvedSelection.NeedsClipboard -> {
                selectionSnapshot = resolved.snapshot
                state.setStage(CommandStage.CLIPBOARD_CONFIRM, null)
                refreshClipboardPreview()
            }

            is ResolvedSelection.None -> {
                selectionSnapshot = cursorSnapshot()
                state.setStage(CommandStage.CLIPBOARD_CONFIRM, null)
                refreshClipboardPreview()
            }
        }
    }

    fun onCancel() {
        cancelCommandMode(showError = false)
    }

    fun onPresetSelected(id: String) {
        val normalized = id.trim()
        if (normalized.isBlank()) return
        state.selectedPresetId = normalized
        deps.scope.launch {
            deps.repository.setCommandPresetId(normalized)
        }
    }

    fun onClipboardContinue() {
        if (!state.active) return
        if (state.stage != CommandStage.CLIPBOARD_CONFIRM) return

        val text = deps.clipboard.readTrimmedText()
        if (text == null) {
            val remaining = state.clipboardAttemptsRemaining - 1
            state.clipboardAttemptsRemaining = remaining
            refreshClipboardPreview()
            if (remaining <= 0) {
                cancelCommandMode(showError = true, message = string(R.string.command_error_clipboard_attempts_exhausted))
            }
            return
        }

        confirmedText = text
        val snapshot = selectionSnapshot
        originalTextForUndo = if (snapshot != null && snapshot.start != snapshot.end) text else ""
        silenceRetryUsed = false
        startListening()
    }

    fun onClipboardRetry() {
        if (!state.active) return
        if (state.stage != CommandStage.CLIPBOARD_CONFIRM) return
        refreshClipboardPreview()
    }

    fun onStopListening() {
        if (!state.active) return
        if (state.stage != CommandStage.LISTENING) return

        commandJob?.cancel()
        commandJob = deps.scope.launch {
            val runtimeConfig = resolveRuntimeConfig() ?: return@launch
            val result = try {
                CommandDisclosureGateCoordinator(deps.shouldBlockExternalSend).finishListening(
                    stopRecording = {
                        state.setStage(CommandStage.PROCESSING, null)
                        deps.capture.stop()
                    },
                    transcribeInstruction = { transcribeInstruction(runtimeConfig) },
                    restartListening = {},
                    transform = { instruction ->
                        lastInstruction = instruction
                        runTransformOrError(
                            spokenInstruction = instruction,
                            runtimeConfig = runtimeConfig,
                        )
                    },
                )
            } catch (e: CommandTranscriptionException) {
                // F-07: a failed transcription is an error, not silence.
                cancelCommandMode(showError = true, message = deps.context.getString(R.string.command_error_transcription_failed, e.message.orEmpty()))
                return@launch
            }

            when (result) {
                CommandPostRecordingResult.TRANSFORMED -> Unit
                CommandPostRecordingResult.RETRY_LISTENING -> {
                    if (!silenceRetryUsed) {
                        silenceRetryUsed = true
                        startListening()
                    } else {
                        cancelCommandMode(showError = false)
                    }
                }
                CommandPostRecordingResult.BLOCKED -> cancelCommandMode(showError = true, message = deps.blockedNotice())
            }
        }
    }

    fun onRetryTransform() {
        if (!state.active) return
        if (state.stage != CommandStage.ERROR) return
        val instruction = lastInstruction ?: return
        commandJob?.cancel()
        commandJob = deps.scope.launch {
            val runtimeConfig = resolveRuntimeConfig() ?: return@launch
            runTransformOrError(
                spokenInstruction = instruction,
                runtimeConfig = runtimeConfig,
            )
        }
    }

    fun onUndo() {
        val entry = undoTracker.consume() ?: run {
            undoTracker.refresh()
            return
        }
        if (deps.currentFocus() != entry.focusKey) {
            undoTracker.refresh()
            return
        }
        val ic = deps.inputConnection() ?: run {
            undoTracker.refresh()
            return
        }

        val start = entry.snapshot.start
        val appliedEnd = start + entry.appliedText.length
        val original = entry.originalText

        ic.replaceAndSelect(start, appliedEnd, original)

        undoTracker.refresh()
    }

    private suspend fun resolveRuntimeConfig(): CommandRuntimeConfig? {
        val message = when (val resolution = CommandRuntimeConfig.resolve(deps.config.load(deps.editorInfo()?.packageName))) {
            is CommandRuntimeConfig.Resolution.Ready -> return resolution.config
            CommandRuntimeConfig.Resolution.MissingStt -> R.string.command_error_setup_stt
            CommandRuntimeConfig.Resolution.MissingText -> R.string.command_error_setup_text
        }
        cancelCommandMode(showError = true, message = string(message))
        return null
    }

    private fun resetRunState() {
        commandJob?.cancel()
        commandJob = null
        deps.capture.reset()

        runFocusKey = null
        selectionSnapshot = null
        confirmedText = null
        originalTextForUndo = null
        lastInstruction = null
        silenceRetryUsed = false

        state.clearClipboard()
        state.clipboardAttemptsRemaining = CommandModeState.CLIPBOARD_ATTEMPTS
    }

    private fun cancelCommandMode(showError: Boolean, message: String? = null) {
        resetRunState()
        state.active = false
        if (showError) {
            state.setStage(CommandStage.ERROR, message)
            deps.scope.launch {
                delay(ERROR_DISPLAY_MS)
                if (!state.active) {
                    state.setStage(CommandStage.WAITING, null)
                }
            }
        } else {
            state.setStage(CommandStage.WAITING, null)
        }
    }

    private fun refreshClipboardPreview() {
        val text = deps.clipboard.readTrimmedText()
        if (text == null) {
            state.clearClipboard(unavailableReason = string(R.string.command_clipboard_unavailable))
            return
        }
        state.showClipboard(CommandClipboardPreview.of(text))
    }

    private fun cursorSnapshot(): SelectionSnapshot? {
        val ic = deps.inputConnection() ?: return null
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        if (start < 0 || end < 0) return null
        val cursor = if (start == end) start else end
        return SelectionSnapshot(start = cursor, end = cursor)
    }

    private fun startListening() {
        if (!state.active) return
        commandJob?.cancel()
        commandJob = deps.scope.launch {
            if (!deps.micPermission.check(MicPermissionGate.ACTION_COMMAND, retry = null)) {
                cancelCommandMode(showError = true, message = string(R.string.command_error_permissions))
                return@launch
            }

            val runtimeConfig = resolveRuntimeConfig() ?: return@launch
            val disclosure = PrivacyDisclosureFormatter.disclosureForCommand(
                sttProvider = runtimeConfig.sttProvider,
                sttModelId = runtimeConfig.sttModelId,
                textProvider = runtimeConfig.textProvider,
                textModelId = runtimeConfig.textModelId,
                useContext = runtimeConfig.useContext,
            )

            val startResult = CommandDisclosureGateCoordinator(deps.shouldBlockExternalSend).startListening(
                awaitDisclosure = {
                    when (
                        deps.disclosures.await(
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
                    lastInstruction = null
                    if (deps.capture.start().isSuccess) {
                        state.setStage(CommandStage.LISTENING, null)
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
                CommandListeningStartResult.BLOCKED -> cancelCommandMode(showError = true, message = deps.blockedNotice())
            }
        }
    }

    private class CommandTranscriptionException(message: String) : Exception(message)

    private suspend fun transcribeInstruction(runtimeConfig: CommandRuntimeConfig): String {
        val provider = runtimeConfig.sttProvider
        val request = TranscriptionRequest(
            provider = provider,
            modelId = runtimeConfig.sttModelId,
            languageCode = runtimeConfig.languageCode,
            prompt = provider.prompt.ifEmpty { runtimeConfig.prefs[PROMPT] ?: "" },
            temperature = provider.temperature,
            timeoutMs = provider.effectiveTimeoutMs(provider.models.find { it.id == runtimeConfig.sttModelId }),
            mediaType = SttTranscriber.MEDIA_TYPE_WAV,
            scriptConversion = deps.transcriber.scriptConversion(runtimeConfig.prefs[POSTPROCESSING]),
        )
        val file = deps.capture.takeAudioFile() ?: throw CommandTranscriptionException("No recording")
        val result = try {
            deps.transcriber.transcribe(file, request)
        } finally {
            file.delete()
        }
        return result.getOrElse { throw CommandTranscriptionException(it.message ?: it::class.java.simpleName) }
    }

    private suspend fun runTransformOrError(
        spokenInstruction: String,
        runtimeConfig: CommandRuntimeConfig,
    ) {
        val text = confirmedText?.trim().orEmpty()
        val snapshot = selectionSnapshot
        val focusKey = runFocusKey
        if (text.isBlank() || snapshot == null || focusKey == null) {
            cancelCommandMode(showError = true, message = string(R.string.command_error_missing_text))
            return
        }

        val textProvider = runtimeConfig.textProvider
        if (textProvider.endpoint.isBlank()) {
            cancelCommandMode(showError = true, message = string(R.string.command_error_missing_endpoint))
            return
        }
        if (textProvider.authMode == ProviderAuthMode.API_KEY && !deps.providers.credentials(textProvider).hasApiKey) {
            cancelCommandMode(showError = true, message = string(R.string.command_error_missing_api_key))
            return
        }
        val textModel = textProvider.models.find { it.id == runtimeConfig.textModelId }
        val enhancer = textModel?.let { deps.providers.enhancement(textProvider, it) }
            ?.getOrElse { error ->
                cancelCommandMode(showError = true, message = error.toProviderUserMessage(deps.context))
                return
            }
        if (enhancer == null) {
            cancelCommandMode(showError = true, message = string(R.string.command_error_setup_text))
            return
        }

        val presetInstruction = presetById(state.selectedPresetId)?.promptInstruction
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
        val contextPrompt = if (runtimeConfig.useContext) deps.inputConnection()?.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString() else null

        state.setStage(CommandStage.PROCESSING, null)

        // Cancelling commandJob cancels the request (G-01).
        val transformed = enhancer.enhance(
            EnhancementRequest(
                systemPrompt = promptTemplate,
                userText = text,
                contextPrompt = contextPrompt,
                temperature = temperature,
                thinking = ThinkingConfig.from(textProvider, textModel),
                timeoutMs = textProvider.effectiveTimeoutMs(textModel),
            )
        ).getOrElse { error ->
            state.setStage(CommandStage.ERROR, error.toProviderUserMessage(deps.context))
            return
        }

        if (!state.active) {
            return
        }

        applyResultOrError(
            focusKey = focusKey,
            snapshot = snapshot,
            originalText = originalTextForUndo.orEmpty(),
            appliedText = transformed,
        )
    }

    private fun applyResultOrError(
        focusKey: FocusKey,
        snapshot: SelectionSnapshot,
        originalText: String,
        appliedText: String,
    ) {
        if (deps.currentFocus() != focusKey) {
            cancelCommandMode(showError = true, message = string(R.string.command_error_focus_changed))
            return
        }

        val ic = deps.inputConnection()
        if (ic == null) {
            cancelCommandMode(showError = true, message = string(R.string.command_error_no_input_connection))
            return
        }

        val trimmed = appliedText.trimEnd()
        if (trimmed.isBlank()) {
            state.setStage(CommandStage.ERROR, string(R.string.command_error_empty_result))
            return
        }

        val start = snapshot.start
        val end = snapshot.end
        val replaced = ic.replaceAndSelect(start, end, trimmed)

        if (!replaced) {
            state.setStage(CommandStage.ERROR, string(R.string.command_error_apply_failed))
            return
        }

        undoTracker.recordApplied(focusKey, snapshot, originalText, trimmed)
        undoTracker.refresh()
        state.setStage(CommandStage.DONE, null)

        deps.scope.launch {
            delay(DONE_DISPLAY_MS)
            if (state.stage == CommandStage.DONE) {
                state.active = false
                state.setStage(CommandStage.WAITING, null)
            }
        }
    }

    private companion object {
        const val ERROR_DISPLAY_MS = 1600L
        const val DONE_DISPLAY_MS = 1200L
        const val CONTEXT_CHARS = 500
    }
}
