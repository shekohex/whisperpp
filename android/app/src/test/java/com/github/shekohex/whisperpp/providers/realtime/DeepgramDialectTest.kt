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
class DeepgramDialectTest {
    private val factory = FakeWsFactory()

    private fun session(
        model: String = "nova-3",
        language: String? = null,
        vocabulary: List<String> = emptyList(),
    ) = testSession(
        factory,
        ProviderType.DEEPGRAM,
        { DeepgramDialect(streamingConfig("https://api.deepgram.com/v1", model, language, vocabulary)) },
    )

    private fun results(text: String, isFinal: Boolean = false, speechFinal: Boolean = false) =
        """{"type":"Results","is_final":$isFinal,"speech_final":$speechFinal,"channel":{"alternatives":[{"transcript":"$text"}]}}"""

    @Test
    fun upgradeRequest_hasUrlParametersAndTokenAuth() = runTest {
        session(vocabulary = listOf("Kotlin", "Whisper++")).start()

        val request = factory.sockets.single().request()
        assertEquals("api.deepgram.com", request.url.host)
        assertEquals("/v1/listen", request.url.encodedPath)
        val query = request.url
        assertEquals("nova-3", query.queryParameter("model"))
        assertEquals("linear16", query.queryParameter("encoding"))
        assertEquals("16000", query.queryParameter("sample_rate"))
        assertEquals("1", query.queryParameter("channels"))
        assertEquals("true", query.queryParameter("smart_format"))
        assertEquals("true", query.queryParameter("interim_results"))
        assertEquals("true", query.queryParameter("mip_opt_out"))
        assertEquals("multi", query.queryParameter("language"))
        assertEquals(listOf("Kotlin", "Whisper++"), query.queryParameterValues("keyterm"))
        assertEquals("Token test-key", request.header("Authorization"))
    }

    @Test
    fun language_isSentWhenSet_andOmittedForAutoOnOtherModels() = runTest {
        session(language = "ar").start()
        assertEquals("ar", factory.sockets.last().request().url.queryParameter("language"))

        session(model = "nova-2", language = null, vocabulary = listOf("x")).start()
        val url = factory.sockets.last().request().url
        assertNull(url.queryParameter("language"))
        assertTrue("keyterm is Nova-3 only", url.queryParameterValues("keyterm").isEmpty())
    }

    @Test
    fun keyterms_areCappedAtOneHundred() = runTest {
        session(vocabulary = (1..150).map { "term$it" }).start()
        assertEquals(100, factory.sockets.single().request().url.queryParameterValues("keyterm").size)
    }

    @Test
    fun audio_isSentAsRawBinaryFrames() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        session.sendAudio(byteArrayOf(9, 8, 7))

        assertEquals(listOf(listOf<Byte>(9, 8, 7)), socket.binaries.map { it.toList() })
        assertTrue(socket.texts.isEmpty())
    }

    @Test
    fun results_buildCumulativePartials_andFinalIsTheCommittedJoin() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive(results("hello"))
        socket.receive(results("hello there", isFinal = true))
        socket.receive(results("general"))
        socket.receive(results("general kenobi", speechFinal = true))
        socket.receive(results("")) // empty finals carry nothing
        runCurrent()
        assertEquals(listOf("hello", "hello there", "hello there general", "hello there general kenobi"), events.partials())

        val result = async { session.commit() }
        runCurrent()
        assertEquals("""{"type":"CloseStream"}""", socket.texts.last())
        socket.receive("""{"type":"Metadata","request_id":"r"}""")
        runCurrent()

        assertEquals("hello there general kenobi", result.await().getOrThrow().text)
        assertEquals(1000, socket.closeCode)
        assertEquals(StreamEvent.Final("hello there general kenobi"), events.filterIsInstance<StreamEvent.Final>().single())
        assertEquals(StreamEvent.Closed, events.last())
    }

    @Test
    fun speechStartedAndUtteranceEnd_areIgnored() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"type":"SpeechStarted"}""")
        socket.receive("""{"type":"UtteranceEnd"}""")
        runCurrent()

        assertTrue(events.partials().isEmpty())
        assertTrue(events.none { it is StreamEvent.Error })
    }

    @Test
    fun errorMessage_failsTheStream() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"type":"Error","variant":"rate_limited"}""")
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.RateLimited })
        assertTrue(socket.cancelled)
        assertTrue(session.commit().exceptionOrNull() is NetworkError.RateLimited)
    }
}
