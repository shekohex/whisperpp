package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeHandshakeProbeTest {
    private val factory = FakeWsFactory()

    @Test
    fun sessionUpdated_isSuccess_andTheSocketIsClosedWithoutAudio() = runTest {
        val result = async { RealtimeHandshakeProbe.probe(openAiSession(factory)) }
        runCurrent()
        val socket = factory.sockets.single()
        socket.open()
        socket.receive(gaSessionUpdated())
        runCurrent()

        assertTrue(result.await().isSuccess)
        assertTrue(socket.cancelled)
        assertEquals(listOf("session.update"), socket.types())
    }

    @Test
    fun betaOnlyGateway_isSuccessAfterTheFallback() = runTest {
        val result = async { RealtimeHandshakeProbe.probe(openAiSession(factory)) }
        runCurrent()
        factory.sockets.single().open()
        factory.sockets.single().receive(UNKNOWN_PARAMETER_ERROR)
        runCurrent()
        val beta = factory.sockets[1]
        beta.open()
        beta.receive(betaSessionUpdated())
        runCurrent()

        assertTrue(result.await().isSuccess)
    }

    @Test
    fun upgrade401_isUnauthorized() = runTest {
        val result = async { RealtimeHandshakeProbe.probe(openAiSession(factory)) }
        runCurrent()
        factory.sockets.single().fail(IOException("upgrade failed"), code = 401)
        runCurrent()

        val error = result.await().exceptionOrNull()!!
        assertEquals(RealtimeHandshakeProbe.Problem.UNAUTHORIZED, RealtimeHandshakeProbe.classify(error))
    }

    @Test
    fun upgrade404And426_meanTheGatewayHasNoRealtimeEndpoint() = runTest {
        for (status in listOf(404, 405, 426)) {
            val result = async { RealtimeHandshakeProbe.probe(openAiSession(factory)) }
            runCurrent()
            factory.sockets.last().fail(IOException("upgrade failed"), code = status)
            runCurrent()

            val error = result.await().exceptionOrNull()!!
            assertEquals("$status", RealtimeHandshakeProbe.Problem.NO_ENDPOINT, RealtimeHandshakeProbe.classify(error))
            assertEquals(status, (error as NetworkError.Http).status)
        }
    }

    @Test
    fun otherHttpStatus_isNotReportedAsMissingEndpoint() {
        assertEquals(
            RealtimeHandshakeProbe.Problem.OTHER,
            RealtimeHandshakeProbe.classify(NetworkError.Http(500, "gw.example.com", null)),
        )
    }

    @Test
    fun silentGateway_timesOut() = runTest {
        val result = async { RealtimeHandshakeProbe.probe(openAiSession(factory), timeoutMs = 1_000) }
        runCurrent()
        factory.sockets.single().open()
        advanceTimeBy(1_001)
        runCurrent()

        val outcome = result.await()
        assertTrue(outcome.exceptionOrNull() is NetworkError.Timeout)
        assertEquals(RealtimeHandshakeProbe.Problem.TIMEOUT, RealtimeHandshakeProbe.classify(outcome.exceptionOrNull()!!))
        assertTrue(factory.sockets.single().cancelled)
    }

    @Test
    fun startFailure_isReturned_notThrown() = runTest {
        val session = openAiSession(factory, endpoint = "http://example.com/v1")

        val error = RealtimeHandshakeProbe.probe(session).exceptionOrNull()

        assertTrue(error is NetworkError.InsecureTransport)
        assertFalse(factory.sockets.isNotEmpty())
    }
}
