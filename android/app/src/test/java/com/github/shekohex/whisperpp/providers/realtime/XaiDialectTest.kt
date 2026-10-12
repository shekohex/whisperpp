package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XaiDialectTest {
    private val factory = FakeWsFactory()

    private fun session(language: String? = null, vocabulary: List<String> = emptyList()) = testSession(
        factory,
        ProviderType.XAI,
        { XaiDialect(streamingConfig("https://api.x.ai/v1", "grok-voice-transcribe-2.0", language, vocabulary)) },
    )

    private val created = """{"type":"transcript.created","id":"t"}"""

    private fun partial(text: String, isFinal: Boolean = false, speechFinal: Boolean = false) =
        """{"type":"transcript.partial","text":"$text","is_final":$isFinal,"speech_final":$speechFinal}"""

    @Test
    fun upgradeRequest_hasParameters_andBearerAuth() = runTest {
        session(language = "ar", vocabulary = listOf("Kotlin", "x".repeat(51), "Whisper")).start()

        val request = factory.sockets.single().request()
        assertEquals("/v1/stt", request.url.encodedPath)
        val url = request.url
        assertEquals("16000", url.queryParameter("sample_rate"))
        assertEquals("pcm", url.queryParameter("encoding"))
        assertEquals("true", url.queryParameter("interim_results"))
        assertEquals("100", url.queryParameter("endpointing"))
        assertEquals("0.5", url.queryParameter("smart_turn"))
        assertEquals("ar", url.queryParameter("language"))
        assertEquals(listOf("Kotlin", "Whisper"), url.queryParameterValues("keyterm"))
        assertEquals("Bearer test-key", request.header("Authorization"))
    }

    @Test
    fun autoLanguage_omitsLanguage() = runTest {
        session().start()
        assertNull(factory.sockets.single().request().url.queryParameter("language"))
    }

    @Test
    fun audioIsSentIn3200ByteFrames_afterTranscriptCreated_andRemainderAtCommit() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        session.sendAudio(ByteArray(2000) { 1 })
        session.sendAudio(ByteArray(2000) { 2 })
        assertTrue(socket.frames.isEmpty())

        socket.receive(created)
        runCurrent()
        assertEquals(listOf(3200), socket.binaries.map { it.size })
        assertEquals(listOf(StreamEvent.Connected), events)

        val result = async { session.commit() }
        runCurrent()
        assertEquals(listOf(3200, 800), socket.binaries.map { it.size })
        assertEquals("audio.done", socket.json.last().getString("type"))

        socket.receive("""{"type":"transcript.done","text":""}""")
        runCurrent()
        result.await()
    }

    @Test
    fun partials_lockOnIsFinal_commitOnSpeechFinal_andDoneReturnsEverything() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(created)

        socket.receive(partial("hel"))
        socket.receive(partial("hello", isFinal = true))
        socket.receive(partial("there"))
        socket.receive(partial("there friend", isFinal = true, speechFinal = true))
        socket.receive(partial("next"))
        runCurrent()
        assertEquals(listOf("hel", "hello", "hello there", "hello there friend", "hello there friend next"), events.partials())

        val result = async { session.commit() }
        runCurrent()
        socket.receive(partial("next one", isFinal = true))
        socket.receive("""{"type":"transcript.done","text":""}""")
        runCurrent()

        assertEquals("hello there friend next one", result.await().getOrThrow().text)
        assertEquals(1000, socket.closeCode)
    }

    @Test
    fun errorMessage_failsTheStream() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"type":"error","code":"rate_limited","message":"slow down"}""")
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.RateLimited })
        assertTrue(socket.cancelled)
    }
}
