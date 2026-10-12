package com.github.shekohex.whisperpp.providers.realtime

import okhttp3.Request
import org.json.JSONObject

/**
 * xAI speech-to-text (`/v1/stt`): raw PCM in 3200-byte (100 ms) frames, `audio.done` to finish.
 * Within an utterance `is_final` segments are locked into a buffer; `speech_final` closes the
 * utterance; `transcript.done` ends the session.
 */
internal class XaiDialect(private val config: StreamingConfig) : StreamingDialect {
    private val utterances = ArrayList<String>()
    private val locked = ArrayList<String>()
    private val pending = Rechunker(FRAME_BYTES)
    private var done = false

    override val awaitsReady: Boolean = true

    override fun upgradeRequest(): Request {
        val url = RealtimeText.v1Url(config.baseUrl, "stt").newBuilder().apply {
            addQueryParameter("sample_rate", SAMPLE_RATE.toString())
            addQueryParameter("encoding", "pcm")
            addQueryParameter("interim_results", "true")
            addQueryParameter("endpointing", "100")
            addQueryParameter("smart_turn", "0.5")
            config.language?.let { addQueryParameter("language", it) }
            keyterms(config.vocabulary).forEach { addQueryParameter("keyterm", it) }
        }.build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()
    }

    override fun audioFrames(pcm16: ByteArray): List<WsFrame> = pending.push(pcm16).map(WsFrame::Binary)

    override fun commitFrames(): List<WsFrame> {
        val frames = ArrayList<WsFrame>()
        pending.flush()?.let { frames += WsFrame.Binary(it) }
        frames += WsFrame.Text(JSONObject().put("type", "audio.done").toString())
        return frames
    }

    override fun onMessage(text: String): List<DialectEvent> {
        val json = RealtimeText.parse(text) ?: return emptyList()
        return when (json.optString("type")) {
            "transcript.created" -> listOf(DialectEvent.Ready)
            "transcript.partial" -> onPartial(json)
            "transcript.done" -> if (done) emptyList() else {
                done = true
                // Normally empty: the text already arrived as partials.
                val rest = RealtimeText.join(utterances + locked)
                listOf(DialectEvent.Done(rest.ifEmpty { json.optString("text").trim() }))
            }
            "error" -> listOf(DialectEvent.Failed(RealtimeText.errorFor(config.baseUrl.host, json.optString("code"))))
            else -> emptyList()
        }
    }

    private fun onPartial(json: JSONObject): List<DialectEvent> {
        val segment = json.optString("text").trim()
        return when {
            json.optBoolean("speech_final") -> {
                val utterance = RealtimeText.join(locked + segment)
                locked.clear()
                if (utterance.isNotEmpty()) utterances += utterance
                listOf(DialectEvent.Partial(RealtimeText.join(utterances)))
            }
            json.optBoolean("is_final") -> {
                if (segment.isNotEmpty()) locked += segment
                listOf(DialectEvent.Partial(RealtimeText.join(utterances + locked)))
            }
            else -> listOf(DialectEvent.Partial(RealtimeText.join(utterances + locked + segment)))
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000

        /** 100 ms of 16 kHz PCM16. */
        const val FRAME_BYTES = 3_200
        private const val MAX_KEYTERMS = 100
        private const val MAX_KEYTERM_CHARS = 50

        fun keyterms(vocabulary: List<String>): List<String> =
            vocabulary.filter { it.length <= MAX_KEYTERM_CHARS }.take(MAX_KEYTERMS)
    }
}
