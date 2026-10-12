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
class CartesiaDialectTest {
    private val factory = FakeWsFactory()

    private fun session() = testSession(
        factory,
        ProviderType.CARTESIA,
        { CartesiaDialect(streamingConfig("https://api.cartesia.ai", "ink-2", language = "ar", vocabulary = listOf("x"))) },
    )

    private fun turn(type: String, text: String) = """{"type":"$type","transcript":"$text"}"""

    @Test
    fun upgradeRequest_hasParameters_andApiKeyHeader() = runTest {
        session().start()

        val request = factory.sockets.single().request()
        assertEquals("/stt/turns/websocket", request.url.encodedPath)
        assertEquals("ink-2", request.url.queryParameter("model"))
        assertEquals("pcm_s16le", request.url.queryParameter("encoding"))
        assertEquals("16000", request.url.queryParameter("sample_rate"))
        assertEquals("2026-03-01", request.url.queryParameter("cartesia_version"))
        assertNull("English only: language is not sent", request.url.queryParameter("language"))
        assertEquals("test-key", request.header("X-API-Key"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun audioIsBinary_andSentRightAfterOpen() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        session.sendAudio(byteArrayOf(1))
        socket.open()
        session.sendAudio(byteArrayOf(2, 3))
        runCurrent()

        assertEquals(listOf(listOf<Byte>(1), listOf<Byte>(2, 3)), socket.binaries.map { it.toList() })
        assertTrue(socket.texts.isEmpty())
        assertEquals(listOf(StreamEvent.Connected), events)
    }

    @Test
    fun turns_buildPartials_closeFrameCommits_andSocketCloseIsTheFinal() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive("""{"type":"connected"}""")

        socket.receive(turn("turn.update", "hello"))
        socket.receive(turn("turn.eager_end", "hello there"))
        socket.receive(turn("turn.end", "Hello there."))
        socket.receive(turn("turn.update", "general"))
        runCurrent()
        assertEquals(listOf("hello", "hello there", "Hello there.", "Hello there. general"), events.partials())

        val result = async { session.commit() }
        runCurrent()
        assertEquals("""{"type":"close"}""", socket.texts.last())
        socket.receive(turn("turn.end", "General Kenobi."))
        socket.remoteClose()
        runCurrent()

        assertEquals("Hello there. General Kenobi.", result.await().getOrThrow().text)
    }

    @Test
    fun closeWithAnUnendedTurn_keepsItsWords() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(turn("turn.update", "last words"))

        val result = async { session.commit() }
        runCurrent()
        socket.remoteClose()
        runCurrent()

        assertEquals("last words", result.await().getOrThrow().text)
    }

    @Test
    fun errorMessage_failsTheStream() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.receive("""{"type":"error","title":"unauthorized","message":"bad key"}""")
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Unauthorized })
        assertTrue(socket.cancelled)
    }
}
