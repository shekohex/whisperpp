package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiRealtimeSessionTest {

    private class FakeSocket(private val request: Request, val listener: WebSocketListener) : WebSocket {
        val sent = mutableListOf<JSONObject>()
        var closeCode: Int? = null

        override fun request(): Request = request

        override fun queueSize(): Long = 0

        override fun send(text: String): Boolean {
            sent += JSONObject(text)
            return true
        }

        override fun send(bytes: ByteString): Boolean = false

        override fun close(code: Int, reason: String?): Boolean {
            closeCode = code
            return true
        }

        override fun cancel() = Unit

        fun open() = listener.onOpen(this, response(101))

        fun receive(json: String) = listener.onMessage(this, json)

        fun fail(t: Throwable, code: Int? = null) = listener.onFailure(this, t, code?.let(::response))

        fun types(): List<String> = sent.map { it.getString("type") }

        private fun response(code: Int) = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .build()
    }

    private class FakeFactory : WebSocket.Factory {
        val sockets = mutableListOf<FakeSocket>()

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
            FakeSocket(request, listener).also { sockets += it }
    }

    private val factory = FakeFactory()

    private fun session(
        endpoint: String = "https://api.openai.com/v1",
        language: String = "ar",
        prompt: String = "Vocab",
        inputSampleRate: Int = 24_000,
        vad: Vad = Vad.ServerVad(),
    ) = OpenAiRealtimeSession(
        sockets = factory,
        provider = ServiceProvider(id = "p", name = "OpenAI", type = ProviderType.OPENAI, endpoint = endpoint, models = emptyList()),
        model = ModelConfig("gpt-4o-transcribe", "GPT-4o Transcribe", ModelCapabilities(stt = true, realtime = true)),
        credentials = Credentials("sk-test-key"),
        request = TranscriptionRequest(
            provider = ServiceProvider(name = "unused", type = ProviderType.OPENAI, endpoint = endpoint, models = emptyList()),
            modelId = "gpt-4o-transcribe",
            languageCode = language,
            prompt = prompt,
        ),
        commitTimeoutMs = COMMIT_TIMEOUT_MS,
        inputSampleRate = inputSampleRate,
        vad = vad,
    )

    private fun completed(itemId: String, text: String) =
        """{"type":"conversation.item.input_audio_transcription.completed","item_id":"$itemId","transcript":"$text"}"""

    private fun committed(itemId: String) = """{"type":"input_audio_buffer.committed","item_id":"$itemId"}"""

    private fun delta(itemId: String, text: String) =
        """{"type":"conversation.item.input_audio_transcription.delta","item_id":"$itemId","delta":"$text"}"""

    private fun speechStarted(itemId: String) = """{"type":"input_audio_buffer.speech_started","item_id":"$itemId"}"""

    private fun speechStopped(itemId: String) = """{"type":"input_audio_buffer.speech_stopped","item_id":"$itemId"}"""

    private val emptyCommitError =
        """{"type":"error","error":{"type":"invalid_request_error","code":"input_audio_buffer_commit_empty","message":"buffer too small"}}"""

    /** One VAD utterance: speech, auto-commit, deltas and completion. */
    private fun FakeSocket.utterance(itemId: String, vararg deltas: String) {
        receive(speechStarted(itemId))
        receive(speechStopped(itemId))
        receive(committed(itemId))
        deltas.forEach { receive(delta(itemId, it)) }
        receive(completed(itemId, deltas.joinToString("")))
    }

    private fun TestScope.collectEvents(session: OpenAiRealtimeSession): MutableList<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        backgroundScope.launch { session.events.collect { events += it } }
        runCurrent()
        return events
    }

    @Test
    fun start_upgradesHttpsUrl_withHeaders_andSendsSessionUpdateOnOpen() = runTest {
        val session = session()
        session.start()

        val socket = factory.sockets.single()
        val request = socket.request()
        assertEquals("https://api.openai.com/v1/realtime?intent=transcription", request.url.toString())
        assertEquals("Bearer sk-test-key", request.header("Authorization"))
        assertEquals("realtime=v1", request.header("OpenAI-Beta"))
        assertTrue(socket.sent.isEmpty())

        socket.open()

        val update = socket.sent.single()
        assertEquals("transcription_session.update", update.getString("type"))
        val config = update.getJSONObject("session")
        assertEquals("pcm16", config.getString("input_audio_format"))
        val turnDetection = config.getJSONObject("turn_detection")
        assertEquals("server_vad", turnDetection.getString("type"))
        assertEquals(0.5, turnDetection.getDouble("threshold"), 0.0)
        assertEquals(300, turnDetection.getInt("prefix_padding_ms"))
        assertEquals(500, turnDetection.getInt("silence_duration_ms"))
        val transcription = config.getJSONObject("input_audio_transcription")
        assertEquals("gpt-4o-transcribe", transcription.getString("model"))
        assertEquals("ar", transcription.getString("language"))
        assertEquals("Vocab", transcription.getString("prompt"))
    }

    @Test
    fun realtimeUrl_acceptsCommonBaseShapes() {
        val expected = "https://api.openai.com/v1/realtime?intent=transcription"
        for (base in listOf("https://api.openai.com", "https://api.openai.com/v1/", "https://api.openai.com/v1/realtime", "https://api.openai.com/v1/audio/transcriptions")) {
            assertEquals(base, expected, OpenAiRealtimeSession.realtimeUrl(base.toHttpUrl()).toString())
        }
    }

    @Test
    fun cleartextToPublicHost_isRejected_butLocalHostIsAllowed() = runTest {
        try {
            session(endpoint = "http://example.com/v1").start()
            fail("expected InsecureTransport")
        } catch (e: NetworkError.InsecureTransport) {
            assertEquals("example.com", e.host)
        }
        assertTrue(factory.sockets.isEmpty())

        session(endpoint = "http://192.168.1.20:8000/v1").start()
        assertEquals("http://192.168.1.20:8000/v1/realtime?intent=transcription", factory.sockets.single().request().url.toString())
    }

    @Test
    fun sendAudio_appendsBase64Pcm_andBuffersUntilOpen() = runTest {
        val session = session()
        session.start()
        val early = byteArrayOf(1, 2, 3, 4)
        val late = byteArrayOf(5, 6)
        session.sendAudio(early)
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(late)

        assertEquals(listOf("transcription_session.update", "input_audio_buffer.append", "input_audio_buffer.append"), socket.types())
        assertEquals(early.toByteString().base64(), socket.sent[1].getString("audio"))
        assertEquals(late.toByteString().base64(), socket.sent[2].getString("audio"))
    }

    @Test
    fun sendAudio_resamples16kTo24k() = runTest {
        val session = session(inputSampleRate = 16_000)
        session.start()
        factory.sockets.single().open()
        session.sendAudio(ByteArray(640))
        session.sendAudio(ByteArray(640))

        val bytes = factory.sockets.single().sent.drop(1).sumOf { it.getString("audio").decodeBase64()!!.size }
        // 640 input samples → 960 at 24 kHz, minus the one sample held back for interpolation.
        assertTrue("got $bytes", bytes in (958 * 2)..(960 * 2))
    }

    @Test
    fun start_withoutVad_sendsNullTurnDetection() = runTest {
        session(vad = Vad.None).start()
        val socket = factory.sockets.single()
        socket.open()

        val config = socket.sent.single().getJSONObject("session")
        assertTrue(config.has("turn_detection"))
        assertTrue(config.isNull("turn_detection"))
    }

    @Test
    fun manualCommit_awaitsTheCompletion_neverReturnsPartials() = runTest {
        val session = session(vad = Vad.None)
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(byteArrayOf(1, 2))

        val result = async { session.commit() }
        runCurrent()
        assertEquals("input_audio_buffer.commit", socket.types().last())
        socket.receive(committed("item_1"))
        socket.receive(delta("item_1", "hel"))
        socket.receive(delta("item_1", "lo"))
        runCurrent()
        assertFalse(result.isCompleted)

        socket.receive(completed("item_1", "hello"))
        runCurrent()

        assertEquals("hello", result.await().getOrThrow().text)
        runCurrent()
        assertTrue(events.contains(StreamEvent.Partial("hel")))
        assertTrue(events.contains(StreamEvent.Partial("hello")))
        assertTrue(events.contains(StreamEvent.Final("hello")))
        assertEquals(StreamEvent.Closed, events.last())
        assertEquals(1000, socket.closeCode)
    }

    @Test
    fun serverVad_twoUtterances_cumulativePartials_commitAwaitsBoth() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(byteArrayOf(1, 2))

        socket.utterance("item_1", "Hello", " there.")
        socket.receive(speechStarted("item_2"))
        socket.receive(speechStopped("item_2"))
        socket.receive(committed("item_2"))
        socket.receive(delta("item_2", "How"))
        runCurrent()
        assertEquals(StreamEvent.Partial("Hello there. How"), events.last())

        // A third utterance is still being spoken at stop: we commit it.
        socket.receive(speechStarted("item_3"))
        session.sendAudio(ByteArray(SPEECH_BYTES))
        val result = async { session.commit() }
        runCurrent()
        assertEquals("input_audio_buffer.commit", socket.types().last())

        socket.receive(delta("item_2", " are you?"))
        socket.receive(completed("item_2", "How are you?"))
        runCurrent()
        assertFalse("waits for the manually committed item", result.isCompleted)

        socket.receive(committed("item_3"))
        socket.receive(completed("item_3", "Bye."))
        runCurrent()

        assertEquals("Hello there. How are you? Bye.", result.await().getOrThrow().text)
        runCurrent()
        assertTrue(events.contains(StreamEvent.Partial("Hello")))
        assertTrue(events.contains(StreamEvent.Partial("Hello there.")))
        assertTrue(events.contains(StreamEvent.Partial("Hello there. How are you?")))
        assertTrue(events.contains(StreamEvent.Final("Hello there. How are you? Bye.")))
    }

    @Test
    fun serverVad_autoCommittedTrailingAudio_needsNoManualCommit() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(byteArrayOf(1, 2))
        socket.receive(speechStarted("item_1"))
        socket.receive(speechStopped("item_1"))
        socket.receive(committed("item_1"))

        val result = async { session.commit() }
        runCurrent()
        assertFalse(socket.types().contains("input_audio_buffer.commit"))
        assertFalse("item_1 is still transcribing", result.isCompleted)

        socket.receive(completed("item_1", "Done."))
        runCurrent()
        assertEquals("Done.", result.await().getOrThrow().text)
    }

    @Test
    fun serverVad_emptyBufferOnCommit_isNothingPending() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(byteArrayOf(1, 2))
        socket.utterance("item_1", "Only this.")
        socket.receive(speechStarted("item_2"))
        session.sendAudio(ByteArray(SPEECH_BYTES))

        val result = async { session.commit() }
        runCurrent()
        assertEquals("input_audio_buffer.commit", socket.types().last())
        // The server had already consumed the audio; item_2 never gets a buffer.
        socket.receive(emptyCommitError)
        runCurrent()

        assertEquals("Only this.", result.await().getOrThrow().text)
    }

    @Test
    fun serverVad_silenceAfterSpeechStopped_isCleared_notCommitted() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(byteArrayOf(1, 2))
        socket.utterance("item_1", "Hello.")
        // Seconds of silence after the server auto-committed the utterance.
        session.sendAudio(ByteArray(SPEECH_BYTES * 20))

        val result = async { session.commit() }
        runCurrent()

        assertEquals("input_audio_buffer.clear", socket.types().last())
        assertFalse(socket.types().contains("input_audio_buffer.commit"))
        assertEquals("Hello.", result.await().getOrThrow().text)
    }

    @Test
    fun serverVad_speechInProgress_isCommitted_unlessShorterThan100ms() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(speechStarted("item_1"))
        session.sendAudio(ByteArray(SPEECH_BYTES))

        val result = async { session.commit() }
        runCurrent()
        assertEquals("input_audio_buffer.commit", socket.types().last())
        socket.receive(committed("item_1"))
        socket.receive(completed("item_1", "Still talking"))
        runCurrent()
        assertEquals("Still talking", result.await().getOrThrow().text)

        // Under 100 ms after speech started counts as silence: cleared, and the empty item is dropped.
        val short = session()
        short.start()
        val shortSocket = factory.sockets.last()
        shortSocket.open()
        shortSocket.receive(speechStarted("item_x"))
        short.sendAudio(ByteArray(SPEECH_BYTES / 4))

        val shortResult = async { short.commit() }
        runCurrent()
        assertEquals("input_audio_buffer.clear", shortSocket.types().last())
        assertEquals("", shortResult.await().getOrThrow().text)
    }

    @Test
    fun serverVad_lateDeltaForOldItem_keepsOrder_andIsDroppedOnceCompleted() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(byteArrayOf(1, 2))

        socket.receive(speechStarted("item_1"))
        socket.receive(speechStopped("item_1"))
        socket.receive(committed("item_1"))
        socket.receive(delta("item_1", "One"))
        socket.receive(speechStarted("item_2"))
        socket.receive(speechStopped("item_2"))
        socket.receive(committed("item_2"))
        socket.receive(delta("item_2", "Two"))
        // item_1 is still transcribing; its delta lands in item_1, not after "Two".
        socket.receive(delta("item_1", " more"))
        runCurrent()
        assertEquals(StreamEvent.Partial("One more Two"), events.last())

        socket.receive(completed("item_1", "One more."))
        socket.receive(delta("item_1", " ghost"))
        socket.receive(completed("item_2", "Two."))
        runCurrent()

        assertEquals("One more. Two.", session.commit().getOrThrow().text)
    }

    @Test
    fun commitBeforeOpen_isSentAfterSessionUpdateAndReplay() = runTest {
        val session = session(vad = Vad.None)
        session.start()
        session.sendAudio(byteArrayOf(1, 2))
        val result = async { session.commit() }
        runCurrent()
        val socket = factory.sockets.single()
        socket.open()

        assertEquals(listOf("transcription_session.update", "input_audio_buffer.append", "input_audio_buffer.commit"), socket.types())
        socket.receive(committed("item_1"))
        socket.receive(completed("item_1", "ok"))
        runCurrent()
        assertEquals("ok", result.await().getOrThrow().text)
    }

    @Test
    fun commit_withoutFinal_timesOut() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive("""{"type":"conversation.item.input_audio_transcription.delta","delta":"partial only"}""")

        val result = async { session.commit() }
        advanceTimeBy(COMMIT_TIMEOUT_MS + 1)
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is NetworkError.Timeout)
        assertEquals(1000, socket.closeCode)
    }

    @Test
    fun errorEvent_failsTheStream_withTypedNetworkError() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"type":"error","error":{"type":"invalid_request_error","code":"invalid_api_key","message":"bad key"}}""")
        runCurrent()

        val error = session.commit().exceptionOrNull()
        runCurrent()
        assertTrue(error is NetworkError.Unauthorized)
        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Unauthorized })
        assertEquals(1, factory.sockets.size)
    }

    @Test
    fun transientFailureBeforeCommit_reconnectsOnce_andReplaysAudio() = runTest {
        val session = session()
        session.start()
        val first = factory.sockets.single()
        first.open()
        val chunk = byteArrayOf(9, 9)
        session.sendAudio(chunk)

        first.fail(IOException("reset"))
        assertEquals(2, factory.sockets.size)
        val second = factory.sockets[1]
        second.open()
        assertEquals(listOf("transcription_session.update", "input_audio_buffer.append"), second.types())
        assertEquals(chunk.toByteString().base64(), second.sent[1].getString("audio"))

        // Late callbacks of the replaced socket are ignored.
        first.receive(completed("x", "ghost"))

        second.fail(IOException("reset again"))
        assertEquals(2, factory.sockets.size)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Offline)
    }

    @Test
    fun failureAfterCommit_neverReconnects() = runTest {
        val session = session(vad = Vad.None)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(byteArrayOf(1, 2))
        val result = async { session.commit() }
        runCurrent()

        socket.fail(IOException("reset"))
        runCurrent()

        assertEquals(1, factory.sockets.size)
        assertTrue(result.await().exceptionOrNull() is NetworkError.Offline)
    }

    @Test
    fun handshakeUnauthorized_isNotRetried() = runTest {
        val session = session()
        session.start()
        factory.sockets.single().fail(IOException("upgrade failed"), code = 401)

        assertEquals(1, factory.sockets.size)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Unauthorized)
    }

    @Test
    fun cancel_closesWithoutCommitting() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        session.cancel()
        session.cancel()

        assertEquals(listOf("transcription_session.update"), socket.types())
        assertEquals(1000, socket.closeCode)
        assertTrue(session.commit().isFailure)
    }

    private companion object {
        const val COMMIT_TIMEOUT_MS = 2_000L

        // 200 ms of 24 kHz PCM16 (input rate is 24 kHz in these tests, so no resampling).
        const val SPEECH_BYTES = 9_600
    }
}
