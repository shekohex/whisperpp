package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.audio.AudioFocusController
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.core.audio.SoundCues
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.modes.OutputMode
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single owner of a dictation run (§4.2): record → transcribe → commit → enhance. Driven by
 * intents from the IME, recorder/focus events and provider results. Every terminal path goes
 * through [finish], which releases the recorder, audio focus, in-flight work, the streaming
 * session and the temp file exactly once. Call intents from the scope's (main) thread.
 */
class DictationSession(private val deps: Deps) {
    data class Deps(
        val recorder: DictationRecorder,
        val focus: AudioFocusController,
        val cues: (SoundCues.Cue) -> Unit,
        val transcriber: suspend (File, TranscriptionRequest) -> Result<String>,
        val enhancer: DictationEnhancer?,
        val committer: TextCommitter,
        /** Platform pre-start checks; must not wait on user interaction (a release cancels it). */
        val planner: suspend (FocusKey) -> StartDecision,
        /** False while the current editor forbids sending (secure field / per-app policy). */
        val sendAllowed: () -> Boolean,
        /** Command mode owns the mic while true (F-08). */
        val commandModeActive: () -> Boolean,
        val clock: () -> Long,
        val scope: CoroutineScope,
        val streamingDispatcher: CoroutineDispatcher = Dispatchers.Default,
        val minRecordingMs: Long = MIN_RECORDING_MS,
        val partialThrottleMs: Long = PARTIAL_THROTTLE_MS,
        /** Transcript clean-up (filter, word replacements) applied once, before commit and enhancement. */
        val postProcess: (String, DictationOptions) -> String = { text, _ -> text },
        /** Clipboard text for modes that add it to the enhancement context. */
        val clipboardText: () -> String? = { null },
        /** Transcript history (§4.8); null disables it. */
        val history: HistoryRecorder? = null,
    )

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private val _pendingCommit = MutableStateFlow<String?>(null)

    /**
     * Transcript held back because focus moved away from its field (G-07) or the editor rejected the
     * insert. Kept until Insert/Copy/Discard; a new run cannot start while it is set (no silent loss).
     */
    val pendingCommit: StateFlow<String?> = _pendingCommit.asStateFlow()

    val isBusy: Boolean
        get() = when (_state.value) {
            SessionState.Idle, SessionState.Cancelled, is SessionState.Error -> false
            else -> true
        }

    private val windowVisible = MutableStateFlow(true)
    private var currentFocus: FocusKey? = null
    private var run: DictationRun? = null

    private val streamingPipeline = RealtimeStreaming(deps, _state, { currentFocus }, { run === it })
    private val enhancement = EnhancementStep(deps, _state, { currentFocus }, { run === it }, ::emit)
    private val completion = CompletionReporter(deps, { run === it }, ::emit)

    /** Id of the most recently started run; a [SessionEvent.Completed] with an older id is stale. */
    var latestRunId: Long = 0L
        private set

    init {
        deps.scope.launch { deps.recorder.errors.collect { onRecorderError(it) } }
        deps.scope.launch { deps.focus.focusLoss.collect { onAudioFocusLost() } }
    }

    // region Intents

    fun onPressStart(locked: Boolean = false) {
        if (deps.commandModeActive() || isBusy) return
        val focus = currentFocus
        if (focus == null) {
            emit(SessionEvent.Notice(R.string.dictation_no_field))
            return
        }
        if (!deps.sendAllowed()) {
            emit(SessionEvent.SendBlocked)
            return
        }
        if (_pendingCommit.value != null) {
            emit(SessionEvent.Notice(R.string.dictation_pending_commit_blocks_start))
            return
        }
        val r = DictationRun(++latestRunId, focus)
        run = r
        _state.value = SessionState.Starting(focus, locked)
        r.job = deps.scope.launch { start(r) }
    }

    /** End of a hold: cancels a pending start (B-01), discards a too-short take, otherwise sends. */
    fun onPressRelease() {
        when (val s = _state.value) {
            is SessionState.Starting -> if (!s.locked) cancelRun(recordDiscard = false)
            is SessionState.Recording -> {
                if (s.locked || s.paused) return
                if (s.elapsedMs(deps.clock()) < deps.minRecordingMs) {
                    emit(SessionEvent.ShortPressHint)
                    cancelRun(recordDiscard = false)
                } else {
                    onStop()
                }
            }
            else -> Unit
        }
    }

    fun onLock() {
        when (val s = _state.value) {
            is SessionState.Starting -> _state.value = s.copy(locked = true)
            is SessionState.Recording -> if (!s.locked) _state.value = s.copy(locked = true)
            else -> Unit
        }
    }

