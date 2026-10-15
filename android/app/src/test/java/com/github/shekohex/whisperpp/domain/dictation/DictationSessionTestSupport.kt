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
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.io.File

/** Shared fakes and the [Harness] for the `DictationSession*Test` classes. */
internal object DictationSessionTestSupport {

    class FakeGateway : AudioFocusGateway {
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

    class FakeRecorder : DictationRecorder {
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
        var onPcm: ((ByteArray) -> Unit)? = null

        override suspend fun start(file: File, onPcm: ((ByteArray) -> Unit)?): Result<Unit> {
            this.onPcm = onPcm
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

        var stopFailure: AudioRecorder.ErrorKind? = null

        override suspend fun stop(): Result<AudioRecorder.Event.Stopped> {
            stops += 1
            stopFailure?.let { return Result.failure(AudioRecorder.RecorderException(it, "failed")) }
            return Result.success(AudioRecorder.Event.Stopped(file!!, durationMs = 1_000L, droppedChunks = droppedChunks))
        }

        override suspend fun pause() {
            pauses += 1
        }

        override suspend fun resume() = Unit

        var keptCancels = 0

        override fun cancel(keepFile: Boolean) {
            if (pendingStart) cancelledDuringStart = true
            cancels += 1
            if (keepFile) keptCancels += 1
        }
    }

    class FakeStreaming(private val committed: String?) : StreamingTranscriptionSession {
        override val events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)
        var commits = 0
        var cancels = 0
        val sent = mutableListOf<ByteArray>()
        var sentAtCommit = -1
        var startFailure: Exception? = null

        override suspend fun start() {
            startFailure?.let { throw it }
        }

        override suspend fun sendAudio(pcm16: ByteArray) {
            // Suspends like a real network send, so an undrained channel would lose the tail.
            yield()
            sent += pcm16
        }

        override suspend fun commit(): Result<Transcript> {
            commits += 1
            sentAtCommit = sent.size
            return committed?.let { Result.success(Transcript(it)) } ?: Result.failure(IllegalStateException("none"))
        }

        override suspend fun cancel() {
            cancels += 1
        }
    }

    class Harness(val test: TestScope) {
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
        var addTrailingSpace = false
        var postProcess: (String) -> String = { it }
        var history: HistoryRecorder? = null
        var options = DictationOptions()
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
                        StartDecision.Proceed(
                            StartPlan(file, request().copy(useContext = useContext, addTrailingSpace = addTrailingSpace), streaming, options),
                        )
                    },
                    sendAllowed = { sendAllowed },
                    commandModeActive = { commandMode },
                    clock = { test.testScheduler.currentTime },
                    scope = scope,
                    streamingDispatcher = dispatcher,
                    postProcess = { text, _ -> postProcess(text) },
                    history = history,
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

    /** In-memory [HistoryRecorder]: one [Record] per take with its status transitions. */
    class FakeHistory : HistoryRecorder {
        class Record(val take: HistoryRecorder.Take) : HistoryTicket {
            val statuses = mutableListOf(HistoryStatus.RECORDED)
            var rawText: String? = null
            var enhancedText: String? = null
            var reason: HistoryFailureReason? = null
            var stats: CompletionStats? = null
            var enhancementFailed = false

            val status: HistoryStatus get() = statuses.last()
        }

        val records = mutableListOf<Record>()

        override fun begin(take: HistoryRecorder.Take): HistoryTicket = Record(take).also { records += it }

        override fun transcribing(ticket: HistoryTicket) {
            (ticket as Record).statuses += HistoryStatus.TRANSCRIBING
        }

        override fun transcribed(ticket: HistoryTicket, rawText: String) {
            (ticket as Record).run {
                this.rawText = rawText
                statuses += HistoryStatus.TRANSCRIBED
            }
        }

        override fun enhancing(ticket: HistoryTicket) {
            (ticket as Record).statuses += HistoryStatus.ENHANCING
        }

        override fun complete(
            ticket: HistoryTicket,
            rawText: String,
            enhancedText: String?,
            stats: CompletionStats,
            enhancementFailed: Boolean,
        ) {
            (ticket as Record).run {
                this.rawText = rawText
                this.enhancedText = enhancedText
                this.stats = stats
                this.enhancementFailed = enhancementFailed
                statuses += HistoryStatus.COMPLETED
            }
        }

        override fun fail(ticket: HistoryTicket, reason: HistoryFailureReason, rawText: String?, enhancedText: String?) {
            (ticket as Record).run {
                this.reason = reason
                rawText?.let { this.rawText = it }
                enhancedText?.let { this.enhancedText = it }
                statuses += HistoryStatus.FAILED
            }
        }
    }

    fun request() = TranscriptionRequest(
        provider = ServiceProvider(id = "p", name = "P", type = ProviderType.OPENAI, endpoint = "https://example.com", models = emptyList()),
        modelId = "whisper-1",
        timeoutMs = 1_000,
    )

    fun sessionTest(block: suspend TestScope.(Harness) -> Unit) = runTest {
        val harness = Harness(this)
        try {
            block(harness)
        } finally {
            harness.dispose()
        }
    }

    /** What the harness plan produces: provider "p", whisper-1, auto language, instant transcription. */
    val STATS = CompletionStats(providerId = "p", sttModelId = "whisper-1")
}
