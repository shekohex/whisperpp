package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.providers.withPathSuffix
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Speechmatics realtime (`/v2`): `StartRecognition` on open, audio starts after `RecognitionStarted`,
 * `EndOfStream` carries the number of audio chunks sent, `AddTranscript` finals accumulate and
 * `EndOfTranscript` ends the session.
 */
internal class SpeechmaticsDialect(private val config: StreamingConfig) : StreamingDialect {
    private val finals = ArrayList<String>()
    private var chunks = 0
    private var done = false

    override val awaitsReady: Boolean = true

    override fun upgradeRequest(): Request = Request.Builder()
        .url(config.baseUrl.withPathSuffix("v2"))
        .header("Authorization", "Bearer ${config.apiKey}")
        .build()

    override fun openFrames(): List<WsFrame> {
        val audio = JSONObject().put("type", "raw").put("encoding", "pcm_s16le").put("sample_rate", SAMPLE_RATE)
        val transcription = JSONObject()
            .put("language", languageFor(config.language))
            .put("enable_partials", true)
            .put("operating_point", operatingPoint(config.modelId))
            .put("max_delay", MAX_DELAY_SECONDS)
            .put("max_delay_mode", "flexible")
        if (config.vocabulary.isNotEmpty()) {
            val vocab = JSONArray()
            config.vocabulary.forEach { vocab.put(JSONObject().put("content", it)) }
            transcription.put("additional_vocab", vocab)
        }
        val start = JSONObject()
            .put("message", "StartRecognition")
            .put("audio_format", audio)
            .put("transcription_config", transcription)
        return listOf(WsFrame.Text(start.toString()))
    }

    override fun audioFrames(pcm16: ByteArray): List<WsFrame> {
        chunks += 1
        return listOf(WsFrame.Binary(pcm16))
    }

    override fun commitFrames(): List<WsFrame> = listOf(
        WsFrame.Text(JSONObject().put("message", "EndOfStream").put("last_seq_no", chunks).toString()),
    )

    override fun onMessage(text: String): List<DialectEvent> {
        val json = RealtimeText.parse(text) ?: return emptyList()
        return when (json.optString("message")) {
            "RecognitionStarted" -> listOf(DialectEvent.Ready)
            "AddPartialTranscript" -> listOf(DialectEvent.Partial(clean(finals + transcriptOf(json))))
            "AddTranscript" -> {
                transcriptOf(json).takeIf { it.isNotBlank() }?.let { finals += it }
                listOf(DialectEvent.Partial(clean(finals)))
            }
            "EndOfTranscript" -> if (done) emptyList() else {
                done = true
                listOf(DialectEvent.Done(clean(finals)))
            }
            "Error" -> listOf(DialectEvent.Failed(RealtimeText.errorFor(config.baseUrl.host, json.optString("type"))))
            else -> emptyList()
        }
    }

    private fun transcriptOf(json: JSONObject): String = json.optJSONObject("metadata")?.optString("transcript").orEmpty()

    /** Joins the pieces and removes the space the join puts before punctuation. */
    private fun clean(parts: List<String>): String = RealtimeText.join(parts).replace(SPACE_BEFORE_PUNCTUATION, "$1")

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MAX_DELAY_SECONDS = 2.0
        private val SPACE_BEFORE_PUNCTUATION = Regex(" ([.,!?;:'])")

        /** Auto-detect is not offered; English is the default, and Chinese is Mandarin (`cmn`). */
        fun languageFor(language: String?): String {
            val base = language?.substringBefore('-')?.lowercase().orEmpty()
            return when (base) {
                "" -> "en"
                "zh" -> "cmn"
                else -> base
            }
        }

        fun operatingPoint(modelId: String): String = if (modelId.contains("standard", ignoreCase = true)) "standard" else "enhanced"
    }
}
