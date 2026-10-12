package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gateways that only speak the beta schema: GA is tried first, then beta once. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiRealtimeSchemaFallbackTest {
    private val factory = FakeWsFactory()

    @Test
    fun gaError_beforeSessionUpdated_retriesOnceWithTheBetaSchema_andReplaysAudio() = runTest {
        val session = openAiSession(factory)
        val events = mutableListOf<StreamEvent>()
        backgroundScope.collectInto(session.events, events)
        runCurrent()
        session.start()
        val ga = factory.sockets.single()
        assertNull(ga.request().header("OpenAI-Beta"))
        val chunk = byteArrayOf(1, 2, 3, 4)
        session.sendAudio(chunk)
        ga.open()

        ga.receive(UNKNOWN_PARAMETER_ERROR)
        runCurrent()

        assertTrue("the rejected GA socket is dropped", ga.cancelled)
        assertEquals(2, factory.sockets.size)
        val beta = factory.sockets[1]
        assertEquals("realtime=v1", beta.request().header("OpenAI-Beta"))
        assertEquals("Bearer sk-test-key", beta.request().header("Authorization"))
        assertEquals(ga.request().url, beta.request().url)
        assertFalse("no failure surfaces while the retry is pending", events.any { it is StreamEvent.Error })

        beta.open()
        val update = beta.json.single()
        assertEquals("transcription_session.update", update.getString("type"))
        val session1 = update.getJSONObject("session")
        assertEquals("pcm16", session1.getString("input_audio_format"))
        assertEquals("gpt-4o-transcribe", session1.getJSONObject("input_audio_transcription").getString("model"))
        assertEquals("server_vad", session1.getJSONObject("turn_detection").getString("type"))
        assertFalse(session1.has("audio"))

        // Audio is held until the beta session is confirmed, then replayed.
        assertEquals(listOf("transcription_session.update"), beta.types())
        beta.receive(betaSessionUpdated())
        runCurrent()
        assertEquals(listOf("transcription_session.update", "input_audio_buffer.append"), beta.types())
        assertEquals(chunk.toByteString().base64(), beta.json[1].getString("audio"))
        assertEquals(StreamEvent.Connected, events.single { it is StreamEvent.Connected })
    }

    @Test
    fun betaStopSequence_usesTranscriptionSessionUpdate() = runTest {
        val session = openAiSession(factory)
        session.start()
        factory.sockets.single().open()
        factory.sockets.single().receive(UNKNOWN_PARAMETER_ERROR)
        val beta = factory.sockets[1]
        beta.open()
        beta.receive(betaSessionUpdated())
        beta.receive(speechStartedEvent("item_1"))
        session.sendAudio(ByteArray(OPENAI_SPEECH_BYTES))

        val result = async { session.commit() }
        runCurrent()
        val disable = beta.json.last()
        assertEquals("transcription_session.update", disable.getString("type"))
        assertTrue(disable.getJSONObject("session").isNull("turn_detection"))

        beta.receive(betaSessionUpdated(vadOn = false))
        runCurrent()
        assertEquals("input_audio_buffer.commit", beta.types().last())
        beta.receive(committedEvent("item_1"))
        beta.receive(CLEARED_EVENT)
        beta.receive(completedEvent("item_1", "Beta works."))
        runCurrent()

        assertEquals("Beta works.", result.await().getOrThrow().text)
    }

    @Test
    fun betaError_afterTheFallback_failsWithoutLooping() = runTest {
        val session = openAiSession(factory)
        session.start()
        factory.sockets.single().open()
        factory.sockets.single().receive(UNKNOWN_PARAMETER_ERROR)
        val beta = factory.sockets[1]
        beta.open()

        beta.receive(UNKNOWN_PARAMETER_ERROR)
        runCurrent()

        assertEquals(2, factory.sockets.size)
        assertTrue(beta.cancelled)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.InvalidResponse)
    }

    @Test
    fun authError_isNotRetriedWithBeta() = runTest {
        val session = openAiSession(factory)
        session.start()
        val ga = factory.sockets.single()
        ga.open()

        ga.receive("""{"type":"error","error":{"type":"authentication_error","code":"invalid_api_key","message":"no"}}""")
        runCurrent()

        assertEquals(1, factory.sockets.size)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Unauthorized)
    }

    @Test
    fun fallbackDisabled_failsOnTheGaError() = runTest {
        val session = openAiSession(factory, betaFallback = false)
        session.start()
        val ga = factory.sockets.single()
        ga.open()

        ga.receive(UNKNOWN_PARAMETER_ERROR)
        runCurrent()

        assertEquals(1, factory.sockets.size)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.InvalidResponse)
    }

    @Test
    fun errorAfterSessionUpdated_isNeverARetry() = runTest {
        val session = openAiSession(factory)
        session.start()
        val ga = factory.sockets.single()
        ga.open()
        ga.receive(gaSessionUpdated())

        ga.receive(UNKNOWN_PARAMETER_ERROR)
        runCurrent()

        assertEquals(1, factory.sockets.size)
        assertTrue(session.commit().isFailure)
    }

    @Test
    fun betaSchemaCanBeForcedAtStart() = runTest {
        val session = openAiSession(factory, schema = RealtimeSchema.BETA)
        session.start()

        val socket = factory.sockets.single()
        assertEquals("realtime=v1", socket.request().header("OpenAI-Beta"))
        socket.open()
        assertEquals(listOf("transcription_session.update"), socket.types())
        // The beta session confirmation of a beta-only gateway is accepted as is.
        socket.receive(betaSessionUpdated())
        session.sendAudio(byteArrayOf(1, 2))
        assertEquals("input_audio_buffer.append", socket.types().last())
    }
}
