package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.providers.withPathSuffix
import okhttp3.Request
import org.json.JSONObject

/**
 * Cartesia Ink (`/stt/turns/websocket`), streaming only: raw PCM, English only, no vocabulary.
 * Turn updates are partials, `turn.end` commits a turn, and `close` makes the server flush the last
 * turn and close the socket, which is the done signal.
 */
internal class CartesiaDialect(private val config: StreamingConfig) : StreamingDialect {
    private val turns = ArrayList<String>()
    private var inProgress = ""
    private var commitSent = false
    private var done = false

    override fun upgradeRequest(): Request {
        val url = config.baseUrl.withPathSuffix("stt", "turns", "websocket").newBuilder()
            .addQueryParameter("model", config.modelId)
            .addQueryParameter("encoding", "pcm_s16le")
            .addQueryParameter("sample_rate", SAMPLE_RATE.toString())
            .addQueryParameter("cartesia_version", API_VERSION)
            .build()
        return Request.Builder().url(url).header("X-API-Key", config.apiKey).build()
    }

    override fun audioFrames(pcm16: ByteArray): List<WsFrame> = listOf(WsFrame.Binary(pcm16))

    override fun commitFrames(): List<WsFrame> {
        commitSent = true
        return listOf(WsFrame.Text(JSONObject().put("type", "close").toString()))
    }

    override fun onMessage(text: String): List<DialectEvent> {
        val json = RealtimeText.parse(text) ?: return emptyList()
        val transcript = json.optString("transcript").trim()
        return when (json.optString("type")) {
            "turn.update", "turn.eager_end" -> {
                inProgress = transcript
                listOf(DialectEvent.Partial(RealtimeText.cumulative(turns, transcript)))
            }
            "turn.end" -> {
                inProgress = ""
                if (transcript.isNotEmpty()) turns += transcript
                listOf(DialectEvent.Partial(RealtimeText.join(turns)))
            }
            "error" -> listOf(DialectEvent.Failed(RealtimeText.errorFor(config.baseUrl.host, errorKind(json))))
            else -> emptyList()
        }
    }

    override fun onRemoteClose(): DialectEvent.Done? {
        if (!commitSent || done) return null
        done = true
        // A turn the server never ended still holds the last words.
        return DialectEvent.Done(RealtimeText.cumulative(turns, inProgress))
    }

    private fun errorKind(json: JSONObject): String =
        json.optString("code").ifEmpty { json.optString("title") }.ifEmpty { json.optString("type") }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val API_VERSION = "2026-03-01"
    }
}
