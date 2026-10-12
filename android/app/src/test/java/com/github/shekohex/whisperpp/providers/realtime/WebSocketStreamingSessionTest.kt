package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Lifecycle shared by every cloud dialect, exercised with the Deepgram dialect. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketStreamingSessionTest {
    private val factory = FakeWsFactory()

    private fun session(base: String = "https://api.deepgram.com/v1", timeoutMs: Long = TEST_COMMIT_TIMEOUT_MS) =
        testSession(factory, ProviderType.DEEPGRAM, { DeepgramDialect(streamingConfig(base, "nova-3")) }, timeoutMs)

    private fun results(text: String, final: Boolean = true) =
        """{"type":"Results","is_final":$final,"speech_final":false,"channel":{"alternatives":[{"transcript":"$text"}]}}"""

    @Test
    fun audioBeforeOpen_isBufferedAndFlushedInOrderOnOpen() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()

        session.sendAudio(byteArrayOf(1, 2))
        session.sendAudio(byteArrayOf(3))
        assertTrue(socket.frames.isEmpty())

        socket.open()
        session.sendAudio(byteArrayOf(4))
        runCurrent()

        assertEquals(listOf(listOf<Byte>(1, 2), listOf<Byte>(3), listOf<Byte>(4)), socket.binaries.map { it.toList() })
        assertEquals(listOf(StreamEvent.Connected), events)
    }

    @Test
    fun cleartextToPublicHost_isRejected_butLocalHostIsAllowed() = runTest {
        try {
            session(base = "http://example.com/v1").start()
            fail("expected InsecureTransport")
        } catch (e: NetworkError.InsecureTransport) {
            assertEquals("example.com", e.host)
        }
        assertTrue(factory.sockets.isEmpty())

        session(base = "http://192.168.1.20:8000/v1").start()
        assertEquals("192.168.1.20", factory.sockets.single().request().url.host)
    }

    @Test
    fun commit_withoutFinal_timesOut_andCancelsTheSocket() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(results("partial only", final = false))

        val result = async { session.commit() }
        advanceTimeBy(TEST_COMMIT_TIMEOUT_MS + 1)
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is NetworkError.Timeout)
        assertTrue("a timed-out stream drops queued audio", socket.cancelled)
        assertNull(socket.closeCode)
        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Timeout })
        assertEquals(StreamEvent.Closed, events.last())
    }

    @Test
    fun commitTimeout_defaultsToAtLeastFiveSeconds() = runTest {
        val provider = ServiceProvider(
            name = "p", type = ProviderType.DEEPGRAM, endpoint = "https://api.deepgram.com/v1", models = emptyList(),
        )
        val request = TranscriptionRequest(
            provider = provider, modelId = "nova-3", timeoutMs = 1_000,
        )
        val session = WebSocketStreamingSession(
            factory, request, { DeepgramDialect(streamingConfig("https://api.deepgram.com/v1", "nova-3")) },
        )
        session.start()
        factory.sockets.single().open()

        val result = async { session.commit() }
        advanceTimeBy(4_900)
        runCurrent()
        assertTrue("still waiting before 5 s", result.isActive)
        advanceTimeBy(200)
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is NetworkError.Timeout)
    }

    private class DoneDialect : StreamingDialect {
        override fun upgradeRequest(): Request = Request.Builder().url("https://api.example.com/ws").build()

        override fun audioFrames(pcm16: ByteArray): List<WsFrame> = emptyList()

        override fun commitFrames(): List<WsFrame> = emptyList()

        override fun onMessage(text: String): List<DialectEvent> = listOf(DialectEvent.Done("done"))
    }

    @Test
    fun doneBeforeCommit_failsTheStream_butAfterCommitCompletesIt() = runTest {
        val early = testSession(factory, ProviderType.DEEPGRAM, { DoneDialect() })
        val events = collectEvents(early)
        early.start()
        factory.sockets.single().apply {
            open()
            receive("anything")
        }
        runCurrent()
        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.InvalidResponse })
        assertTrue(early.commit().isFailure)

        val late = testSession(factory, ProviderType.DEEPGRAM, { DoneDialect() })
        late.start()
        val socket = factory.sockets.last()
        socket.open()
        val result = async { late.commit() }
        runCurrent()
        socket.receive("anything")
        runCurrent()
        assertEquals("done", result.await().getOrThrow().text)
        assertEquals(1000, socket.closeCode)
    }

    @Test
    fun metadataBeforeCommit_isNotADoneSignal() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive("""{"type":"Metadata"}""")
        runCurrent()
        assertTrue(events.none { it is StreamEvent.Error })
    }

    @Test
    fun remoteCloseBeforeCommit_failsWithOffline_andCommitReturnsTheFailure() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        socket.remoteClose()
        runCurrent()

        assertTrue(events.any { it is StreamEvent.Error && it.error is NetworkError.Offline })
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Offline)
        assertTrue(socket.cancelled || socket.closeCode == null)
    }

    @Test
    fun remoteCloseAfterCommit_withoutDone_returnsTheAccumulatedText() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(results("hello world"))

        val result = async { session.commit() }
        runCurrent()
        socket.remoteClose()
        runCurrent()

        assertEquals("hello world", result.await().getOrThrow().text)
    }

    @Test
    fun transportFailure_mapsHttpStatusToTypedError() = runTest {
        for ((status, check) in listOf<Pair<Int, (Throwable?) -> Boolean>>(
            401 to { it is NetworkError.Unauthorized },
            403 to { it is NetworkError.Unauthorized },
            429 to { it is NetworkError.RateLimited },
            500 to { it is NetworkError.Http && it.status == 500 },
        )) {
            val local = FakeWsFactory()
            val session = testSession(local, ProviderType.DEEPGRAM, { DeepgramDialect(streamingConfig("https://api.deepgram.com/v1", "nova-3")) })
            session.start()

            local.sockets.single().fail(IOException("handshake"), code = status)

            assertTrue("status $status", check(session.commit().exceptionOrNull()))
        }
    }

    @Test
    fun ioFailure_mapsToOffline() = runTest {
        val session = session()
        session.start()
        factory.sockets.single().fail(IOException("reset"))
        assertTrue(session.commit().exceptionOrNull() is NetworkError.Offline)
    }

    @Test
    fun refusedSend_failsTheStream() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()
        socket.acceptSends = false

        session.sendAudio(byteArrayOf(1))

        assertTrue(session.commit().exceptionOrNull() is NetworkError.Offline)
        assertTrue(socket.cancelled)
    }

    @Test
    fun cancel_isIdempotent_andAbortsTheSocket() = runTest {
        val session = session()
        val events = collectEvents(session)
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        session.cancel()
        session.cancel()
        runCurrent()

        assertTrue(socket.cancelled)
        assertEquals(1, events.count { it == StreamEvent.Closed })
        assertTrue(session.commit().isFailure)
    }

    @Test
    fun keepAlive_isSentWhileRecording_andStopsAtCommit() = runTest {
        val session = session()
        session.start()
        val socket = factory.sockets.single()
        socket.open()

        session.keepAliveTick()
        assertEquals(listOf("""{"type":"KeepAlive"}"""), socket.texts)

        val result = async { session.commit() }
        runCurrent()
        session.keepAliveTick()
        assertEquals(listOf("""{"type":"KeepAlive"}""", """{"type":"CloseStream"}"""), socket.texts)
        socket.receive("""{"type":"Metadata"}""")
        runCurrent()
        result.await()
    }

    @Test
    fun commit_beforeSessionIsOpen_returnsTheFailure() = runTest {
        val session = session()
        assertTrue(session.commit().isFailure)
    }
}
