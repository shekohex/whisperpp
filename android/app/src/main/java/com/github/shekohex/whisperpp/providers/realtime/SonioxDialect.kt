package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.providers.withPathSuffix
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Soniox realtime (`/transcribe-websocket`): the first text frame carries the config (and the API
 * key, as the protocol requires), audio is raw binary, `finalize` plus an empty frame ends the
 * take, and token messages build the transcript. `is_final` tokens are locked into a buffer, the
 * non-final tail follows them, `<fin>` closes a segment and `finished` ends the session.
 */
internal class SonioxDialect(private val config: StreamingConfig) : StreamingDialect {
    private val segments = ArrayList<String>()
    private val finalBuffer = StringBuilder()
    private var done = false

    override fun upgradeRequest(): Request =
        Request.Builder().url(config.baseUrl.withPathSuffix("transcribe-websocket")).build()

    override fun openFrames(): List<WsFrame> {
        val message = JSONObject()
            .put("api_key", config.apiKey)
            .put("model", wireModel(config.modelId))
            .put("audio_format", "pcm_s16le")
            .put("sample_rate", SAMPLE_RATE)
            .put("num_channels", 1)
            .put("enable_language_identification", true)
        config.language?.let {
            message.put("language_hints", JSONArray().put(it)).put("language_hints_strict", true)
        }
        if (config.vocabulary.isNotEmpty()) {
            message.put("context", JSONObject().put("terms", JSONArray(config.vocabulary)))
        }
        return listOf(WsFrame.Text(message.toString()))
    }

    override fun audioFrames(pcm16: ByteArray): List<WsFrame> = listOf(WsFrame.Binary(pcm16))

    // `finalize` flushes pending tokens; the empty frame tells the server the audio is over.
    override fun commitFrames(): List<WsFrame> =
        listOf(WsFrame.Text("""{"type":"finalize"}"""), WsFrame.Text(""))

    override fun onMessage(text: String): List<DialectEvent> {
        val json = RealtimeText.parse(text) ?: return emptyList()
        if (json.has("error_code")) {
            return listOf(DialectEvent.Failed(RealtimeText.errorFor(config.baseUrl.host, json.optString("error_code"))))
        }
        val events = ArrayList<DialectEvent>()
        val tail = StringBuilder()
        val tokens = json.optJSONArray("tokens")
        for (i in 0 until (tokens?.length() ?: 0)) {
            val token = tokens?.optJSONObject(i) ?: continue
            val tokenText = token.optString("text")
            when {
                tokenText == FIN -> commitSegment()
                tokenText == END -> Unit
                token.optBoolean("is_final") -> finalBuffer.append(tokenText)
                else -> tail.append(tokenText)
            }
        }
        if (tokens != null && tokens.length() > 0) {
            events += DialectEvent.Partial(RealtimeText.cumulative(segments, finalBuffer.toString() + tail))
        }
        if (json.optBoolean("finished") && !done) {
            done = true
            commitSegment()
            events += DialectEvent.Done(RealtimeText.join(segments))
        }
        return events
    }

    private fun commitSegment() {
        val segment = finalBuffer.toString().trim()
        finalBuffer.setLength(0)
        if (segment.isNotEmpty()) segments += segment
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FIN = "<fin>"
        private const val END = "<end>"

        /** The UI offers the async model id; the socket takes the realtime sibling. */
        fun wireModel(modelId: String): String = modelId.replace("-async-", "-rt-")
    }
}
