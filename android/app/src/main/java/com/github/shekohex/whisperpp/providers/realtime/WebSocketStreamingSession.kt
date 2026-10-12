package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.isTransportAllowed
import com.github.shekohex.whisperpp.core.network.parseRetryAfterMs
import com.github.shekohex.whisperpp.core.network.toNetworkError
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.Transcript
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Shared lifecycle of the cloud realtime STT providers (§4.5.1): opens the socket in [start], holds
 * audio until the socket (and, for dialects that need it, the server handshake) is ready and then
 * flushes it in order, turns the dialect's events into [StreamEvent]s, and in [commit] awaits the
 * dialect's done signal with a timeout. There is no reconnect: a lost socket fails the stream and
 * the caller falls back to batch. The provider-specific wire format lives in a [StreamingDialect].
 *
 * [sockets] is the shared client derived with a ping interval (OkHttp upgrades http(s) URLs).
 * [dialectFactory] runs in [start], so configuration errors surface there.
 */
class WebSocketStreamingSession internal constructor(
    private val sockets: WebSocket.Factory,
    private val request: TranscriptionRequest,
    private val dialectFactory: () -> StreamingDialect,
    private val postProcess: suspend (String, ScriptConversion) -> String = { text, _ -> text },
    private val commitTimeoutMs: Long = request.timeoutMs.coerceAtLeast(MIN_COMMIT_TIMEOUT_MS),
) : StreamingTranscriptionSession {
    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<StreamEvent> = _events.asSharedFlow()

    // Guards everything below; OkHttp calls the listener on its own threads.
    private val lock = Any()
    private val final = CompletableDeferred<Result<String>>()

    // Frames produced before the session is ready, in send order.
    private val pending = ArrayList<WsFrame>()
    private var dialect: StreamingDialect? = null
    private var host = ""
    private var socket: WebSocket? = null
    private var open = false
    private var ready = false
    private var commitRequested = false
    private var closed = false
    private var failure: NetworkError? = null
    private var keepAliveExecutor: ScheduledExecutorService? = null

    /** Throws [com.github.shekohex.whisperpp.domain.providers.ProviderConfigException] or [NetworkError] when the stream cannot be opened. */
    override suspend fun start() {
        val dialect = dialectFactory()
        val upgrade = dialect.upgradeRequest()
        // OkHttp only takes http(s) URLs and upgrades them itself; check the scheme it will use (J-01).
        val wsScheme = if (upgrade.url.isHttps) "wss" else "ws"
        if (!isTransportAllowed(wsScheme, upgrade.url.host)) throw NetworkError.InsecureTransport(upgrade.url.host)
        synchronized(lock) {
            if (closed || this.dialect != null) return
            this.dialect = dialect
            host = upgrade.url.host
            socket = sockets.newWebSocket(upgrade, Listener())
        }
    }

    override suspend fun sendAudio(pcm16: ByteArray) {
        synchronized(lock) {
            val dialect = dialect ?: return
            if (closed || commitRequested) return
            dispatchLocked(dialect.audioFrames(pcm16))
        }
    }

    override suspend fun commit(): Result<Transcript> {
        synchronized(lock) {
            failure?.let { return Result.failure(it) }
            val dialect = dialect
            if (closed || dialect == null) return Result.failure(NetworkError.InvalidResponse("realtime session not open"))
            commitRequested = true
            stopKeepAliveLocked()
            dispatchLocked(dialect.commitFrames())
        }
        var succeeded = false
        try {
            val raw = withTimeoutOrNull(commitTimeoutMs) { final.await() }
            if (raw == null) {
                val timeout = NetworkError.Timeout()
                emit(StreamEvent.Error(timeout))
                return Result.failure(timeout)
            }
            val text = raw.getOrElse { return Result.failure(it) }
            val processed = postProcess(text, request.scriptConversion)
            emit(StreamEvent.Final(processed))
            succeeded = true
            return Result.success(Transcript(processed))
        } finally {
            synchronized(lock) { closeLocked(graceful = succeeded) }
        }
    }

    override suspend fun cancel() {
        synchronized(lock) { closeLocked(graceful = false) }
    }

    /** Sends the dialect's keep-alive frame; driven by the keep-alive timer, callable from tests. */
    internal fun keepAliveTick() {
        synchronized(lock) {
            val keepAlive = dialect?.keepAlive ?: return
            if (!ready || closed || commitRequested) return
            sendLocked(keepAlive.frame)
        }
    }

    private fun dispatchLocked(frames: List<WsFrame>) {
        if (frames.isEmpty()) return
        if (ready) frames.forEach(::sendLocked) else pending += frames
    }

    private fun sendLocked(frame: WsFrame) {
        val ws = socket ?: return
        val accepted = when (frame) {
            is WsFrame.Text -> ws.send(frame.payload)
            is WsFrame.Binary -> ws.send(frame.payload.toByteString())
        }
        // A refused frame (queue full or socket going down) leaves a hole in the take.
        if (!accepted) failLocked(NetworkError.Offline())
    }

    private fun onOpenLocked() {
        val dialect = dialect ?: return
        open = true
        dialect.openFrames().forEach(::sendLocked)
        if (!dialect.awaitsReady) becomeReadyLocked()
    }

    private fun becomeReadyLocked() {
        val dialect = dialect ?: return
        if (ready || closed || !open) return
        ready = true
        dialect.readyFrames().forEach(::sendLocked)
        val buffered = pending.toList()
        pending.clear()
        buffered.forEach(::sendLocked)
        emit(StreamEvent.Connected)
        if (!commitRequested) startKeepAliveLocked(dialect.keepAlive)
    }

    private fun onMessageLocked(text: String) {
        val dialect = dialect ?: return
        if (closed || final.isCompleted) return
        for (event in dialect.onMessage(text)) {
            handleLocked(event)
            if (closed || final.isCompleted) return
        }
    }

    private fun handleLocked(event: DialectEvent) {
        when (event) {
            DialectEvent.Ready -> becomeReadyLocked()
            is DialectEvent.Partial -> if (event.text.isNotBlank()) emit(StreamEvent.Partial(event.text))
            is DialectEvent.Done ->
                // A server that ends the session on its own leaves the rest of the take untranscribed.
                if (commitRequested) {
                    final.complete(Result.success(event.text))
                } else {
                    failLocked(NetworkError.InvalidResponse("realtime stream ended before commit"))
                }
            is DialectEvent.Failed -> failLocked(event.error)
        }
    }

    private fun onRemoteClosedLocked() {
        if (closed || final.isCompleted) return
        val done = if (commitRequested) dialect?.onRemoteClose() else null
        if (done != null) handleLocked(done) else failLocked(NetworkError.Offline())
    }

    private fun failLocked(error: NetworkError) {
        if (closed) return
        failure = error
        final.complete(Result.failure(error))
        emit(StreamEvent.Error(error))
        closeLocked(graceful = false)
    }

    /**
     * [graceful] only after a successful final: `close()` still flushes queued audio, so cancel,
     * timeout and failure use `cancel()`, which drops it.
     */
    private fun closeLocked(graceful: Boolean) {
        if (closed) return
        closed = true
        open = false
        ready = false
        if (graceful) socket?.close(NORMAL_CLOSURE, null) else socket?.cancel()
        socket = null
        stopKeepAliveLocked()
        pending.clear()
        final.complete(Result.failure(NetworkError.InvalidResponse("realtime session closed")))
        emit(StreamEvent.Closed)
    }

    private fun startKeepAliveLocked(keepAlive: StreamingDialect.KeepAlive?) {
        if (keepAlive == null || keepAliveExecutor != null) return
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "realtime-keepalive").apply { isDaemon = true }
        }
        keepAliveExecutor = executor
        executor.scheduleWithFixedDelay(::keepAliveTick, keepAlive.intervalMs, keepAlive.intervalMs, TimeUnit.MILLISECONDS)
    }

    private fun stopKeepAliveLocked() {
        keepAliveExecutor?.shutdownNow()
        keepAliveExecutor = null
    }

    private fun transportError(t: Throwable, response: Response?): NetworkError {
        val code = response?.code
        return when {
            code == 401 || code == 403 -> NetworkError.Unauthorized(code, host)
            code == 429 -> NetworkError.RateLimited(host, parseRetryAfterMs(response?.header("Retry-After")))
            code != null && code != SWITCHING_PROTOCOLS -> NetworkError.Http(code, host, null)
            t is IOException -> t.toNetworkError()
            else -> NetworkError.InvalidResponse("realtime transport", t)
        }
    }

    private fun emit(event: StreamEvent) {
        _events.tryEmit(event)
    }

    /** One listener per socket; callbacks of a replaced or closed socket are ignored. */
    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) { if (webSocket === socket) onOpenLocked() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            synchronized(lock) { if (webSocket === socket) onMessageLocked(text) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            synchronized(lock) { if (webSocket === socket) onRemoteClosedLocked() }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronized(lock) {
                if (webSocket === socket && !final.isCompleted) failLocked(transportError(t, response))
            }
        }
    }

    companion object {
        const val PING_INTERVAL_SECONDS = 15L
        const val MIN_COMMIT_TIMEOUT_MS = 5_000L
        private const val NORMAL_CLOSURE = 1000
        private const val SWITCHING_PROTOCOLS = 101
    }
}
