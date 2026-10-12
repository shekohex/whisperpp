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
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible realtime transcription (`{base}/v1/realtime?model=M&intent=transcription`,
 * §4.5.1), for OpenAI itself and for any gateway that exposes the Realtime API (LiteLLM, ...). The
 * protocol follows VoiceInk:
 *
 * - The first message configures the session ([RealtimeSchema.GA]; a gateway that answers it with an
 *   `error` gets one retry with the [RealtimeSchema.BETA] shape). Audio is only sent after the
 *   server confirmed it with `session.updated`; until then it is kept in memory and flushed in order.
 * - With [Vad.ServerVad] the server commits each utterance on silence, so every utterance becomes
 *   one conversation item transcribed while the user keeps speaking; items are kept in commit order
 *   and partials show the whole take so far.
 * - [commit] is deterministic, with no timing guesses: flush the resampler, turn the server VAD
 *   off, and on its `session.updated` commit the speech that is still pending (or clear the buffer
 *   when there is none); after `committed` send `clear`, and `cleared` acknowledges that the server
 *   saw everything we sent. The text is returned once every item has its transcription.
 *
 * Audio is resampled to the 24 kHz the API expects and kept in memory so one reconnect before
 * [commit] can replay it; after the commit a broken stream fails instead, and the caller falls back
 * to batch. A refused send fails the stream for the same reason.
 *
 * [sockets] is the shared client derived with a ping interval (OkHttp upgrades the http(s) URL).
 */
