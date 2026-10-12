package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.core.audio.AudioFocusController
import com.github.shekohex.whisperpp.core.audio.AudioFocusGateway
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.dictation.FakeInputConnection
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.Transcript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionTest {

    private class FakeGateway : AudioFocusGateway {
        var requests = 0
        var abandons = 0
        override fun request(onFocusChange: (Int) -> Unit): Boolean {
            requests += 1
            return true
        }

        override fun abandon() {
            abandons += 1
        }
    }

    private class FakeRecorder : DictationRecorder {
        override val errors = MutableSharedFlow<AudioRecorder.Event.Error>(extraBufferCapacity = 4)
        var startGate: CompletableDeferred<Unit>? = null
        var startFailure: AudioRecorder.ErrorKind? = null
        var droppedChunks = 0
        var starts = 0
        var stops = 0
        var cancels = 0
        var pauses = 0
        var file: File? = null
        private var pendingStart = false
        private var cancelledDuringStart = false

        val releases: Int
            get() = stops + cancels

        val startedFiles = mutableListOf<File>()

        override suspend fun start(file: File, onPcm: ((ByteArray) -> Unit)?): Result<Unit> {
            starts += 1
            startedFiles += file
            this.file = file
            pendingStart = true
            cancelledDuringStart = false
            startGate?.await()
            pendingStart = false
            if (cancelledDuringStart) {
                return Result.failure(AudioRecorder.RecorderException(AudioRecorder.ErrorKind.CANCELLED, "cancelled"))
            }
            startFailure?.let { return Result.failure(AudioRecorder.RecorderException(it, "failed")) }
            return Result.success(Unit)
        }

        override suspend fun stop(): Result<AudioRecorder.Event.Stopped> {
            stops += 1
            return Result.success(AudioRecorder.Event.Stopped(file!!, durationMs = 1_000L, droppedChunks = droppedChunks))
        }

        override suspend fun pause() {
            pauses += 1
        }

        override suspend fun resume() = Unit

        override fun cancel() {
            if (pendingStart) cancelledDuringStart = true
            cancels += 1
        }
    }

    private class FakeStreaming(private val committed: String?) : StreamingTranscriptionSession {
        override val events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)
        var commits = 0
        var cancels = 0

        override suspend fun start() = Unit

        override suspend fun sendAudio(pcm16: ByteArray) = Unit

        override suspend fun commit(): Result<Transcript> {
            commits += 1
            return committed?.let { Result.success(Transcript(it)) } ?: Result.failure(IllegalStateException("none"))
        }

        override suspend fun cancel() {
            cancels += 1
        }
    }

    private inner class Harness(val test: TestScope) {
        val focus = FocusKey("com.example", inputType = 1, fieldId = null, focusInstanceId = 1L)
        val otherFocus = FocusKey("com.other", inputType = 1, fieldId = null, focusInstanceId = 2L)
        val gateway = FakeGateway()
        val recorder = FakeRecorder()
        val ic = FakeInputConnection().apply { setText("prefix ") }
        val audioFile: File = File.createTempFile("dictation", ".wav").apply { writeText("pcm") }
        val events = mutableListOf<SessionEvent>()
        var plannerGate: CompletableDeferred<Unit>? = null
        var plannerCalls = 0
        var transcript: suspend () -> Result<String> = { Result.success("hello") }
        var transcriberCancelled = false
        var streaming: StreamingTranscriptionSession? = null
        var commandMode = false
        var sendAllowed = true
        var enhancer: DictationEnhancer? = null
        var useContext = false
        val committer = TextCommitter { ic }
        var nextAudioFile: (() -> File)? = null
        val requests = mutableListOf<TranscriptionRequest>()

        private val dispatcher = StandardTestDispatcher(test.testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val session: DictationSession by lazy {
            DictationSession(
                DictationSession.Deps(
                    recorder = recorder,
                    focus = AudioFocusController(gateway, MutableStateFlow(true)),
                    cues = {},
                    transcriber = { _, request ->
                        requests += request
                        try {
                            transcript()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            transcriberCancelled = true
                            throw e
                        }
                    },
                    enhancer = enhancer,
                    committer = committer,
                    planner = {
                        plannerCalls += 1
                        plannerGate?.await()
                        val file = nextAudioFile?.invoke() ?: audioFile
                        StartDecision.Proceed(StartPlan(file, request().copy(useContext = useContext), streaming))
                    },
                    sendAllowed = { sendAllowed },
                    commandModeActive = { commandMode },
                    clock = { test.testScheduler.currentTime },
                    scope = scope,
                    streamingDispatcher = dispatcher,
                ),
            ).also { session ->
                session.onFocusChanged(focus)
                scope.launch { session.events.collect { events += it } }
                test.runCurrent()
            }
        }

        fun recordFor(ms: Long, locked: Boolean = false) {
            session.onPressStart(locked)
            test.runCurrent()
            test.advanceTimeBy(ms)
            test.runCurrent()
        }

        fun dispose() {
            scope.cancel()
            audioFile.delete()
        }
    }

    private fun request() = TranscriptionRequest(
        provider = ServiceProvider(id = "p", name = "P", type = ProviderType.OPENAI, endpoint = "https://example.com", models = emptyList()),
        modelId = "whisper-1",
        languageCode = "auto",
        mediaType = "audio/wav",
        postprocessing = "",
        addTrailingSpace = false,
        timeoutMs = 1_000,
        staticPrompt = "",
        temperature = 0f,
        useContext = false,
    )

    private fun sessionTest(block: suspend TestScope.(Harness) -> Unit) = runTest {
        val harness = Harness(this)
        try {
            block(harness)
        } finally {
            harness.dispose()
        }
    }

    @Test
    fun releaseBeforePlannerCompletes_endsIdle_withoutRecording() = sessionTest { h ->
        h.plannerGate = CompletableDeferred()
        h.session.onPressStart()
        runCurrent()
        assertTrue(h.session.state.value is SessionState.Starting)

        h.session.onPressRelease()
        h.plannerGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals(0, h.recorder.starts)
        assertEquals(0, h.gateway.requests)
    }

    @Test
    fun releaseWhileRecorderStarting_cancelsStart_andReleasesFocusOnce() = sessionTest { h ->
        h.recorder.startGate = CompletableDeferred()
        h.session.onPressStart()
        runCurrent()
        assertEquals(1, h.recorder.starts)

        h.session.onPressRelease()
        h.recorder.startGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals(1, h.recorder.cancels)
        assertEquals(1, h.gateway.abandons)
        assertFalse(h.audioFile.exists())
    }

    @Test
    fun secondPressWhileStarting_isSwallowed() = sessionTest { h ->
        h.plannerGate = CompletableDeferred()
        h.session.onPressStart()
        h.session.onPressStart()
        runCurrent()
        h.plannerGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, h.plannerCalls)
        assertEquals(1, h.recorder.starts)
    }

    @Test
    fun holdAndRelease_recordsTranscribesAndCommits() = sessionTest { h ->
        val states = mutableListOf<SessionState>()
        val session = h.session
        h.scope.launch { session.state.collect { states += it } }
        h.recordFor(1_000)
        assertEquals(SessionState.Recording(h.focus, startedAt = 0L, locked = false, paused = false), h.session.state.value)

        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(states.any { it is SessionState.Transcribing })
        assertEquals("prefix hello", h.ic.getText())
        assertTrue(h.events.contains(SessionEvent.Committed("hello")))
        assertTrue(h.events.contains(SessionEvent.Completed("hello", "hello", 1_000L)))
        assertFalse(h.audioFile.exists())
        assertEquals(1, h.recorder.stops)
        assertEquals(0, h.recorder.cancels)
        assertEquals(1, h.gateway.abandons)
    }

    @Test
    fun shortRelease_discardsWithHint() = sessionTest { h ->
        h.recordFor(200)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(h.events.contains(SessionEvent.ShortPressHint))
        assertEquals(0, h.recorder.stops)
        assertEquals(1, h.recorder.cancels)
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun cancelDuringTranscribing_doesNotCommit_deletesFile_releasesFocus() = sessionTest { h ->
        val gate = CompletableDeferred<Result<String>>()
        h.transcript = { gate.await() }
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()
        assertTrue(h.session.state.value is SessionState.Transcribing)

        h.session.onCancel()
        gate.complete(Result.success("late"))
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(h.transcriberCancelled)
        assertEquals("prefix ", h.ic.getText())
        assertFalse(h.audioFile.exists())
        assertEquals(1, h.gateway.abandons)
        assertTrue(h.events.contains(SessionEvent.Discarded(1_000L)))
        assertFalse(h.events.any { it is SessionEvent.Committed })
    }

    @Test
    fun windowHiddenWhileRecording_pausesAndReleasesFocus() = sessionTest { h ->
        h.recordFor(1_000, locked = true)
        h.session.onWindowHidden()
        runCurrent()

        val state = h.session.state.value as SessionState.Recording
        assertTrue(state.paused)
        assertEquals(1_000L, state.accumulatedMs)
        assertEquals(1, h.recorder.pauses)
        assertEquals(1, h.gateway.abandons)
        assertEquals(0, h.recorder.cancels)
    }

    @Test
    fun windowHiddenWhileTranscribing_commitsWhenShownAgain() = sessionTest { h ->
        val gate = CompletableDeferred<Result<String>>()
        h.transcript = { gate.await() }
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()

        h.session.onWindowHidden()
        gate.complete(Result.success("hello"))
        advanceUntilIdle()
        assertTrue(h.session.state.value is SessionState.Committing)
        assertEquals("prefix ", h.ic.getText())

        h.session.onWindowShown()
        advanceUntilIdle()
        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix hello", h.ic.getText())
    }

    @Test
    fun focusChangeWhilePaused_thenStop_holdsTranscriptForOriginalField() = sessionTest { h ->
        h.recordFor(1_000, locked = true)
        h.session.onFocusChanged(h.otherFocus)
        runCurrent()
        assertTrue((h.session.state.value as SessionState.Recording).paused)

        h.session.onStop()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(h.events.contains(SessionEvent.CommitBlockedByFocus("hello")))
        assertEquals("hello", h.session.pendingCommit.value)
        assertEquals("prefix ", h.ic.getText())

        h.session.onPendingCommitInsert()
        assertEquals("prefix hello", h.ic.getText())
        assertNull(h.session.pendingCommit.value)
    }

    @Test
    fun recorderStartFailure_entersError_andReleasesFocus() = sessionTest { h ->
        h.recorder.startFailure = AudioRecorder.ErrorKind.MIC_UNAVAILABLE
        h.session.onPressStart()
        advanceUntilIdle()

        assertEquals(
            SessionState.Error(SessionState.ErrorKind.MIC_UNAVAILABLE, recoverable = true),
            h.session.state.value,
        )
        assertTrue(h.events.contains(SessionEvent.RecordingFailed(AudioRecorder.ErrorKind.MIC_UNAVAILABLE)))
        assertEquals(1, h.gateway.requests)
        assertEquals(1, h.gateway.abandons)
        assertFalse(h.session.isBusy)
    }

    @Test
    fun transcriberFailure_isDistinctFromEmptyText() = sessionTest { h ->
        h.transcript = { Result.failure(java.io.IOException("HTTP 500")) }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        assertTrue(h.events.contains(SessionEvent.TranscriptionFailed("HTTP 500")))
        assertFalse(h.events.contains(SessionEvent.TranscriptionEmpty))
        assertEquals(
            SessionState.Error(SessionState.ErrorKind.TRANSCRIPTION_FAILED, recoverable = true),
            h.session.state.value,
        )

        h.events.clear()
        h.transcript = { Result.success("  ") }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        assertTrue(h.events.contains(SessionEvent.TranscriptionEmpty))
        assertFalse(h.events.any { it is SessionEvent.TranscriptionFailed })
        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun droppedPcm_ignoresRealtimeResult() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "streamed")
        h.streaming = streaming
        h.recorder.droppedChunks = 3
        h.transcript = { Result.failure(java.io.IOException("offline")) }
        h.recordFor(1_000)
        streaming.events.emit(StreamEvent.Partial("stream"))
        runCurrent()
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(0, streaming.commits)
        assertTrue(streaming.cancels >= 1)
        assertTrue(h.events.contains(SessionEvent.TranscriptionFailed("offline")))
        assertFalse(h.events.any { it is SessionEvent.Committed })
    }

    @Test
    fun healthyStream_isFallbackWhenBatchFails() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "streamed")
        h.streaming = streaming
        h.transcript = { Result.failure(java.io.IOException("offline")) }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(1, streaming.commits)
        assertTrue(h.events.contains(SessionEvent.Committed("streamed")))
    }

    @Test
    fun commandModeActive_ignoresMicStart() = sessionTest { h ->
        h.commandMode = true
        h.session.onPressStart()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals(0, h.plannerCalls)
    }

    @Test
    fun cancelDuringEnhancing_keepsRawText() = sessionTest { h ->
        val gate = CompletableDeferred<DictationEnhancer.Result>()
        h.enhancer = object : DictationEnhancer {
            override suspend fun isEnabled(request: DictationEnhancer.Request) = true
            override suspend fun enhance(request: DictationEnhancer.Request) = gate.await()
        }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        assertEquals(SessionState.Enhancing(h.focus, "hello"), h.session.state.value)

        h.session.onCancel()
        gate.complete(DictationEnhancer.Result.Enhanced("HELLO", "P (m)"))
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix hello", h.ic.getText())
        assertTrue(h.events.contains(SessionEvent.Completed("hello", "hello", 1_000L)))
        assertEquals(1, h.gateway.abandons)
    }

    @Test
    fun everyTerminalPath_releasesRecorderAndFocusExactlyOnce() {
        val paths: Map<String, suspend TestScope.(Harness) -> Unit> = mapOf(
            "send" to { h ->
                h.recordFor(1_000)
                h.session.onPressRelease()
            },
            "cancel while recording" to { h ->
                h.recordFor(1_000, locked = true)
                h.session.onCancel()
            },
            "cancel while paused" to { h ->
                h.recordFor(1_000, locked = true)
                h.session.onPause()
                h.session.onCancel()
            },
            "short press" to { h ->
                h.recordFor(100)
                h.session.onPressRelease()
            },
            "capture error" to { h ->
                h.recordFor(1_000)
                h.recorder.errors.emit(AudioRecorder.Event.Error(AudioRecorder.ErrorKind.CAPTURE_FAILED, "boom"))
            },
            "transcriber failure" to { h ->
                h.transcript = { Result.failure(java.io.IOException("x")) }
                h.recordFor(1_000)
                h.session.onPressRelease()
            },
            "send blocked" to { h ->
                h.recordFor(1_000, locked = true)
                h.sendAllowed = false
                h.session.onStop()
            },
            "window hidden then cancel" to { h ->
                h.recordFor(1_000, locked = true)
                h.session.onWindowHidden()
                h.session.onCancel()
            },
        )
        for ((name, path) in paths) {
            sessionTest { h ->
                path(h)
                advanceUntilIdle()
                // Late intents after the terminal transition must not release anything again.
                h.session.onCancel()
                h.session.onPressRelease()
                advanceUntilIdle()

                assertFalse(name, h.session.isBusy)
                assertEquals("$name: recorder releases", 1, h.recorder.releases)
                assertEquals("$name: focus abandons", 1, h.gateway.abandons)
                assertFalse("$name: temp file", h.audioFile.exists())
            }
        }
    }

    // region Phase 1 review regressions (WP-1.7)

    @Test
    fun cancelThenImmediateRestart_keepsTheNewTakesOwnFile() = sessionTest { h ->
        val files = List(2) { File.createTempFile("take$it", ".wav").apply { writeText("pcm") } }
        var next = 0
        h.nextAudioFile = { files[next++] }
        h.recordFor(1_000, locked = true)
        h.session.onPause()
        h.session.onCancel()
        h.recordFor(1_000, locked = true)

        assertEquals(files, h.recorder.startedFiles)
        assertFalse(files[0].exists())
        assertTrue(files[1].exists())

        h.session.onStop()
        advanceUntilIdle()
        assertTrue(h.events.contains(SessionEvent.Committed("hello")))
        assertFalse(files[1].exists())
    }

    @Test
    fun focusChangeDuringStreaming_cancelLeavesOtherFieldUntouched() = sessionTest { h ->
        h.streaming = FakeStreaming(committed = null)
        h.recordFor(1_000, locked = true)
        (h.streaming as FakeStreaming).events.emit(StreamEvent.Partial("hel"))
        runCurrent()
        assertEquals("prefix hel", h.ic.getText())

        // Field B takes over the connection with its own composing text.
        h.session.onFocusChanged(h.otherFocus)
        h.ic.setText("B")
        h.ic.setComposingText("x", 1)
        h.session.onCancel()
        advanceUntilIdle()

        assertEquals("Bx", h.ic.getText())
        assertEquals(1 to 2, h.ic.composingRegion())
    }

    @Test
    fun commitBlockedByFocus_resetsComposingOwnership_beforeInsertingElsewhere() = sessionTest { h ->
        h.streaming = FakeStreaming(committed = null)
        h.recordFor(1_000, locked = true)
        (h.streaming as FakeStreaming).events.emit(StreamEvent.Partial("hel"))
        runCurrent()
        h.session.onFocusChanged(h.otherFocus)
        h.ic.setText("B")
        h.session.onStop()
        advanceUntilIdle()
        assertTrue(h.events.contains(SessionEvent.CommitBlockedByFocus("hello")))
        assertEquals("B", h.ic.getText())

        h.session.onPendingCommitInsert()

        assertEquals("Bhello", h.ic.getText())
        // Undo uses B's real insertion offset, not the stale offset of A's partial.
        assertTrue(h.committer.undoLastInsertion(h.otherFocus))
        assertEquals("B", h.ic.getText())
    }

    @Test
    fun emptyTranscript_clearsStreamedPartial() = sessionTest { h ->
        h.streaming = FakeStreaming(committed = null)
        // Dropped PCM disables the stream fallback, so the take really ends empty.
        h.recorder.droppedChunks = 1
        h.transcript = { Result.success("  ") }
        h.recordFor(1_000, locked = true)
        (h.streaming as FakeStreaming).events.emit(StreamEvent.Partial("hel"))
        runCurrent()
        assertEquals("prefix hel", h.ic.getText())

        h.session.onStop()
        advanceUntilIdle()

        assertTrue(h.events.contains(SessionEvent.TranscriptionEmpty))
        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun partialsWithoutSelectionSnapshot_replaceInsteadOfAppending() = sessionTest { h ->
        h.ic.extractedTextAvailable = false
        h.streaming = FakeStreaming(committed = null)
        h.recordFor(1_000, locked = true)
        val streaming = h.streaming as FakeStreaming
        streaming.events.emit(StreamEvent.Partial("he"))
        runCurrent()
        advanceTimeBy(DictationSession.PARTIAL_THROTTLE_MS + 1)
        streaming.events.emit(StreamEvent.Partial("hell"))
        runCurrent()
        assertEquals("prefix hell", h.ic.getText())

        h.session.onStop()
        advanceUntilIdle()

        assertEquals("prefix hello", h.ic.getText())
    }

    @Test
    fun contextPrompt_isCapturedAtStop_beforeFocusMoves() = sessionTest { h ->
        h.useContext = true
        h.recordFor(1_000, locked = true)

        h.session.onStop()
        // Focus moves to another field before the stop coroutine runs.
        h.session.onFocusChanged(h.otherFocus)
        h.ic.setText("secret from field B ")
        advanceUntilIdle()

        assertEquals(listOf("prefix "), h.requests.map { it.contextPrompt })
    }

    @Test
    fun contextPrompt_isOmittedWhenFocusAlreadyMoved() = sessionTest { h ->
        h.useContext = true
        h.recordFor(1_000, locked = true)
        h.session.onFocusChanged(h.otherFocus)
        h.ic.setText("secret from field B ")

        h.session.onStop()
        advanceUntilIdle()

        assertEquals(listOf<String?>(null), h.requests.map { it.contextPrompt })
    }

    @Test
    fun sendBlockedWhileRecorderStops_dispatchesNothing() = sessionTest { h ->
        h.recordFor(1_000, locked = true)

        h.session.onStop()
        h.sendAllowed = false
        advanceUntilIdle()

        assertTrue(h.requests.isEmpty())
        assertTrue(h.events.contains(SessionEvent.SendBlocked))
        assertTrue(h.events.contains(SessionEvent.Discarded(1_000L)))
        assertEquals(SessionState.Idle, h.session.state.value)
        assertFalse(h.audioFile.exists())
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun pressStartWithPendingCommit_isBlocked_andKeepsTranscript() = sessionTest { h ->
        h.recordFor(1_000, locked = true)
        h.session.onFocusChanged(h.otherFocus)
        h.session.onStop()
        advanceUntilIdle()
        assertEquals("hello", h.session.pendingCommit.value)
        val plannerCalls = h.plannerCalls

        h.session.onPressStart()
        advanceUntilIdle()

        assertEquals("hello", h.session.pendingCommit.value)
        assertEquals(plannerCalls, h.plannerCalls)
        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(h.events.contains(SessionEvent.Notice(com.github.shekohex.whisperpp.R.string.dictation_pending_commit_blocks_start)))
    }

    @Test
    fun rejectedInsert_keepsTranscriptPending_insteadOfReportingSuccess() = sessionTest { h ->
        h.ic.rejectInserts = true
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertTrue(h.events.contains(SessionEvent.CommitFailed("hello", SessionEvent.CommitFailed.Reason.INSERT_FAILED)))
        assertFalse(h.events.any { it is SessionEvent.Committed || it is SessionEvent.Completed })
        assertEquals("hello", h.session.pendingCommit.value)

        h.ic.rejectInserts = false
        h.session.onPendingCommitInsert()

        assertEquals("prefix hello", h.ic.getText())
        assertNull(h.session.pendingCommit.value)
    }

    // endregion
}
