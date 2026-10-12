package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MistralDialectTest {
    private val factory = FakeWsFactory()

    private fun session(model: String = "voxtral-mini-latest", language: String? = "ar") = testSession(
        factory,
        ProviderType.MISTRAL,
        { MistralDialect(streamingConfig("https://api.mistral.ai/v1", model, language)) },
    )

    private val created = """{"type":"session.created","session":{"id":"s"}}"""

    private fun delta(text: String) = """{"type":"transcription.text.delta","text":"$text"}"""

    @Test
    fun upgradeRequest_mapsUiModelToRealtimeModel_andSendsBearerKey() = runTest {
        session().start()

        val request = factory.sockets.single().request()
        assertEquals("/v1/audio/transcriptions/realtime", request.url.encodedPath)
        assertEquals("voxtral-mini-transcribe-realtime-2602", request.url.queryParameter("model"))
        assertNull("language is not sent", request.url.queryParameter("language"))
        assertEquals("Bearer test-key", request.header("Authorization"))
    }

    @Test
    fun explicitRealtimeModelId_isKept() = runTest {
        session(model = "voxtral-mini-transcribe-realtime-2602").start()
        assertEquals(
            "voxtral-mini-transcribe-realtime-2602",
            factory.sockets.single().request().url.queryParameter("model"),
        )
    }

    @Test
    fun sessionUpdate_followsSessionCreated_andPrecedesBufferedAudio() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        session.sendAudio(byteArrayOf(1, 2))
        socket.open()
        assertTrue(socket.frames.isEmpty())

        socket.receive(created)
        runCurrent()

        val frames = socket.json
        assertEquals("session.update", frames[0].getString("type"))
        val format = frames[0].getJSONObject("session").getJSONObject("audio_format")
        assertEquals("pcm_s16le", format.getString("encoding"))
        assertEquals(16000, format.getInt("sample_rate"))
        assertEquals("input_audio.append", frames[1].getString("type"))
        assertEquals(byteArrayOf(1, 2).toByteString().base64(), frames[1].getString("audio"))
        assertEquals(listOf(StreamEvent.Connected), events)
    }

    @Test
    fun deltas_accumulateIntoCumulativePartials_andDoneIsFinal() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(created)

        socket.receive(delta("Hello"))
        socket.receive(delta(" wor"))
        socket.receive(delta("ld"))
        runCurrent()
        assertEquals(listOf("Hello", "Hello wor", "Hello world"), events.partials())

        val result = async { session.commit() }
        runCurrent()
        assertEquals("input_audio.end", socket.json.last().getString("type"))
        socket.receive("""{"type":"transcription.done","text":"ignored when deltas exist"}""")
        runCurrent()

        assertEquals("Hello world", result.await().getOrThrow().text)
        assertEquals(1000, socket.closeCode)
    }

    @Test
    fun done_fallsBackToItsOwnTextWithoutDeltas() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(created)

        val result = async { session.commit() }
        runCurrent()
        socket.receive("""{"type":"transcription.done","text":"whole text"}""")
        runCurrent()

        assertEquals("whole text", result.await().getOrThrow().text)
    }

    @Test
    fun errorEvent_failsTheStream() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"type":"error","error":{"message":"bad key text","code":401}}""")
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Unauthorized })
        assertTrue(socket.cancelled)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Unauthorized)
    }

    @Test
    fun genericErrorEvent_mapsToInvalidResponse_withoutEchoingTheMessage() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"type":"error","error":{"message":"secret transcript text"}}""")

        val error = session.commit().exceptionOrNull()
        assertTrue(error is NetworkError.InvalidResponse)
        assertTrue(!error!!.message.orEmpty().contains("secret"))
    }
}