class OpenAiRealtimeSession internal constructor(
    private val sockets: WebSocket.Factory,
    private val provider: ServiceProvider,
    private val model: ModelConfig,
    private val credentials: Credentials,
    private val request: TranscriptionRequest,
    private val postProcess: suspend (String, ScriptConversion) -> String = { text, _ -> text },
    private val commitTimeoutMs: Long = request.timeoutMs.coerceAtLeast(MIN_COMMIT_TIMEOUT_MS),
    inputSampleRate: Int = AudioRecorder.DEFAULT_SAMPLE_RATE,
    private val vad: Vad = Vad.ServerVad(),
    initialSchema: RealtimeSchema = RealtimeSchema.GA,
    private val betaFallback: Boolean = true,
    private val connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
) : StreamingTranscriptionSession {
    /** One committed (or in-progress) utterance; [final] is set once its transcription completed. */
    private class Item {
        val deltas = StringBuilder()
        var final: String? = null

        val text: String
            get() = final ?: deltas.toString()
    }

    /** Where the stop sequence stands; [RUNNING] until [commit]. */
    private enum class Stop { RUNNING, DISABLING_VAD, COMMITTING, CLEARING, ACKNOWLEDGED }

    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<StreamEvent> = _events.asSharedFlow()

    // Guards everything below; OkHttp calls the listener on its own threads.
    private val lock = Any()
    private val resampler = Pcm16Resampler(inputSampleRate, RealtimeSchema.API_SAMPLE_RATE)
    private val final = CompletableDeferred<Result<String>>()

    // Insertion order is commit order (items appear when their audio is committed).
    private val items = LinkedHashMap<String, Item>()

    // Every 24 kHz chunk of the take: held back until the session is ready, replayed after a reconnect.
    private val sentAudio = ArrayList<ByteArray>()
    private var sentBytes = 0L
    private var replayable = true
    private var endpoint: HttpUrl? = null
    private var apiKey: String? = null
    private var schema = initialSchema
    private var fellBackToBeta = false
    private var socket: WebSocket? = null

    // The socket is upgraded, but the server has not confirmed the session update yet.
    private var open = false
    private var ready = false
    private var connectTimeout: Future<*>? = null
    private var reconnectsLeft = 1
    private var commitRequested = false
    private var stop = Stop.RUNNING

    // `speech_started` item whose audio the server has not committed yet.
    private var pendingSpeechId: String? = null
    private var manualCommitSent = false
    private var failure: NetworkError? = null
    private var closed = false

    /** Throws [com.github.shekohex.whisperpp.domain.providers.ProviderConfigException] or [NetworkError] when the stream cannot be opened. */
    override suspend fun start() {
        val key = provider.apiKeyOrThrow(credentials)
        val url = realtimeUrl(provider.baseUrlOrThrow(), model.id)
        // OkHttp only takes http(s) URLs and upgrades them itself; check the scheme it will use (J-01).
        val wsScheme = if (url.isHttps) "wss" else "ws"
        if (!isTransportAllowed(wsScheme, url.host)) throw NetworkError.InsecureTransport(url.host)
        synchronized(lock) {
            if (closed || endpoint != null) return
            endpoint = url
            apiKey = key
            connectLocked()
        }
    }

    override suspend fun sendAudio(pcm16: ByteArray) {
        synchronized(lock) {
            if (closed || commitRequested) return
            appendLocked(resampler.process(pcm16))
        }
    }

    override suspend fun commit(): Result<Transcript> {
        synchronized(lock) {
            failure?.let { return Result.failure(it) }
            if (closed || endpoint == null) return Result.failure(NetworkError.InvalidResponse("realtime session not open"))
            if (!commitRequested) {
                commitRequested = true
                appendLocked(resampler.flush())
                if (ready) beginStopLocked()
            }
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

    /** Fails a session whose server never confirmed the session update; driven by the connect timer. */
    internal fun connectTimeoutTick() {
        synchronized(lock) {
            if (!ready && !closed) failLocked(NetworkError.Timeout())
        }
    }

    private fun upgradeRequest(): Request {
        val builder = Request.Builder().url(checkNotNull(endpoint))
        apiKey?.let { builder.header("Authorization", "Bearer $it") }
        schema.applyHeaders(builder)
        return builder.build()
    }

    private fun connectLocked() {
        open = false
        ready = false
        // A new connection transcribes the replayed take from scratch.
        items.clear()
        stop = Stop.RUNNING
        pendingSpeechId = null
        manualCommitSent = false
        connectTimeout?.cancel(false)
        connectTimeout = TIMER.schedule(Runnable { connectTimeoutTick() }, connectTimeoutMs, TimeUnit.MILLISECONDS)
        socket = sockets.newWebSocket(upgradeRequest(), Listener())
    }

    private fun onOpenLocked(ws: WebSocket) {
        open = true
        sendLocked(schema.sessionUpdate(model.id, request.languageCode, request.prompt, vad))
    }

    /** The server confirmed the session: flush what was recorded meanwhile, in order, then carry on. */
    private fun becomeReadyLocked() {
        if (ready || closed || !open) return
        ready = true
        connectTimeout?.cancel(false)
        connectTimeout = null
        for (chunk in sentAudio) {
            if (!sendAudioLocked(chunk)) return
        }
        emit(StreamEvent.Connected)
        if (commitRequested) beginStopLocked()
    }

    private fun onMessageLocked(text: String) {
        val event = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return
        }
        val type = event.optString("type")
        if (!ready) {
            onHandshakeMessageLocked(type, event)
            return
        }
        val itemId = event.optString("item_id")
        when (type) {
            "session.updated", "transcription_session.updated" ->
                if (stop == Stop.DISABLING_VAD && schema.vadDisabledIn(event)) decideStopLocked()
            "input_audio_buffer.speech_started" -> pendingSpeechId = itemKey(itemId)
            "input_audio_buffer.committed" -> onCommittedLocked(itemId)
            "input_audio_buffer.cleared" -> if (stop == Stop.CLEARING) stop = Stop.ACKNOWLEDGED
            "conversation.item.input_audio_transcription.delta" -> {
                val delta = event.optString("delta")
                val item = deltaTargetLocked(itemId) ?: return
                if (delta.isEmpty()) return
                item.deltas.append(delta)
                emitPartialLocked()
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val item = items.getOrPut(itemKey(itemId.ifEmpty { lastOpenItemKeyLocked().orEmpty() })) { Item() }
                item.final = event.optString("transcript").trim()
                emitPartialLocked()
            }
            // A hole in the take: the stream can no longer match the WAV, so batch takes over.
            "conversation.item.input_audio_transcription.failed" ->
                failLocked(NetworkError.InvalidResponse("realtime transcription failed"))
            "error" -> onErrorLocked(event)
        }
        checkDoneLocked()
    }

    /** Before the session is confirmed only `session.updated` and `error` matter; the rest is greeting. */
    private fun onHandshakeMessageLocked(type: String, event: JSONObject) {
        when {
            RealtimeSchema.isSessionUpdated(type) -> becomeReadyLocked()
            type == "error" -> onHandshakeErrorLocked(event)
        }
    }

    /**
     * A gateway that does not know the GA session shape answers with an `error`; the beta shape gets
     * one try on a fresh connection (the audio recorded meanwhile is replayed). Credential and
     * rate-limit errors are final: the other schema cannot fix them.
     */
    private fun onHandshakeErrorLocked(event: JSONObject) {
        val error = errorEventToNetworkError(event)
        val schemaMismatch = error !is NetworkError.Unauthorized && error !is NetworkError.RateLimited
        if (betaFallback && schema == RealtimeSchema.GA && !fellBackToBeta && schemaMismatch) {
            fellBackToBeta = true
            schema = RealtimeSchema.BETA
            socket?.cancel()
            connectLocked()
        } else {
            failLocked(error)
        }
    }

    private fun onErrorLocked(event: JSONObject) {
        if (isEmptyCommitError(event)) {
            // The VAD had already committed that speech, so there is nothing left to commit: carry on.
            if (stop == Stop.COMMITTING) startClearingLocked()
        } else {
            failLocked(errorEventToNetworkError(event))
        }
    }

    private fun onCommittedLocked(itemId: String) {
        val key = itemKey(itemId)
        items.getOrPut(key) { Item() }
        if (pendingSpeechId == key) pendingSpeechId = null
        if (stop == Stop.COMMITTING) startClearingLocked()
    }

    private fun itemKey(itemId: String): String = itemId.ifEmpty { ANONYMOUS_ITEM }

    // Deltas for an item that already completed are late and dropped; they never move text between items.
    private fun deltaTargetLocked(itemId: String): Item? {
        val key = itemKey(itemId.ifEmpty { lastOpenItemKeyLocked().orEmpty() })
        val item = items.getOrPut(key) { Item() }
        return if (item.final != null) null else item
    }

    private fun lastOpenItemKeyLocked(): String? = items.entries.lastOrNull { it.value.final == null }?.key

    private fun joinedTextLocked(): String = items.values.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    private fun emitPartialLocked() {
        val text = joinedTextLocked()
        if (text.isNotEmpty()) emit(StreamEvent.Partial(text))
    }

    /** Done once the stop sequence is acknowledged, no speech is pending and every item has its text. */
    private fun checkDoneLocked() {
        if (closed || !commitRequested || stop != Stop.ACKNOWLEDGED || pendingSpeechId != null) return
        if (items.values.any { it.final == null }) return
        final.complete(Result.success(joinedTextLocked()))
    }

    /** Stop step 1: turn the server VAD off (without VAD there is nothing to race, so go to step 2). */
    private fun beginStopLocked() {
        if (closed || stop != Stop.RUNNING) return
        if (vad is Vad.ServerVad) {
            stop = Stop.DISABLING_VAD
            sendLocked(schema.disableTurnDetection())
        } else {
            decideStopLocked()
        }
    }

    /**
     * Stop step 2, on the VAD-off confirmation: speech the server heard but has not committed is
     * committed; with none, the buffer only holds silence and is cleared.
     */
    private fun decideStopLocked() {
        val speechPending = pendingSpeechId != null || (vad is Vad.None && sentBytes > 0)
        if (speechPending) {
            stop = Stop.COMMITTING
            manualCommitSent = true
            sendLocked(JSONObject().put("type", "input_audio_buffer.commit").toString())
        } else {
            startClearingLocked()
        }
    }

    /** Stop step 3: `clear` is the barrier; its `cleared` answer means the server saw all our audio. */
    private fun startClearingLocked() {
        pendingSpeechId = null
        stop = Stop.CLEARING
        sendLocked(JSONObject().put("type", "input_audio_buffer.clear").toString())
    }

    private fun appendLocked(audio: ByteArray) {
        if (audio.isEmpty()) return
        if (replayable) {
            sentBytes += audio.size
            if (sentBytes > MAX_REPLAY_BYTES) {
                // Too long to replay; a dropped connection now ends the stream (batch takes over).
                if (!ready) {
                    failLocked(NetworkError.InvalidResponse("realtime audio backlog"))
                    return
                }
                replayable = false
                sentAudio.clear()
            } else {
                sentAudio += audio
            }
        }
        if (ready) sendAudioLocked(audio)
    }

    /** Sends [audio] as `input_audio_buffer.append` slices of at most [MAX_APPEND_BYTES]. */
    private fun sendAudioLocked(audio: ByteArray): Boolean {
        var offset = 0
        while (offset < audio.size) {
            val length = minOf(MAX_APPEND_BYTES, audio.size - offset)
            if (!sendLocked(appendMessage(audio, offset, length))) return false
            offset += length
        }
        return true
    }

    // A refused frame (queue full or socket going down) leaves a hole in the take: batch takes over.
    private fun sendLocked(text: String): Boolean {
        if (socket?.send(text) == true) return true
        failLocked(NetworkError.Offline())
        return false
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
        connectTimeout?.cancel(false)
        connectTimeout = null
        if (graceful) socket?.close(NORMAL_CLOSURE, null) else socket?.cancel()
        socket = null
        sentAudio.clear()
        final.complete(Result.failure(NetworkError.InvalidResponse("realtime session closed")))
        emit(StreamEvent.Closed)
    }

    private fun isEmptyCommitError(event: JSONObject): Boolean {
        val error = event.optJSONObject("error") ?: return false
        if (error.optString("code") == "input_audio_buffer_commit_empty") return true
        return manualCommitSent && error.optString("message").contains("buffer too small", ignoreCase = true)
    }

    private fun appendMessage(audio: ByteArray, offset: Int, length: Int): String = JSONObject()
        .put("type", "input_audio_buffer.append")
        .put("audio", audio.toByteString(offset, length).base64())
        .toString()

    private fun errorEventToNetworkError(event: JSONObject): NetworkError {
        val error = event.optJSONObject("error")
        val code = error?.optString("code").orEmpty()
        val type = error?.optString("type").orEmpty()
        val host = endpoint?.host.orEmpty()
        return when {
            code == "invalid_api_key" || type == "authentication_error" || code == "401" || code == "403" ->
                NetworkError.Unauthorized(code.toIntOrNull() ?: 401, host)
            code == "rate_limit_exceeded" || code == "429" -> NetworkError.RateLimited(host, null)
            else -> NetworkError.InvalidResponse("realtime error ${code.ifEmpty { type }}")
        }
    }

    private fun transportError(t: Throwable, response: Response?): NetworkError {
        val host = endpoint?.host.orEmpty()
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

        /** How long the server gets to confirm the session update (VoiceInk uses 10 s). */
        const val CONNECT_TIMEOUT_MS = 10_000L
        private const val NORMAL_CLOSURE = 1000
        private const val SWITCHING_PROTOCOLS = 101
        private const val ANONYMOUS_ITEM = "_"
        private const val OPENAI_HOST = "api.openai.com"

        // 0.5 s of 24 kHz PCM16 per append, as VoiceInk slices it.
        private const val MAX_APPEND_BYTES = 24_000

        // ~5.8 min of 24 kHz PCM16; longer takes are not replayed after a reconnect.
        private const val MAX_REPLAY_BYTES = 16L * 1024 * 1024

        // Shared by all sessions: one daemon thread is enough for a few connect timeouts.
        private val TIMER = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "realtime-connect-timeout").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }

        /**
         * `{base}/v1/realtime?model=M&intent=transcription`; tolerates bases ending in `/v1`,
         * `/v1/realtime` or `/audio/transcriptions` and keeps a path prefix (`https://host/litellm/v1`).
         * Gateways route by the `model` query parameter; api.openai.com does not need it.
         */
        internal fun realtimeUrl(base: HttpUrl, modelId: String): HttpUrl {
            val segments = base.pathSegments.filter { it.isNotBlank() }.toMutableList()
            if (segments.takeLast(2) == listOf("audio", "transcriptions")) {
                repeat(2) { segments.removeAt(segments.lastIndex) }
            }
            if (segments.lastOrNull() != "realtime") {
                if (segments.lastOrNull() != "v1") segments += "v1"
                segments += "realtime"
            }
            val builder = base.newBuilder()
                .encodedPath("/")
                .addPathSegments(segments.joinToString("/"))
                .query(null)
            if (modelId.isNotBlank() && base.host != OPENAI_HOST) builder.addQueryParameter("model", modelId.trim())
            return builder.addQueryParameter("intent", "transcription").build()
        }
    }
}
