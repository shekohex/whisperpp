package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import okhttp3.Request
import org.json.JSONObject

/**
 * Deepgram live streaming (`/v1/listen`): raw binary PCM, `Results` messages, `CloseStream` to
 * finish. Committed segments are appended; the in-progress one replaces the previous partial.
 */
internal class DeepgramDialect(private val config: StreamingConfig) : StreamingDialect {
    private val committed = ArrayList<String>()
    private var commitSent = false
    private var done = false

    override fun upgradeRequest(): Request {
        val url = RealtimeText.v1Url(config.baseUrl, "listen").newBuilder().apply {
            addQueryParameter("model", config.modelId)
            addQueryParameter("encoding", "linear16")
            addQueryParameter("sample_rate", SAMPLE_RATE.toString())
            addQueryParameter("channels", "1")
            addQueryParameter("smart_format", "true")
            addQueryParameter("interim_results", "true")
            addQueryParameter("mip_opt_out", "true")
            languageParameter()?.let { addQueryParameter("language", it) }
            if (supportsKeyterms(config.modelId)) {
                config.vocabulary.take(MAX_KEYTERMS).forEach { addQueryParameter("keyterm", it) }
            }
        }.build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Token ${config.apiKey}")
            .build()
    }

    override fun audioFrames(pcm16: ByteArray): List<WsFrame> = listOf(WsFrame.Binary(pcm16))

    override fun commitFrames(): List<WsFrame> {
        commitSent = true
        return listOf(WsFrame.Text(CLOSE_STREAM))
    }

    override val keepAlive: StreamingDialect.KeepAlive =
        StreamingDialect.KeepAlive(WsFrame.Text(KEEP_ALIVE), KEEP_ALIVE_INTERVAL_MS)

    override fun onMessage(text: String): List<DialectEvent> {
        val json = RealtimeText.parse(text) ?: return emptyList()
        return when (json.optString("type")) {
            "Results" -> onResults(json)
            // Sent once the stream is closed and every result was delivered.
            "Metadata" -> if (commitSent) listOf(finish()) else emptyList()
            "Error" -> listOf(DialectEvent.Failed(failure(json)))
            else -> emptyList()
        }
    }

    override fun onRemoteClose(): DialectEvent.Done? = if (commitSent && !done) finish() else null

    private fun finish(): DialectEvent.Done {
        done = true
        return DialectEvent.Done(RealtimeText.join(committed))
    }

    private fun onResults(json: JSONObject): List<DialectEvent> {
        val transcript = json.optJSONObject("channel")
            ?.optJSONArray("alternatives")
            ?.optJSONObject(0)
            ?.optString("transcript")
            .orEmpty()
            .trim()
        if (transcript.isEmpty()) return emptyList()
        return if (json.optBoolean("is_final") || json.optBoolean("speech_final")) {
            committed += transcript
            listOf(DialectEvent.Partial(RealtimeText.join(committed)))
        } else {
            listOf(DialectEvent.Partial(RealtimeText.cumulative(committed, transcript)))
        }
    }

    private fun failure(json: JSONObject): NetworkError {
        val kind = json.optString("variant").ifEmpty { json.optString("err_code") }
        return RealtimeText.errorFor(config.baseUrl.host, kind)
    }

    private fun languageParameter(): String? {
        config.language?.let { return it }
        // Nova-3 transcribes mixed languages when told to.
        return if (config.modelId.startsWith("nova-3")) "multi" else null
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MAX_KEYTERMS = 100
        private const val KEEP_ALIVE_INTERVAL_MS = 5_000L
        private const val CLOSE_STREAM = """{"type":"CloseStream"}"""
        private const val KEEP_ALIVE = """{"type":"KeepAlive"}"""

        /** `keyterm` prompting is a Nova-3 feature. */
        fun supportsKeyterms(modelId: String): Boolean = modelId.startsWith("nova-3")
    }
}
