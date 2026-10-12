package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.core.audio.AudioFocusController
import com.github.shekohex.whisperpp.core.audio.AudioFocusGateway
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.dictation.FakeInputConnection
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.modes.OutputMode
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mode output modes (§4.10) and per-run options as seen by [DictationSession]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionOutputModeTest {

    private class NoopGateway : AudioFocusGateway {
        override fun request(onFocusChange: (Int) -> Unit): Boolean = true
        override fun abandon() = Unit
    }

    private class FakeRecorder : DictationRecorder {
        override val errors = MutableSharedFlow<AudioRecorder.Event.Error>()
        private var file: File? = null

        override suspend fun start(file: File, onPcm: ((ByteArray) -> Unit)?): Result<Unit> {
            this.file = file
            return Result.success(Unit)
        }

        override suspend fun stop(): Result<AudioRecorder.Event.Stopped> =
            Result.success(AudioRecorder.Event.Stopped(file!!, durationMs = 1_000L, droppedChunks = 0))

        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override fun cancel() = Unit
    }

    private class RecordingEnhancer(private val output: String?) : DictationEnhancer {
        val requests = mutableListOf<DictationEnhancer.Request>()

        override suspend fun isEnabled(request: DictationEnhancer.Request): Boolean =
            request.options.enhancementEnabled ?: false

        override suspend fun enhance(request: DictationEnhancer.Request): DictationEnhancer.Result {
            requests += request
            return output?.let { DictationEnhancer.Result.Enhanced(it, "P (gpt)") } ?: DictationEnhancer.Result.Kept()
        }
    }

    private inner class Harness(val test: TestScope, val options: DictationOptions, val enhancer: DictationEnhancer? = null) {
        val focus = FocusKey("com.example", inputType = 1, fieldId = null, focusInstanceId = 1L)
        val ic = FakeInputConnection().apply { setText("prefix ") }
        val events = mutableListOf<SessionEvent>()
        val postProcessOptions = mutableListOf<DictationOptions>()
        var clipboard: String? = null
        private val audioFile: File = File.createTempFile("mode", ".wav").apply { writeText("pcm") }
        private val dispatcher = StandardTestDispatcher(test.testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val session: DictationSession by lazy {
            DictationSession(
                DictationSession.Deps(
                    recorder = FakeRecorder(),
                    focus = AudioFocusController(NoopGateway(), MutableStateFlow(false)),
                    cues = {},
                    transcriber = { _, _ -> Result.success("hello") },
                    enhancer = enhancer,
                    committer = TextCommitter { ic },
                    planner = { StartDecision.Proceed(StartPlan(audioFile, request(), options = options)) },
                    sendAllowed = { true },
                    commandModeActive = { false },
                    clock = { test.testScheduler.currentTime },
                    scope = scope,
                    streamingDispatcher = dispatcher,
                    postProcess = { text, opts ->
                        postProcessOptions += opts
                        text
                    },
                    clipboardText = { clipboard },
                ),
            ).also { session ->
                session.onFocusChanged(focus)
                scope.launch { session.events.collect { events += it } }
                test.runCurrent()
            }
        }

        /** Starts, then lets [beforeStop] change the editor while recording, then sends. */
        fun dictate(beforeStop: () -> Unit = {}) {
            session.onPressStart(locked = true)
            test.runCurrent()
            beforeStop()
            test.advanceTimeBy(1_000)
            session.onStop()
            test.advanceUntilIdle()
        }

        fun dispose() {
            scope.cancel()
            audioFile.delete()
        }
    }

    private fun request() = TranscriptionRequest(
        provider = ServiceProvider(id = "p", name = "P", type = ProviderType.OPENAI, endpoint = "https://example.com", models = emptyList()),
        modelId = "whisper-1",
        timeoutMs = 1_000,
    )

    private fun modeTest(
        options: DictationOptions,
        enhancer: DictationEnhancer? = null,
        block: suspend TestScope.(Harness) -> Unit,
    ) = runTest {
        val harness = Harness(this, options, enhancer)
        try {
            block(harness)
        } finally {
            harness.dispose()
        }
    }

    @Test
    fun insert_isTheDefaultBehaviour() = modeTest(DictationOptions()) { h ->
        h.dictate()

        assertEquals("prefix hello", h.ic.getText())
        val completed = h.events.filterIsInstance<SessionEvent.Completed>().single()
        assertTrue(completed.inserted)
        assertTrue(h.events.none { it is SessionEvent.CopyToClipboard })
    }

    @Test
    fun clipboard_leavesTheFieldAlone_andCopiesTheEnhancedText() = modeTest(
        DictationOptions(outputMode = OutputMode.CLIPBOARD, enhancementEnabled = true, autoSend = true),
        RecordingEnhancer("Hello!"),
    ) { h ->
        h.dictate()

        assertEquals("prefix ", h.ic.getText())
        assertEquals(SessionEvent.CopyToClipboard("Hello!"), h.events.filterIsInstance<SessionEvent.CopyToClipboard>().single())
        val completed = h.events.filterIsInstance<SessionEvent.Completed>().single()
        assertFalse("nothing to auto-send", completed.inserted)
        assertEquals("hello", completed.rawText)
        assertEquals("Hello!", completed.finalText)
        assertTrue(h.events.none { it is SessionEvent.Committed })
    }

    @Test
    fun clipboard_withoutEnhancement_copiesTheRawText() = modeTest(DictationOptions(outputMode = OutputMode.CLIPBOARD)) { h ->
        h.dictate()

        assertEquals("prefix ", h.ic.getText())
        assertEquals("hello", h.events.filterIsInstance<SessionEvent.CopyToClipboard>().single().text)
    }

    @Test
    fun replaceSelection_replacesTheSelectionFromRecordingStart() = modeTest(DictationOptions(outputMode = OutputMode.REPLACE_SELECTION)) { h ->
        h.ic.setText("Hello world")
        h.ic.setSelection(6, 11)

        // The cursor moves while recording; the selection captured at the start is still replaced.
        h.dictate { h.ic.setSelection(0, 0) }

        assertEquals("Hello hello", h.ic.getText())
        assertTrue(h.events.filterIsInstance<SessionEvent.Completed>().single().inserted)
    }

    @Test
    fun replaceSelection_withoutSelection_insertsAtTheCursor() = modeTest(DictationOptions(outputMode = OutputMode.REPLACE_SELECTION)) { h ->
        h.dictate()

        assertEquals("prefix hello", h.ic.getText())
    }

    @Test
    fun options_reachPostProcessEnhancerAndCompletion() = modeTest(
        DictationOptions(
            modeId = "assistant",
            enhancementEnabled = true,
            removeFillers = true,
            autoSend = false,
            contextSelectedText = true,
            contextClipboard = true,
        ),
        RecordingEnhancer(null),
    ) { h ->
        h.clipboard = "clip"
        h.ic.setText("pick me")
        h.ic.setSelection(0, 7)

        h.dictate()

        assertEquals("assistant", h.postProcessOptions.single().modeId)
        val request = h.enhancerRequests().single()
        assertEquals("assistant", request.options.modeId)
        assertEquals("Selected text:\npick me\n\nClipboard:\nclip", request.contextPrompt)
        assertEquals(false, h.events.filterIsInstance<SessionEvent.Completed>().single().autoSend)
    }

    private fun Harness.enhancerRequests(): List<DictationEnhancer.Request> = (enhancer as RecordingEnhancer).requests
}