    fun onPause() {
        val s = _state.value as? SessionState.Recording ?: return
        if (s.paused) return
        deps.cues(SoundCues.Cue.PAUSE)
        pause(s)
    }

    fun onResume() {
        val s = _state.value as? SessionState.Recording ?: return
        if (!s.paused) return
        deps.focus.acquire()
        deps.cues(SoundCues.Cue.START)
        _state.value = s.copy(paused = false, startedAt = deps.clock())
        deps.scope.launch { deps.recorder.resume() }
    }

    /** Send: stop recording, transcribe, then commit to the start focus. */
    fun onStop() {
        val r = run ?: return
        val s = _state.value as? SessionState.Recording ?: return
        val plan = r.plan ?: return
        if (!deps.sendAllowed()) {
            emit(SessionEvent.SendBlocked)
            cancelRun(recordDiscard = true)
            return
        }
        r.durationMs = s.elapsedMs(deps.clock())
        r.stoppedAt = deps.clock()
        // Read now, before stopping the recorder suspends: later the editor may be another field's.
        r.contextPrompt = if (plan.request.useContext && currentFocus == r.focus) {
            deps.committer.textBeforeCursor(CONTEXT_CHARS)
        } else {
            null
        }
        r.enhancementContext = enhancement.context(r, plan.options)
        _state.value = SessionState.Transcribing(r.focus, plan.audioFile, r.lastPartial)
        r.job = deps.scope.launch { transcribeAndCommit(r, plan) }
    }

    fun onCancel() = cancelRun(recordDiscard = true)

    fun onFocusChanged(focus: FocusKey?) {
        currentFocus = focus
        val r = run ?: return
        if (focus == r.focus) return
        when (val s = _state.value) {
            is SessionState.Starting -> cancelRun(recordDiscard = false)
            is SessionState.Recording -> if (!s.paused) pause(s)
            // Transcribing/enhancing keep going; the commit checks the focus (G-07).
            else -> Unit
        }
    }

    /** Recording pauses and lets media resume; network work continues and commits on return (A-05). */
    fun onWindowHidden() {
        windowVisible.value = false
        when (val s = _state.value) {
            is SessionState.Starting -> cancelRun(recordDiscard = false)
            is SessionState.Recording -> {
                if (!s.paused) {
                    pause(s)
                    emit(SessionEvent.Notice(R.string.dictation_paused))
                }
                deps.focus.release()
            }
            else -> Unit
        }
    }

    fun onWindowShown() {
        windowVisible.value = true
    }

    /** The current editor just became blocked for sending; stop anything that would send from it. */
    fun onExternalSendBlocked() {
        val r = run ?: return
        if (currentFocus != r.focus) return
        when (val s = _state.value) {
            is SessionState.Starting -> cancelRun(recordDiscard = false)
            is SessionState.Recording -> {
                if (!s.paused) pause(s)
                emit(SessionEvent.SendBlocked)
            }
            is SessionState.Transcribing,
            is SessionState.Enhancing,
            is SessionState.Committing,
            -> {
                emit(SessionEvent.SendBlocked)
                cancelRun(recordDiscard = true)
            }
            else -> Unit
        }
    }

    /** "Insert here" for a transcript held by [pendingCommit]. */
    fun onPendingCommitInsert() {
        val text = _pendingCommit.value ?: return
        val focus = currentFocus ?: return
        if (isBusy) return
        if (!deps.sendAllowed()) {
            emit(SessionEvent.SendBlocked)
            return
        }
        _pendingCommit.value = null
        when (deps.committer.commit(focus, text)) {
            TextCommitter.CommitResult.NoField -> emit(SessionEvent.CommitFailed(text, SessionEvent.CommitFailed.Reason.NO_FIELD))
            is TextCommitter.CommitResult.Failed -> {
                _pendingCommit.value = text
                emit(SessionEvent.CommitFailed(text, SessionEvent.CommitFailed.Reason.INSERT_FAILED))
            }
            is TextCommitter.CommitResult.Inserted -> emit(SessionEvent.Committed(text))
        }
    }

    fun onPendingCommitDiscard() {
        _pendingCommit.value = null
    }

    /** Service teardown: ends any run and releases its resources. */
    fun release() {
        run?.let { finish(it, SessionState.Idle) }
    }

    // endregion

