package com.github.shekohex.whisperpp.providers.realtime

import okhttp3.Request
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/**
 * Mistral Voxtral realtime (`/v1/audio/transcriptions/realtime`): after `session.created` the audio
 * format is set, audio goes as base64 JSON, deltas accumulate and `transcription.done` ends the take.
 * The language is not sent.
 */
internal class MistralDialect(private val config: StreamingConfig) : StreamingDialect {
    private val accumulated = StringBuilder()
    private var done = false

    override val awaitsReady: Boolean = true

    override fun upgradeRequest(): Request {
        val url = RealtimeText.v1Url(config.baseUrl, "audio", "transcriptions", "realtime").newBuilder()
            .addQueryParameter("model", wireModel(config.modelId))
            .build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()
    }

    override fun readyFrames(): List<WsFrame> {
        val format = JSONObject().put("encoding", "pcm_s16le").put("sample_rate", SAMPLE_RATE)
        val update = JSONObject()
            .put("type", "session.update")
            .put("session", JSONObject().put("audio_format", format))
        return listOf(WsFrame.Text(update.toString()))
    }

    override fun audioFrames(pcm16: ByteArray): List<WsFrame> {
        val message = JSONObject()
            .put("type", "input_audio.append")
            .put("audio", pcm16.toByteString().base64())
        return listOf(WsFrame.Text(message.toString()))
    }

    override fun commitFrames(): List<WsFrame> {
        return listOf(WsFrame.Text(JSONObject().put("type", "input_audio.end").toString()))
    }

    override fun onMessage(text: String): List<DialectEvent> {
        val json = RealtimeText.parse(text) ?: return emptyList()
        return when (json.optString("type")) {
            "session.created" -> listOf(DialectEvent.Ready)
            "transcription.text.delta" -> {
                accumulated.append(json.optString("text"))
                listOf(DialectEvent.Partial(accumulated.toString().trim()))
            }
            "transcription.done" -> {
                if (done) return emptyList()
                done = true
                if (accumulated.isBlank()) accumulated.append(json.optString("text"))
                listOf(DialectEvent.Done(accumulated.toString().trim()))
            }
            "error" -> listOf(DialectEvent.Failed(failure(json)))
            else -> emptyList()
        }
    }

    private fun failure(json: JSONObject) = RealtimeText.errorFor(
        config.baseUrl.host,
        json.optJSONObject("error")?.let { it.optString("code").ifEmpty { it.optString("type") } }.orEmpty(),
    )

    companion object {
        private const val SAMPLE_RATE = 16_000
        const val REALTIME_MODEL = "voxtral-mini-transcribe-realtime-2602"
        private const val LATEST_ALIAS = "voxtral-mini-latest"

        /** The UI model id `voxtral-mini-latest` is the batch name; the socket takes the realtime model. */
        fun wireModel(modelId: String): String = if (modelId == LATEST_ALIAS) REALTIME_MODEL else modelId
    }
}
