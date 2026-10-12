package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.isTransportAllowed
import com.github.shekohex.whisperpp.core.network.parseRetryAfterMs
import com.github.shekohex.whisperpp.core.network.toNetworkError
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.Transcript
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.apiKeyOrThrow
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * OpenAI Realtime transcription (`/v1/realtime?intent=transcription`, §4.5.1). Server VAD is off:
 * the whole take is one input buffer that [commit] finalizes. Audio is resampled to the 24 kHz the
 * API expects and kept in memory so one reconnect before [commit] can replay it; after the commit
 * a broken stream fails instead, and the caller falls back to batch.
 *
 * [sockets] is the shared client derived with a ping interval (OkHttp upgrades the http(s) URL).
 */
class OpenAiRealtimeSession(
    private val sockets: WebSocket.Factory,
    private val provider: ServiceProvider,
    private val model: ModelConfig,
    private val credentials: Credentials,
    private val request: TranscriptionRequest,
    private val postProcess: suspend (String, ScriptConversion) -> String = { text, _ -> text },
    private val commitTimeoutMs: Long = request.timeoutMs.coerceAtLeast(MIN_COMMIT_TIMEOUT_MS),
    inputSampleRate: Int = AudioRecorder.DEFAULT_SAMPLE_RATE,
) : StreamingTranscriptionSession {
    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<StreamEvent> = _events.asSharedFlow()

    // Guards everything below; OkHttp calls the listener on its own threads.
    private val lock = Any()
    private val resampler = Pcm16Resampler(inputSampleRate, API_SAMPLE_RATE)
    private val final = CompletableDeferred<Result<String>>()
    private val partial = StringBuilder()
    private val sentAudio = ArrayList<ByteArray>()
    private var sentBytes = 0L
    private var replayable = true
    private var upgrade: Request? = null
    private var socket: WebSocket? = null
    private var open = false
    private var reconnectsLeft = 1
    private var commitRequested = false
    private var commitSent = false
    private var committedItemId: String? = null
    private var failure: NetworkError? = null
    private var closed = false

    /** Throws [com.github.shekohex.whisperpp.domain.providers.ProviderConfigException] or [NetworkError] when the stream cannot be opened. */
    override suspend fun start() {
        val upgradeRequest = buildUpgradeRequest()
        synchronized(lock) {
            if (closed || upgrade != null) return
            upgrade = upgradeRequest
            connectLocked()
        }
    }

    override suspend fun sendAudio(pcm16: ByteArray) {
        synchronized(lock) {
            if (closed || commitRequested) return
            val audio = resampler.process(pcm16)
            if (audio.isEmpty()) return
            rememberLocked(audio)
            if (open) socket?.send(appendMessage(audio))
        }
    }

    override suspend fun commit(): Result<Transcript> {
        synchronized(lock) {
            failure?.let { return Result.failure(it) }
            if (closed || upgrade == null) return Result.failure(NetworkError.InvalidResponse("realtime session not open"))
            commitRequested = true
            if (open) sendCommitLocked()
        }
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
            return Result.success(Transcript(processed))
        } finally {
            synchronized(lock) { closeLocked() }
        }
    }

    override suspend fun cancel() {
        synchronized(lock) { closeLocked() }
    }

    internal fun buildUpgradeRequest(): Request {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val url = realtimeUrl(provider.baseUrlOrThrow())
        // OkHttp only takes http(s) URLs and upgrades them itself; check the scheme it will use (J-01).
        val wsScheme = if (url.isHttps) "wss" else "ws"
        if (!isTransportAllowed(wsScheme, url.host)) throw NetworkError.InsecureTransport(url.host)
        return Request.Builder()
            .url(url)
            .header("OpenAI-Beta", "realtime=v1")
            .apply { if (apiKey != null) header("Authorization", "Bearer $apiKey") }
            .build()
    }

    private fun connectLocked() {
        val upgradeRequest = upgrade ?: return
        open = false
        partial.setLength(0)
        committedItemId = null
        socket = sockets.newWebSocket(upgradeRequest, Listener())
    }

    private fun onOpenLocked(ws: WebSocket) {
        ws.send(sessionUpdateMessage())
        // A reconnect starts with an empty server buffer: replay the take so far.
        for (chunk in sentAudio) ws.send(appendMessage(chunk))
        open = true
        emit(StreamEvent.Connected)
        if (commitRequested) sendCommitLocked()
    }

    private fun onMessageLocked(text: String) {
        val event = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return
        }
        when (event.optString("type")) {
            "conversation.item.input_audio_transcription.delta" -> {
                val delta = event.optString("delta")
                if (delta.isEmpty()) return
                partial.append(delta)
                emit(StreamEvent.Partial(partial.toString()))
            }
            "input_audio_buffer.committed" -> {
                if (commitSent) committedItemId = event.optString("item_id").ifEmpty { null }
            }
            "conversation.item.input_audio_transcription.completed" -> onCompletedLocked(event)
            "conversation.item.input_audio_transcription.failed" -> {
                if (commitSent) failLocked(NetworkError.InvalidResponse("realtime transcription failed"))
            }
            "error" -> failLocked(errorEventToNetworkError(event))
        }
    }

    // Only the transcript of the buffer we committed counts; anything earlier is stale.
    private fun onCompletedLocked(event: JSONObject) {
        if (!commitSent) return
        val itemId = event.optString("item_id")
        val expected = committedItemId
        if (expected != null && itemId.isNotEmpty() && itemId != expected) return
        final.complete(Result.success(event.optString("transcript").trim()))
    }

    private fun onTransportLostLocked(error: NetworkError) {
        if (closed) return
        if (!commitRequested && reconnectsLeft > 0 && replayable && error.isTransient()) {
            reconnectsLeft -= 1
            connectLocked()
            return
        }
        failLocked(error)
    }

    private fun failLocked(error: NetworkError) {
        if (closed) return
        failure = error
        final.complete(Result.failure(error))
        emit(StreamEvent.Error(error))
        closeLocked()
    }

    private fun closeLocked() {
        if (closed) return
        closed = true
        open = false
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        sentAudio.clear()
        final.complete(Result.failure(NetworkError.InvalidResponse("realtime session closed")))
        emit(StreamEvent.Closed)
    }

    private fun sendCommitLocked() {
        val ws = socket ?: return
        if (commitSent) return
        ws.send(JSONObject().put("type", "input_audio_buffer.commit").toString())
        commitSent = true
        partial.setLength(0)
    }

    private fun rememberLocked(audio: ByteArray) {
        if (!replayable) return
        sentBytes += audio.size
        if (sentBytes > MAX_REPLAY_BYTES) {
            // Too long to replay; a dropped connection now ends the stream (batch takes over).
            replayable = false
            sentAudio.clear()
        } else {
            sentAudio += audio
        }
    }

    private fun sessionUpdateMessage(): String {
        val transcription = JSONObject().put("model", model.id)
        val language = request.languageCode.trim()
        if (language.isNotEmpty() && language != "auto") transcription.put("language", language)
        if (request.prompt.isNotBlank()) transcription.put("prompt", request.prompt)
        val session = JSONObject()
            .put("input_audio_format", "pcm16")
            .put("input_audio_transcription", transcription)
            // We commit manually: the take is one buffer, matching the WAV used for fallback.
            .put("turn_detection", JSONObject.NULL)
        return JSONObject()
            .put("type", "transcription_session.update")
            .put("session", session)
            .toString()
    }

    private fun appendMessage(audio: ByteArray): String = JSONObject()
        .put("type", "input_audio_buffer.append")
        .put("audio", audio.toByteString().base64())
        .toString()

    private fun errorEventToNetworkError(event: JSONObject): NetworkError {
        val error = event.optJSONObject("error")
        val code = error?.optString("code").orEmpty()
        val type = error?.optString("type").orEmpty()
        val host = upgrade?.url?.host.orEmpty()
        return when {
            code == "invalid_api_key" || type == "authentication_error" -> NetworkError.Unauthorized(401, host)
            code == "rate_limit_exceeded" -> NetworkError.RateLimited(host, null)
            else -> NetworkError.InvalidResponse("realtime error ${code.ifEmpty { type }}")
        }
    }

    private fun transportError(t: Throwable, response: Response?): NetworkError {
        val host = upgrade?.url?.host.orEmpty()
        val code = response?.code
        return when {
            code == 401 || code == 403 -> NetworkError.Unauthorized(code, host)
            code == 429 -> NetworkError.RateLimited(host, parseRetryAfterMs(response?.header("Retry-After")))
            code != null && code != SWITCHING_PROTOCOLS -> NetworkError.Http(code, host, null)
            t is IOException -> t.toNetworkError()
            else -> NetworkError.InvalidResponse("realtime transport", t)
        }
    }

    private fun NetworkError.isTransient(): Boolean = when (this) {
        is NetworkError.Offline, is NetworkError.Timeout -> true
        is NetworkError.Http -> status >= 500
        else -> false
    }

    private fun emit(event: StreamEvent) {
        _events.tryEmit(event)
    }

    /** One listener per socket; callbacks of a replaced or closed socket are ignored. */
    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) { if (webSocket === socket) onOpenLocked(webSocket) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            synchronized(lock) { if (webSocket === socket) onMessageLocked(text) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            synchronized(lock) {
                if (webSocket === socket) onTransportLostLocked(NetworkError.Offline())
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronized(lock) {
                if (webSocket === socket) onTransportLostLocked(transportError(t, response))
            }
        }
    }

    companion object {
        const val PING_INTERVAL_SECONDS = 15L
        const val MIN_COMMIT_TIMEOUT_MS = 5_000L
        private const val API_SAMPLE_RATE = 24_000
        private const val NORMAL_CLOSURE = 1000
        private const val SWITCHING_PROTOCOLS = 101

        // ~5.8 min of 24 kHz PCM16; longer takes are not replayed after a reconnect.
        private const val MAX_REPLAY_BYTES = 16L * 1024 * 1024

        /** `{base}/v1/realtime?intent=transcription`; tolerates bases ending in `/v1`, `/v1/realtime` or `/audio/transcriptions`. */
        internal fun realtimeUrl(base: HttpUrl): HttpUrl {
            val segments = base.pathSegments.filter { it.isNotBlank() }.toMutableList()
            if (segments.takeLast(2) == listOf("audio", "transcriptions")) {
                repeat(2) { segments.removeAt(segments.lastIndex) }
            }
            if (segments.lastOrNull() != "realtime") {
                if (segments.lastOrNull() != "v1") segments += "v1"
                segments += "realtime"
            }
            return base.newBuilder()
                .encodedPath("/")
                .addPathSegments(segments.joinToString("/"))
                .query(null)
                .addQueryParameter("intent", "transcription")
                .build()
        }
    }
}