    private suspend fun start(r: DictationRun) {
        val decision = deps.planner(r.focus)
        if (run !== r) return
        val plan = when (decision) {
            is StartDecision.Proceed -> decision.plan
            StartDecision.Abort -> return finish(r, SessionState.Idle)
            // The permission gate owns the retry and restarts through the service once access is granted.
            StartDecision.PermissionRequired -> return finish(r, SessionState.Idle)
        }
        r.plan = plan
        if (plan.options.outputMode == OutputMode.REPLACE_SELECTION && currentFocus == r.focus) {
            r.replaceRange = deps.committer.selectedRange()
        }
        deps.focus.acquire()
        deps.cues(SoundCues.Cue.START)
        val onPcm = plan.streaming?.let { streamingPipeline.start(r, it) }
        val result = deps.recorder.start(plan.audioFile, onPcm)
        if (run !== r) {
            // Cancelled while the recorder was opening; never keep a capture nobody owns.
            if (result.isSuccess) deps.recorder.cancel()
            return
        }
        result.onFailure { error ->
            val kind = (error as? AudioRecorder.RecorderException)?.kind
            emit(SessionEvent.RecordingFailed(kind))
            return finish(r, SessionState.Error(kind.toSessionErrorKind(), recoverable = true))
        }
        r.recorderLive = true
        val locked = (_state.value as? SessionState.Starting)?.locked ?: false
        _state.value = SessionState.Recording(r.focus, deps.clock(), locked = locked, paused = false)
    }

    private suspend fun transcribeAndCommit(r: DictationRun, plan: StartPlan) {
        val stopped = deps.recorder.stop()
        r.recorderLive = false
        deps.focus.release()
        val recording = stopped.getOrElse { error ->
            emit(SessionEvent.RecordingFailed((error as? AudioRecorder.RecorderException)?.kind))
            return finish(r, SessionState.Error(SessionState.ErrorKind.RECORDING_FAILED, recoverable = true))
        }
        deps.cues(SoundCues.Cue.STOP)
        // No more chunks; the sender drains what is queued before the stream is committed.
        r.pcm?.close()
        // The editor may have become blocked while the recorder was stopping; nothing leaves the device then.
        if (!deps.sendAllowed()) {
            emit(SessionEvent.SendBlocked)
            return cancelRun(recordDiscard = true)
        }

        val streaming = streamingPipeline.healthyStreamOrDiscard(r, recording.droppedChunks)

        // A healthy stream is primary; the WAV is kept so batch can always take over (§4.5.1).
        val outcome = try {
            val streamed = streaming?.let { streamingPipeline.committedText(r, it) }
            val result = if (streamed != null) {
                TranscriptionOutcome.Text(streamed)
            } else {
                val request = plan.request.copy(contextPrompt = r.contextPrompt)
                TranscriptionOutcome.fromBatch(transcribe(recording.file, request))
            }
            r.transcriptionLatencyMs = (deps.clock() - r.stoppedAt).coerceAtLeast(0L)
            if (result is TranscriptionOutcome.Text) completion.retainAudio(r, recording.file)
            result
        } finally {
            recording.file.delete()
        }

        when (outcome) {
            is TranscriptionOutcome.Failed -> {
                emit(SessionEvent.TranscriptionFailed(outcome.message))
                finish(r, SessionState.Error(SessionState.ErrorKind.TRANSCRIPTION_FAILED, recoverable = true))
            }
            TranscriptionOutcome.Empty -> {
                emit(SessionEvent.TranscriptionEmpty)
                finish(r, SessionState.Idle)
            }
            is TranscriptionOutcome.Text -> commit(r, outcome.text)
        }
    }

