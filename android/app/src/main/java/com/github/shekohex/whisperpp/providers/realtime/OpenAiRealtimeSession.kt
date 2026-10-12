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
 * OpenAI Realtime transcription (`/v1/realtime?intent=transcription`, §4.5.1). With [Vad.ServerVad]
 * the server commits each utterance on silence, so every utterance becomes one conversation item
 * transcribed while the user keeps speaking; [Vad.None] makes the whole take one item. Items are
 * kept in arrival order: partials show the whole take so far, and [commit] returns the join of all
 * items once each is completed. Audio is resampled to the 24 kHz the API expects and kept in memory
 * so one reconnect before [commit] can replay it; after the commit a broken stream fails instead,
 * and the caller falls back to batch.
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
    private val vad: Vad = Vad.ServerVad(),
) : StreamingTranscriptionSession {
    /** One committed (or in-progress) utterance; [final] is set once its transcription completed. */
    private class Item {
        val deltas = StringBuilder()
        var final: String? = null

        val text: String
            get() = final ?: deltas.toString()
    }

    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<StreamEvent> = _events.asSharedFlow()

    // Guards everything below; OkHttp calls the listener on its own threads.
    private val lock = Any()
    private val resampler = Pcm16Resampler(inputSampleRate, API_SAMPLE_RATE)
    private val final = CompletableDeferred<Result<String>>()

    // Insertion order is utterance order (items appear when speech starts or the buffer is committed).
    private val items = LinkedHashMap<String, Item>()
    private val sentAudio = ArrayList<ByteArray>()
    private var sentBytes = 0L
    private var replayable = true
    private var upgrade: Request? = null
    private var socket: WebSocket? = null
    private var open = false
    private var reconnectsLeft = 1
    private var commitRequested = false

    // Our manual commit was sent and neither its `committed` event nor an empty-buffer error came back.
    private var manualCommitPending = false

    // Audio appended since the last `input_audio_buffer.committed` (the server buffer is not empty).
    private var appendedSinceCommit = false

    // Server VAD: `speech_stopped` events whose auto-commit has not arrived yet.
    private var autoCommitsPending = 0
    private var speechActive = false
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
            if (open) {
                socket?.send(appendMessage(audio))
                appendedSinceCommit = true
            }
        }
    }

    override suspend fun commit(): Result<Transcript> {
        synchronized(lock) {
            failure?.let { return Result.failure(it) }
            if (closed || upgrade == null) return Result.failure(NetworkError.InvalidResponse("realtime session not open"))
            commitRequested = true
            if (open) commitTrailingAudioLocked()
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
        // A new connection transcribes the replayed take from scratch.
        items.clear()
        manualCommitPending = false
        appendedSinceCommit = false
        autoCommitsPending = 0
        speechActive = false
        socket = sockets.newWebSocket(upgradeRequest, Listener())
    }

    private fun onOpenLocked(ws: WebSocket) {
        ws.send(sessionUpdateMessage())
        // A reconnect starts with an empty server buffer: replay the take so far.
        for (chunk in sentAudio) ws.send(appendMessage(chunk))
        if (sentAudio.isNotEmpty()) appendedSinceCommit = true
        open = true
        emit(StreamEvent.Connected)
        if (commitRequested) commitTrailingAudioLocked()
    }

    private fun onMessageLocked(text: String) {
        val event = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return
        }
        val itemId = event.optString("item_id")
        when (event.optString("type")) {
            "input_audio_buffer.speech_started" -> {
                speechActive = true
                itemLocked(itemId)
            }
            "input_audio_buffer.speech_stopped" -> {
                speechActive = false
                autoCommitsPending += 1
                itemLocked(itemId)
            }
            "input_audio_buffer.committed" -> onCommittedLocked(itemId)
            "conversation.item.created" -> itemLocked(event.optJSONObject("item")?.optString("id").orEmpty().ifEmpty { itemId })
            "conversation.item.input_audio_transcription.delta" -> {
                val delta = event.optString("delta")
                val item = deltaTargetLocked(itemId) ?: return
                if (delta.isEmpty()) return
                item.deltas.append(delta)
                emitPartialLocked()
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val item = itemLocked(itemId.ifEmpty { lastOpenItemKeyLocked() ?: ANONYMOUS_ITEM })
                item.final = event.optString("transcript").trim()
                emitPartialLocked()
                checkDoneLocked()
            }
            // A hole in the take: the stream can no longer match the WAV, so batch takes over.
            "conversation.item.input_audio_transcription.failed" ->
                failLocked(NetworkError.InvalidResponse("realtime transcription failed"))
            "error" -> {
                if (isEmptyCommitError(event)) {
                    // Nothing was pending (the server had already committed everything).
                    manualCommitPending = false
                    appendedSinceCommit = false
                    checkDoneLocked()
                } else {
                    failLocked(errorEventToNetworkError(event))
                }
            }
        }
    }

    private fun onCommittedLocked(itemId: String) {
        itemLocked(itemId)
        appendedSinceCommit = false
        speechActive = false
        // Server VAD commits follow `speech_stopped`; any other commit answers ours.
        if (autoCommitsPending > 0) {
            autoCommitsPending -= 1
        } else {
            manualCommitPending = false
        }
        checkDoneLocked()
    }

    private fun itemLocked(itemId: String): Item {
        val key = itemId.ifEmpty { ANONYMOUS_ITEM }
        return items.getOrPut(key) { Item() }
    }

    // Deltas for an item that already completed are late and dropped; they never move text between items.
    private fun deltaTargetLocked(itemId: String): Item? {
        val key = itemId.ifEmpty { lastOpenItemKeyLocked() ?: ANONYMOUS_ITEM }
        val item = itemLocked(key)
        return if (item.final != null) null else item
    }

    private fun lastOpenItemKeyLocked(): String? = items.entries.lastOrNull { it.value.final == null }?.key

    private fun joinedTextLocked(): String = items.values.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    private fun emitPartialLocked() {
        val text = joinedTextLocked()
        if (text.isNotEmpty()) emit(StreamEvent.Partial(text))
    }

    /** After [commit]: done when our commit is answered and every known item has completed. */
    private fun checkDoneLocked() {
        if (!commitRequested || !open) return
        if (manualCommitPending || autoCommitsPending > 0) return
        if (items.values.any { it.final == null }) return
        final.complete(Result.success(joinedTextLocked()))
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

    /** Commits audio the server has not committed yet, then waits for every item (see [checkDoneLocked]). */
    private fun commitTrailingAudioLocked() {
        val ws = socket ?: return
        if (appendedSinceCommit || speechActive) {
            ws.send(JSONObject().put("type", "input_audio_buffer.commit").toString())
            manualCommitPending = true
        }
        checkDoneLocked()
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
            .put("turn_detection", turnDetection())
        return JSONObject()
            .put("type", "transcription_session.update")
            .put("session", session)
            .toString()
    }

    private fun turnDetection(): Any = when (vad) {
        Vad.None -> JSONObject.NULL
        is Vad.ServerVad -> JSONObject()
            .put("type", "server_vad")
            .put("threshold", vad.threshold)
            .put("prefix_padding_ms", vad.prefixPaddingMs)
            .put("silence_duration_ms", vad.silenceDurationMs)
    }

    private fun isEmptyCommitError(event: JSONObject): Boolean {
        val error = event.optJSONObject("error") ?: return false
        if (error.optString("code") == "input_audio_buffer_commit_empty") return true
        return manualCommitPending && error.optString("message").contains("buffer too small", ignoreCase = true)
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
        private const val ANONYMOUS_ITEM = "_"

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
