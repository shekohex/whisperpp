package com.github.shekohex.whisperpp.providers.realtime

import okhttp3.Request
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/**
 * ElevenLabs Scribe realtime (`/v1/speech-to-text/realtime`) with manual commit: audio goes as
 * base64 JSON, the session starts on `session_started`, and the first committed transcript after
 * our commit message is the final text.
 */
internal class ElevenLabsDialect(private val config: StreamingConfig) : StreamingDialect {
    private val committed = ArrayList<String>()
    private var commitSent = false
    private var done = false

    override val awaitsReady: Boolean = true

    override fun upgradeRequest(): Request {
        val url = RealtimeText.v1Url(config.baseUrl, "speech-to-text", "realtime").newBuilder().apply {
            addQueryParameter("model_id", config.modelId)
            addQueryParameter("audio_format", "pcm_16000")
            addQueryParameter("commit_strategy", "manual")
            addQueryParameter("no_verbatim", "true")
            config.language?.let { addQueryParameter("language_code", it) }
            keyterms(config.vocabulary).forEach { addQueryParameter("keyterms", it) }
        }.build()
        return Request.Builder()
            .url(url)
            .header("xi-api-key", config.apiKey)
            .build()
    }

    override fun audioFrames(pcm16: ByteArray): List<WsFrame> =
        listOf(WsFrame.Text(chunk(pcm16.toByteString().base64(), commit = false)))

    override fun commitFrames(): List<WsFrame> {
        commitSent = true
        return listOf(WsFrame.Text(chunk("", commit = true)))
    }

    override fun onMessage(text: String): List<DialectEvent> {
        val json = RealtimeText.parse(text) ?: return emptyList()
        val kind = json.optString("message_type")
        return when {
            kind == "session_started" -> listOf(DialectEvent.Ready)
            kind == "partial_transcript" ->
                listOf(DialectEvent.Partial(RealtimeText.cumulative(committed, json.optString("text"))))
            kind == "committed_transcript" || kind == "committed_transcript_with_timestamps" -> onCommitted(json)
            kind in ERROR_KINDS -> listOf(DialectEvent.Failed(RealtimeText.errorFor(config.baseUrl.host, kind)))
            else -> emptyList()
        }
    }

    private fun onCommitted(json: JSONObject): List<DialectEvent> {
        if (done) return emptyList()
        val text = json.optString("text").trim()
        if (text.isNotEmpty()) committed += text
        val joined = RealtimeText.join(committed)
        if (!commitSent) return listOf(DialectEvent.Partial(joined))
        done = true
        return listOf(DialectEvent.Done(joined))
    }

    private fun chunk(base64: String, commit: Boolean): String = JSONObject()
        .put("message_type", "input_audio_chunk")
        .put("audio_base_64", base64)
        .put("commit", commit)
        .put("sample_rate", SAMPLE_RATE)
        .toString()

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MAX_KEYTERMS = 50
        private const val MAX_KEYTERM_CHARS = 20
        private const val FORBIDDEN_KEYTERM_CHARS = "<>{}[]\\"
        private val ERROR_KINDS = setOf(
            "error", "auth_error", "quota_exceeded", "rate_limited", "resource_exhausted",
            "session_time_limit_exceeded", "input_error", "chunk_size_exceeded", "transcriber_error",
        )

        /** Keyterms ElevenLabs accepts: at most 50, each short and free of markup characters. */
        fun keyterms(vocabulary: List<String>): List<String> = vocabulary
            .filter { term -> term.length <= MAX_KEYTERM_CHARS && term.none { it in FORBIDDEN_KEYTERM_CHARS } }
            .take(MAX_KEYTERMS)
    }
}