    private suspend fun transcribe(file: File, request: TranscriptionRequest): Result<String> {
        return try {
            deps.transcriber(file, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun commit(r: DictationRun, transcript: String) {
        val processed = deps.postProcess(transcript, r.options)
        if (processed.isBlank()) {
            // Filtering removed everything: nothing to insert, enhance or send.
            emit(SessionEvent.TranscriptionEmpty)
            return finish(r, SessionState.Idle)
        }
        // Applied once here, for streamed and batch transcripts alike.
        val addSpace = r.plan?.request?.addTrailingSpace == true && !processed.endsWith(' ')
        val raw = if (addSpace) "$processed " else processed
        _state.value = SessionState.Committing(r.focus, raw)
        deps.cues(SoundCues.Cue.DONE)
        // A hidden keyboard holds the commit until the user is back (A-05).
        windowVisible.first { it }
        if (run !== r) return
        if (r.options.outputMode == OutputMode.CLIPBOARD) return deliverToClipboard(r, raw)

        if (currentFocus != r.focus) {
            _pendingCommit.value = raw
            emit(SessionEvent.CommitBlockedByFocus(raw))
            return finish(r, SessionState.Idle)
        }
        if (!deps.sendAllowed()) {
            emit(SessionEvent.CommitFailed(raw, SessionEvent.CommitFailed.Reason.BLOCKED))
            return finish(r, SessionState.Idle)
        }
        r.replaceRange?.let(deps.committer::select)
        val result = deps.committer.commit(r.focus, raw)
        r.composing = false
        val segment = when (result) {
            TextCommitter.CommitResult.NoField -> {
                emit(SessionEvent.CommitFailed(raw, SessionEvent.CommitFailed.Reason.NO_FIELD))
                return finish(r, SessionState.Idle)
            }
            is TextCommitter.CommitResult.Failed -> {
                // Not in the field: keep it for Insert here / Copy instead of reporting success.
                _pendingCommit.value = raw
                emit(SessionEvent.CommitFailed(raw, SessionEvent.CommitFailed.Reason.INSERT_FAILED))
                return finish(r, SessionState.Idle)
            }
            is TextCommitter.CommitResult.Inserted -> result.segment
        }
        r.rawText = raw
        emit(SessionEvent.Committed(raw))

        val finalText = if (segment != null) enhancement.enhance(r, raw, segment) else raw
        if (run !== r) return
        completion.complete(r, raw, finalText)
        finish(r, SessionState.Idle)
    }

    /** Output mode CLIPBOARD: enhance without touching the field, then hand the text to the clipboard. */
    private suspend fun deliverToClipboard(r: DictationRun, raw: String) {
        r.rawText = raw
        val finalText = enhancement.forClipboard(r, raw)
        if (run !== r) return
        emit(SessionEvent.CopyToClipboard(finalText))
        completion.complete(r, raw, finalText)
        finish(r, SessionState.Idle)
    }

    private fun pause(s: SessionState.Recording) {
        _state.value = s.copy(paused = true, accumulatedMs = s.elapsedMs(deps.clock()))
        deps.scope.launch { deps.recorder.pause() }
    }

    private fun onAudioFocusLost() {
        val s = _state.value as? SessionState.Recording ?: return
        if (s.paused) return
        pause(s)
        emit(SessionEvent.Notice(R.string.recording_interrupted))
    }

    private fun onRecorderError(event: AudioRecorder.Event.Error) {
        if (event.kind != AudioRecorder.ErrorKind.CAPTURE_FAILED && event.kind != AudioRecorder.ErrorKind.WRITE_FAILED) return
        val r = run ?: return
        val s = _state.value as? SessionState.Recording ?: return
        if (!r.recorderLive) return
        emit(SessionEvent.RecordingFailed(event.kind))
        emit(SessionEvent.Discarded(s.elapsedMs(deps.clock())))
        finish(r, SessionState.Error(SessionState.ErrorKind.RECORDING_FAILED, recoverable = true))
    }

    private fun cancelRun(recordDiscard: Boolean) {
        val r = run ?: return
        when (val s = _state.value) {
            // The raw text is already in the field; only the enhancement is abandoned.
            is SessionState.Enhancing -> {
                if (r.options.outputMode == OutputMode.CLIPBOARD) emit(SessionEvent.CopyToClipboard(s.raw))
                completion.complete(r, s.raw, s.raw)
            }
            is SessionState.Recording -> if (recordDiscard) emit(SessionEvent.Discarded(s.elapsedMs(deps.clock())))
            is SessionState.Transcribing,
            is SessionState.Committing,
            -> if (recordDiscard) emit(SessionEvent.Discarded(r.durationMs))
            else -> Unit
        }
        finish(r, SessionState.Cancelled)
    }

    /** The one terminal transition. Idempotent per run; late callbacks of [r] are ignored afterwards (G-06). */
    private fun finish(r: DictationRun, next: SessionState) {
        if (run !== r) return
        run = null
        val wasStarting = _state.value is SessionState.Starting
        r.job?.cancel()
        r.auxJobs.forEach { it.cancel() }
        r.pcm?.close()
        if (r.recorderLive) {
            r.recorderLive = false
            deps.recorder.cancel()
        } else if (wasStarting && r.plan != null) {
            // Aborts a recorder start that is still opening the device.
            deps.recorder.cancel()
        }
        deps.focus.release()
        r.streaming?.let(streamingPipeline::cancel)
        // Every terminal path, Idle included; only the field that owns the partial is edited.
        if (r.composing) deps.committer.clearComposing(currentFocus)
        r.plan?.audioFile?.delete()
        r.audioPath?.let { path -> deps.history?.discardAudio(path) }
        r.audioPath = null
        _state.value = next
        if (next == SessionState.Cancelled) _state.value = SessionState.Idle
    }

    private fun emit(event: SessionEvent) {
        _events.tryEmit(event)
    }

    companion object {
        const val MIN_RECORDING_MS = 500L
        const val PARTIAL_THROTTLE_MS = 150L
        private const val CONTEXT_CHARS = 500
    }
}
