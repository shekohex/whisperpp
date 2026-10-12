package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Test connection" for a realtime model: opens the stream, waits until the server confirmed the
 * session (or failed it), and closes it again without sending any audio.
 */
internal object RealtimeHandshakeProbe {
    /** A little longer than the session's own connect timeout, which normally reports first. */
    const val TIMEOUT_MS = OpenAiRealtimeSession.CONNECT_TIMEOUT_MS + 2_000L

    /** What went wrong, in terms a user can act on. */
    enum class Problem { UNAUTHORIZED, NO_ENDPOINT, TIMEOUT, OTHER }

    private val NO_ENDPOINT_STATUSES = setOf(404, 405, 426, 501)

    suspend fun probe(session: StreamingTranscriptionSession, timeoutMs: Long = TIMEOUT_MS): Result<Unit> = coroutineScope {
        // Subscribe before start(): the event flow does not replay.
        val outcome = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(timeoutMs) {
                session.events.first { it is StreamEvent.Connected || it is StreamEvent.Error || it is StreamEvent.Closed }
            }
        }
        try {
            session.start()
            when (val event = outcome.await()) {
                StreamEvent.Connected -> Result.success(Unit)
                is StreamEvent.Error -> Result.failure(event.error)
                StreamEvent.Closed -> Result.failure(NetworkError.InvalidResponse("realtime session closed"))
                else -> Result.failure(NetworkError.Timeout())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            outcome.cancel()
            Result.failure(e)
        } finally {
            session.cancel()
        }
    }

    /** 404/405/426/501 on the upgrade mean the server has no WebSocket endpoint at that path. */
    fun classify(error: Throwable): Problem = when (error) {
        is NetworkError.Unauthorized -> Problem.UNAUTHORIZED
        is NetworkError.Http -> if (error.status in NO_ENDPOINT_STATUSES) Problem.NO_ENDPOINT else Problem.OTHER
        is NetworkError.Timeout -> Problem.TIMEOUT
        else -> Problem.OTHER
    }
}
