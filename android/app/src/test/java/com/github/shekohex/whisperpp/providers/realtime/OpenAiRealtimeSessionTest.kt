package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Handshake, audio buffering, partial/final assembly and failure handling of the GA protocol. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiRealtimeSessionTest {
    private val factory = FakeWsFactory()

    @Test
    fun start_upgradesWithModelQuery_bearerAuth_andNoBetaHeader() = runTest {
        openAiSession(factory).start()

        val socket = factory.sockets.single()
        val request = socket.request()
        assertEquals("https://litellm.example.com/v1/realtime?model=gpt-4o-transcribe&intent=transcription", request.url.toString())
        assertEquals("Bearer sk-test-key", request.header("Authorization"))
        assertNull(request.header("OpenAI-Beta"))
        assertTrue(socket.frames.isEmpty())
    }

    @Test
    fun open_sendsGaSessionUpdate() = runTest {
        openAiSession(factory).start()
        val socket = factory.sockets.single()
        socket.open()

        val update = socket.json.single()
        assertEquals("session.update", update.getString("type"))
        val session = update.getJSONObject("session")
        assertEquals("transcription", session.getString("type"))
        val input = session.getJSONObject("audio").getJSONObject("input")
        val format = input.getJSONObject("format")
        assertEquals("audio/pcm", format.getString("type"))
        assertEquals(24_000, format.getInt("rate"))
        val transcription = input.getJSONObject("transcription")
        assertEquals("gpt-4o-transcribe", transcription.getString("model"))
        assertEquals("ar", transcription.getString("language"))
        assertEquals("Vocab", transcription.getString("prompt"))
        val vad = input.getJSONObject("turn_detection")
        assertEquals("server_vad", vad.getString("type"))
        assertEquals(400, vad.getInt("silence_duration_ms"))
        assertEquals(setOf("type", "silence_duration_ms"), vad.keys().asSequence().toSet())
        // The GA shape has no beta fields.
        assertFalse(session.has("input_audio_format"))
        assertFalse(session.has("input_audio_transcription"))
    }

    @Test
    fun autoLanguage_andBlankPrompt_areOmitted() = runTest {
        openAiSession(factory, language = "auto", prompt = " ").start()
        val socket = factory.sockets.single()
        socket.open()

        val transcription = socket.json.single().getJSONObject("session").getJSONObject("audio")
            .getJSONObject("input").getJSONObject("transcription")
        assertEquals(setOf("model"), transcription.keys().asSequence().toSet())
    }

    @Test
    fun withoutVad_sessionUpdateCarriesNullTurnDetection() = runTest {
        openAiSession(factory, vad = Vad.None).start()
        val socket = factory.sockets.single()
        socket.open()

        val input = socket.json.single().getJSONObject("session").getJSONObject("audio").getJSONObject("input")
        assertTrue(input.has("turn_detection"))
        assertTrue(input.isNull("turn_detection"))
    }

    @Test
    fun realtimeUrl_acceptsGatewayBaseShapes() {
        val expected = "https://litellm.example.com/v1/realtime?model=whisper-1&intent=transcription"
        for (base in listOf(
            "https://litellm.example.com",
            "https://litellm.example.com/",
            "https://litellm.example.com/v1",
            "https://litellm.example.com/v1/",
            "https://litellm.example.com/v1/realtime",
            "https://litellm.example.com/v1/audio/transcriptions",
        )) {
            assertEquals(base, expected, OpenAiRealtimeSession.realtimeUrl(base.toHttpUrl(), "whisper-1").toString())
        }
    }

    @Test
    fun realtimeUrl_keepsPathPrefix_portAndLanHosts() {
        assertEquals(
            "https://host/litellm/v1/realtime?model=m&intent=transcription",
            OpenAiRealtimeSession.realtimeUrl("https://host/litellm/v1".toHttpUrl(), "m").toString(),
        )
        assertEquals(
            "https://host/litellm/v1/realtime?model=m&intent=transcription",
            OpenAiRealtimeSession.realtimeUrl("https://host/litellm/v1/audio/transcriptions".toHttpUrl(), "m").toString(),
        )
        assertEquals(
            "http://192.168.1.20:4000/v1/realtime?model=m&intent=transcription",
            OpenAiRealtimeSession.realtimeUrl("http://192.168.1.20:4000".toHttpUrl(), "m").toString(),
        )
    }

    @Test
    fun realtimeUrl_dropsQuery_encodesModel_andSkipsModelForOpenAiItself() {
        assertEquals(
            "https://gw.example.com/v1/realtime?model=openai%2Fgpt-4o-transcribe&intent=transcription",
            OpenAiRealtimeSession.realtimeUrl("https://gw.example.com/v1?x=1".toHttpUrl(), "openai/gpt-4o-transcribe").toString(),
        )
        assertEquals(
            "https://api.openai.com/v1/realtime?intent=transcription",
            OpenAiRealtimeSession.realtimeUrl("https://api.openai.com/v1".toHttpUrl(), "gpt-4o-transcribe").toString(),
        )
    }

    @Test
    fun cleartextToPublicHost_isRejected_butLanHostIsAllowed() = runTest {
        try {
            openAiSession(factory, endpoint = "http://example.com/v1").start()
            fail("expected InsecureTransport")
        } catch (e: NetworkError.InsecureTransport) {
            assertEquals("example.com", e.host)
        }
        assertTrue(factory.sockets.isEmpty())

        openAiSession(factory, endpoint = "http://192.168.1.20:4000").start()
        assertEquals(
            "http://192.168.1.20:4000/v1/realtime?model=gpt-4o-transcribe&intent=transcription",
            factory.sockets.single().request().url.toString(),
        )
    }

    @Test
    fun audio_waitsForSessionUpdated_thenFlushesBufferedChunksInOrder() = runTest {
        val session = openAiSession(factory)
        val events = mutableListOf<StreamEvent>()
        backgroundScope.collectInto(session.events, events)
        runCurrent()
        session.start()
        val early = byteArrayOf(1, 2, 3, 4)
        val beforeOpen = byteArrayOf(5, 6)
        session.sendAudio(early)
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(beforeOpen)

        // Opened, but the session is not confirmed: only the session update went out.
        assertEquals(listOf("session.update"), socket.types())
        assertFalse(events.contains(StreamEvent.Connected))

        socket.receive("""{"type":"session.created"}""")
        assertEquals(listOf("session.update"), socket.types())

        socket.receive(gaSessionUpdated())
        val afterReady = byteArrayOf(7, 8)
        session.sendAudio(afterReady)
        runCurrent()

        assertEquals(listOf("session.update", "input_audio_buffer.append", "input_audio_buffer.append", "input_audio_buffer.append"), socket.types())
        val audio = socket.json.drop(1).map { it.getString("audio") }
        assertEquals(listOf(early, beforeOpen, afterReady).map { it.toByteString().base64() }, audio)
        assertEquals(StreamEvent.Connected, events.single())
    }

    @Test
    fun audio_isSentInSlicesOfAtMost24000Bytes() = runTest {
        val session = openAiSession(factory)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())

        session.sendAudio(ByteArray(60_000) { 1 })

        val sizes = socket.json.drop(1).map { it.getString("audio").decodeBase64()!!.size }
        assertEquals(listOf(24_000, 24_000, 12_000), sizes)
    }

    @Test
    fun audio_resamples16kTo24k() = runTest {
        val session = openAiSession(factory, inputSampleRate = 16_000)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())
        session.sendAudio(ByteArray(640))
        session.sendAudio(ByteArray(640))

        val bytes = socket.json.drop(1).sumOf { it.getString("audio").decodeBase64()!!.size }
        // 640 input samples → 960 at 24 kHz, minus the one sample held back for interpolation.
        assertTrue("got $bytes", bytes in (958 * 2)..(960 * 2))
    }

    @Test
    fun serverVad_twoUtterances_cumulativePartials_orderedFinal() = runTest {
        val session = openAiSession(factory)
        val events = mutableListOf<StreamEvent>()
        backgroundScope.collectInto(session.events, events)
        runCurrent()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())
        session.sendAudio(byteArrayOf(1, 2))

        socket.utterance("item_1", "Hello", " there.")
        socket.receive(speechStartedEvent("item_2"))
        socket.receive(speechStoppedEvent("item_2"))
        socket.receive(committedEvent("item_2"))
        socket.receive(deltaEvent("item_2", "How"))
        runCurrent()
        assertEquals(StreamEvent.Partial("Hello there. How"), events.last())

        // item_2 is still transcribing when the take is stopped.
        val result = async { session.commit() }
        runCurrent()
        socket.receive(gaSessionUpdated(vadOn = false))
        socket.receive(CLEARED_EVENT)
        socket.receive(deltaEvent("item_2", " are you?"))
        runCurrent()
        assertFalse(result.isCompleted)
        socket.receive(completedEvent("item_2", "How are you?"))
        runCurrent()

        assertEquals("Hello there. How are you?", result.await().getOrThrow().text)
        runCurrent()
        val partials = events.filterIsInstance<StreamEvent.Partial>().map { it.text }
        assertEquals(
            listOf("Hello", "Hello there.", "Hello there.", "Hello there. How", "Hello there. How are you?", "Hello there. How are you?"),
            partials,
        )
        assertTrue(events.contains(StreamEvent.Final("Hello there. How are you?")))
        assertEquals(StreamEvent.Closed, events.last())
        assertEquals(1000, socket.closeCode)
        assertFalse(socket.cancelled)
    }

    @Test
    fun lateDeltaForOldItem_keepsOrder_andIsDroppedOnceCompleted() = runTest {
        val session = openAiSession(factory)
        val events = mutableListOf<StreamEvent>()
        backgroundScope.collectInto(session.events, events)
        runCurrent()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())

        socket.receive(committedEvent("item_1"))
        socket.receive(deltaEvent("item_1", "One"))
        socket.receive(committedEvent("item_2"))
        socket.receive(deltaEvent("item_2", "Two"))
        // item_1 is still transcribing; its delta lands in item_1, not after "Two".
        socket.receive(deltaEvent("item_1", " more"))
        runCurrent()
        assertEquals(StreamEvent.Partial("One more Two"), events.last())

        socket.receive(completedEvent("item_1", "One more."))
        socket.receive(deltaEvent("item_1", " ghost"))
        socket.receive(completedEvent("item_2", "Two."))
        val result = async { session.commit() }
        runCurrent()
        socket.receive(gaSessionUpdated(vadOn = false))
        socket.receive(CLEARED_EVENT)
        runCurrent()

        assertEquals("One more. Two.", result.await().getOrThrow().text)
    }

    @Test
    fun connectTimeout_failsAStreamTheServerNeverConfirmed() = runTest {
        val session = openAiSession(factory)
        val events = mutableListOf<StreamEvent>()
        backgroundScope.collectInto(session.events, events)
        runCurrent()
        session.start()
        factory.sockets.single().open()

        session.connectTimeoutTick()
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Timeout })
        assertTrue(factory.sockets.single().cancelled)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Timeout)
    }

    @Test
    fun connectTimeoutTick_afterReady_isIgnored() = runTest {
        val session = openAiSession(factory)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())

        session.connectTimeoutTick()

        assertFalse(socket.cancelled)
    }

    @Test
    fun refusedSend_failsTheStream() = runTest {
        val session = openAiSession(factory)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())
        socket.acceptSends = false

        session.sendAudio(byteArrayOf(1, 2))

        assertTrue(session.commit().exceptionOrNull() is NetworkError.Offline)
        assertTrue(socket.cancelled)
    }

    @Test
    fun errorEvent_failsTheStream_withTypedNetworkError() = runTest {
        val session = openAiSession(factory)
        val events = mutableListOf<StreamEvent>()
        backgroundScope.collectInto(session.events, events)
        runCurrent()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())

        socket.receive("""{"type":"error","error":{"type":"invalid_request_error","code":"invalid_api_key","message":"bad key"}}""")
        runCurrent()

        assertTrue(session.commit().exceptionOrNull() is NetworkError.Unauthorized)
        runCurrent()
        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Unauthorized })
        assertEquals(1, factory.sockets.size)
    }

    @Test
    fun failedItemTranscription_failsTheStream() = runTest {
        val session = openAiSession(factory)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())

        socket.receive("""{"type":"conversation.item.input_audio_transcription.failed","item_id":"item_1"}""")

        assertTrue(session.commit().exceptionOrNull() is NetworkError.InvalidResponse)
    }

    @Test
    fun commit_withoutFinal_timesOut_andDropsQueuedAudio() = runTest {
        val session = openAiSession(factory)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())
        socket.receive(deltaEvent("item_1", "partial only"))

        val result = async { session.commit() }
        advanceTimeBy(OPENAI_TEST_COMMIT_TIMEOUT_MS + 1)
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is NetworkError.Timeout)
        assertTrue("a timed-out stream drops queued audio", socket.cancelled)
        assertNull(socket.closeCode)
    }

    @Test
    fun transientFailureBeforeCommit_reconnectsOnce_andReplaysAudioAfterSessionUpdated() = runTest {
        val session = openAiSession(factory)
        session.start()
        val first = factory.sockets.single()
        first.open()
        first.receive(gaSessionUpdated())
        val chunk = byteArrayOf(9, 9)
        session.sendAudio(chunk)

        first.fail(IOException("reset"))
        assertEquals(2, factory.sockets.size)
        val second = factory.sockets[1]
        second.open()
        assertEquals(listOf("session.update"), second.types())
        second.receive(gaSessionUpdated())
        assertEquals(listOf("session.update", "input_audio_buffer.append"), second.types())
        assertEquals(chunk.toByteString().base64(), second.json[1].getString("audio"))

        // Late callbacks of the replaced socket are ignored.
        first.receive(completedEvent("x", "ghost"))

        second.fail(IOException("reset again"))
        assertEquals(2, factory.sockets.size)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Offline)
    }

    @Test
    fun failureAfterCommit_neverReconnects() = runTest {
        val session = openAiSession(factory)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())
        val result = async { session.commit() }
        runCurrent()

        socket.fail(IOException("reset"))
        runCurrent()

        assertEquals(1, factory.sockets.size)
        assertTrue(result.await().exceptionOrNull() is NetworkError.Offline)
    }

    @Test
    fun handshakeUnauthorized_isNotRetried() = runTest {
        val session = openAiSession(factory)
        session.start()
        factory.sockets.single().fail(IOException("upgrade failed"), code = 401)

        assertEquals(1, factory.sockets.size)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Unauthorized)
    }

    @Test
    fun gatewayWithoutRealtime_reportsHttpStatus() = runTest {
        val session = openAiSession(factory, type = ProviderType.CUSTOM)
        session.start()
        factory.sockets.single().fail(IOException("upgrade failed"), code = 404)

        val error = session.commit().exceptionOrNull()
        assertTrue(error is NetworkError.Http)
        assertEquals(404, (error as NetworkError.Http).status)
        assertEquals(1, factory.sockets.size)
    }

    @Test
    fun cancel_closesWithoutCommitting() = runTest {
        val session = openAiSession(factory)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())

        session.cancel()
        session.cancel()

        assertEquals(listOf("session.update"), socket.types())
        assertTrue(socket.cancelled)
        assertNull(socket.closeCode)
        assertTrue(session.commit().isFailure)
    }
}
