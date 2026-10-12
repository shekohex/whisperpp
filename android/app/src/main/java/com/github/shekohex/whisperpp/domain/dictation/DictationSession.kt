package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.audio.AudioFocusController
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.core.audio.SoundCues
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.Transcript
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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
        val postProcess: (String) -> String = { it },
        /** Transcript history (§4.8); null disables it. */
        val history: HistoryRecorder? = null,
    )

    private class Run(val id: Long, val focus: FocusKey) {
        var plan: StartPlan? = null
        var job: Job? = null
        val auxJobs = mutableListOf<Job>()
        var recorderLive = false
        var streaming: StreamingTranscriptionSession? = null
        var pcm: Channel<ByteArray>? = null
        var pcmSender: Job? = null

        // Set on the streaming dispatcher; read after joining [pcmSender].
        @Volatile var streamBroken = false
        var lastPartial: String? = null
        var lastPartialAt: Long? = null
        var composing = false
        var contextPrompt: String? = null
        var durationMs = 0L
        var rawText: String? = null
        var stoppedAt = 0L
        var transcriptionLatencyMs = 0L
        var enhancementProviderId: String? = null
        var enhancementModelId: String? = null
        var enhancementLatencyMs: Long? = null

        /** Kept recording owned by this run until it is handed to history (or discarded in [finish]). */
        var audioPath: String? = null
    }

    private sealed interface Outcome {
        data class Text(val text: String) : Outcome
        data object Empty : Outcome
        data class Failed(val message: String) : Outcome
    }

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
    private var run: Run? = null

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
        val r = Run(++latestRunId, focus)
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

    private suspend fun start(r: Run) {
        val decision = deps.planner(r.focus)
        if (run !== r) return
        val plan = when (decision) {
            is StartDecision.Proceed -> decision.plan
            StartDecision.Abort -> return finish(r, SessionState.Idle)
            // The permission gate owns the retry and restarts through the service once access is granted.
            StartDecision.PermissionRequired -> return finish(r, SessionState.Idle)
        }
        r.plan = plan
        deps.focus.acquire()
        deps.cues(SoundCues.Cue.START)
        val onPcm = plan.streaming?.let { startStreaming(r, it) }
        val result = deps.recorder.start(plan.audioFile, onPcm)
        if (run !== r) {
            // Cancelled while the recorder was opening; never keep a capture nobody owns.
            if (result.isSuccess) deps.recorder.cancel()
            return
        }
        result.onFailure { error ->
            val kind = (error as? AudioRecorder.RecorderException)?.kind
            emit(SessionEvent.RecordingFailed(kind))
            return finish(r, SessionState.Error(kind.toErrorKind(), recoverable = true))
        }
        r.recorderLive = true
        val locked = (_state.value as? SessionState.Starting)?.locked ?: false
        _state.value = SessionState.Recording(r.focus, deps.clock(), locked = locked, paused = false)
    }

    private fun startStreaming(r: Run, streaming: StreamingTranscriptionSession): (ByteArray) -> Unit {
        r.streaming = streaming
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        r.pcm = channel
        r.auxJobs += deps.scope.launch {
            streaming.events.collect { onStreamEvent(r, it) }
        }
        val sender = deps.scope.launch(deps.streamingDispatcher) {
            try {
                streaming.start()
                for (chunk in channel) streaming.sendAudio(chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The WAV is complete regardless; the stop path falls back to batch.
                r.streamBroken = true
                channel.cancel()
            }
        }
        r.pcmSender = sender
        r.auxJobs += sender
        return { chunk -> channel.trySend(chunk) }
    }

    private fun onStreamEvent(r: Run, event: StreamEvent) {
        if (run !== r) return
        when (event) {
            is StreamEvent.Partial -> applyPartial(r, event.text)
            is StreamEvent.Final -> r.lastPartial = event.text.takeIf { it.isNotBlank() } ?: r.lastPartial
            else -> Unit
        }
    }

    private fun applyPartial(r: Run, text: String) {
        if (text.isBlank()) return
        r.lastPartial = text
        val s = _state.value
        if (s !is SessionState.Recording && s !is SessionState.Transcribing) return
        if (currentFocus != r.focus || !deps.sendAllowed()) return
        val now = deps.clock()
        val last = r.lastPartialAt
        if (last != null && now - last < deps.partialThrottleMs) return
        r.lastPartialAt = now
        if (deps.committer.setComposing(r.focus, text)) r.composing = true
        if (s is SessionState.Transcribing) _state.value = s.copy(partial = text)
    }

    private suspend fun transcribeAndCommit(r: Run, plan: StartPlan) {
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

        // F-05: dropped PCM means the stream diverged from the file; ignore every realtime result.
        val streaming = r.streaming?.takeIf { recording.droppedChunks == 0 }
        if (streaming == null) {
            r.lastPartial = null
            r.pcmSender?.cancel()
            r.streaming?.let(::cancelStreaming)
        }

        // A healthy stream is primary; the WAV is kept so batch can always take over (§4.5.1).
        val outcome = try {
            val streamed = streaming?.let { commitStream(r, it) }?.getOrNull()?.text?.takeIf { it.isNotBlank() }
            val result = if (streamed != null) {
                Outcome.Text(streamed)
            } else {
                batchOutcome(transcribe(recording.file, plan.request.copy(contextPrompt = r.contextPrompt)))
            }
            r.transcriptionLatencyMs = (deps.clock() - r.stoppedAt).coerceAtLeast(0L)
            if (result is Outcome.Text) retainAudio(r, recording.file)
            result
        } finally {
            recording.file.delete()
        }

        when (outcome) {
            is Outcome.Failed -> {
                emit(SessionEvent.TranscriptionFailed(outcome.message))
                finish(r, SessionState.Error(SessionState.ErrorKind.TRANSCRIPTION_FAILED, recoverable = true))
            }
            Outcome.Empty -> {
                emit(SessionEvent.TranscriptionEmpty)
                finish(r, SessionState.Idle)
            }
            is Outcome.Text -> commit(r, outcome.text)
        }
    }

    private suspend fun retainAudio(r: Run, file: File) {
        val history = deps.history ?: return
        val path = try {
            history.retainAudio(file)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (run === r) r.audioPath = path else path?.let(history::discardAudio)
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

    /**
     * Waits until every captured chunk was handed to the stream, then commits it. The stream's own
     * final is the only accepted result; partials never stand in for it.
     */
    private suspend fun commitStream(r: Run, streaming: StreamingTranscriptionSession): Result<Transcript> {
        r.pcmSender?.join()
        if (r.streamBroken) return Result.failure(IllegalStateException("stream broken"))
        return try {
            streaming.commit()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun batchOutcome(batch: Result<String>): Outcome {
        val text = batch.getOrNull()
        if (!text.isNullOrBlank()) return Outcome.Text(text)
        return batch.exceptionOrNull()
            ?.let { Outcome.Failed(it.message?.takeIf(String::isNotBlank) ?: it::class.java.simpleName) }
            ?: Outcome.Empty
    }

    private suspend fun commit(r: Run, transcript: String) {
        val processed = deps.postProcess(transcript)
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

        if (currentFocus != r.focus) {
            _pendingCommit.value = raw
            emit(SessionEvent.CommitBlockedByFocus(raw))
            return finish(r, SessionState.Idle)
        }
        if (!deps.sendAllowed()) {
            emit(SessionEvent.CommitFailed(raw, SessionEvent.CommitFailed.Reason.BLOCKED))
            return finish(r, SessionState.Idle)
        }
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

        val finalText = if (segment != null) enhance(r, raw, segment) else raw
        if (run !== r) return
        complete(r, raw, finalText)
        finish(r, SessionState.Idle)
    }

    /** Text reached the field: analytics event plus the history entry, which takes over the kept audio. */
    private fun complete(r: Run, raw: String, finalText: String) {
        val stats = CompletionStats(
            providerId = r.plan?.request?.provider?.id.orEmpty(),
            sttModelId = r.plan?.request?.modelId.orEmpty(),
            languageCode = r.plan?.request?.languageCode?.takeIf { it.isNotBlank() && it != AUTO_LANGUAGE },
            transcriptionLatencyMs = r.transcriptionLatencyMs,
            enhancementProviderId = r.enhancementProviderId,
            enhancementModelId = r.enhancementModelId,
            enhancementLatencyMs = r.enhancementLatencyMs,
        )
        emit(SessionEvent.Completed(raw, finalText, r.durationMs, r.focus, r.id, stats))
        deps.history?.record(
            HistoryRecorder.Entry(
                rawText = raw,
                enhancedText = finalText.takeIf { it != raw },
                stats = stats,
                appPackage = r.focus.packageName,
                durationMs = r.durationMs,
                audioPath = r.audioPath,
            )
        )
        r.audioPath = null
    }

    private suspend fun enhance(r: Run, raw: String, segment: TextCommitter.CapturedSegment): String {
        val enhancer = deps.enhancer ?: return raw
        val request = DictationEnhancer.Request(r.focus, raw, r.contextPrompt)
        if (!enhancer.isEnabled(request)) return raw
        if (run !== r) return raw
        _state.value = SessionState.Enhancing(r.focus, raw)
        val startedAt = deps.clock()
        val result = try {
            enhancer.enhance(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DictationEnhancer.Result.Kept(e.message, isError = true)
        }
        if (run !== r) return raw
        if (result is DictationEnhancer.Result.Enhanced) {
            r.enhancementLatencyMs = (deps.clock() - startedAt).coerceAtLeast(0L)
            r.enhancementProviderId = result.providerId
            r.enhancementModelId = result.modelId
        }
        return when (result) {
            is DictationEnhancer.Result.Kept -> {
                result.notice?.let { emit(SessionEvent.EnhancementNotice(it, result.isError)) }
                raw
            }
            is DictationEnhancer.Result.Enhanced -> {
                if (deps.committer.replaceSegment(segment, result.text, currentFocus)) {
                    emit(SessionEvent.EnhancementApplied)
                    result.text
                } else {
                    emit(SessionEvent.EnhancementKeptRaw(result.label))
                    raw
                }
            }
        }
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
            is SessionState.Enhancing -> complete(r, s.raw, s.raw)
            is SessionState.Recording -> if (recordDiscard) emit(SessionEvent.Discarded(s.elapsedMs(deps.clock())))
            is SessionState.Transcribing,
            is SessionState.Committing,
            -> if (recordDiscard) emit(SessionEvent.Discarded(r.durationMs))
            else -> Unit
        }
        finish(r, SessionState.Cancelled)
    }

    /** The one terminal transition. Idempotent per run; late callbacks of [r] are ignored afterwards (G-06). */
    private fun finish(r: Run, next: SessionState) {
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
        r.streaming?.let(::cancelStreaming)
        // Every terminal path, Idle included; only the field that owns the partial is edited.
        if (r.composing) deps.committer.clearComposing(currentFocus)
        r.plan?.audioFile?.delete()
        r.audioPath?.let { path -> deps.history?.discardAudio(path) }
        r.audioPath = null
        _state.value = next
        if (next == SessionState.Cancelled) _state.value = SessionState.Idle
    }

    private fun cancelStreaming(streaming: StreamingTranscriptionSession) {
        deps.scope.launch {
            try {
                streaming.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Closing is best effort.
            }
        }
    }

    private fun emit(event: SessionEvent) {
        _events.tryEmit(event)
    }

    private fun AudioRecorder.ErrorKind?.toErrorKind(): SessionState.ErrorKind = when (this) {
        AudioRecorder.ErrorKind.PERMISSION_DENIED -> SessionState.ErrorKind.PERMISSION_DENIED
        AudioRecorder.ErrorKind.MIC_UNAVAILABLE -> SessionState.ErrorKind.MIC_UNAVAILABLE
        else -> SessionState.ErrorKind.RECORDING_FAILED
    }

    companion object {
        const val MIN_RECORDING_MS = 500L
        const val PARTIAL_THROTTLE_MS = 150L
        private const val CONTEXT_CHARS = 500
        private const val AUTO_LANGUAGE = "auto"
    }
}
