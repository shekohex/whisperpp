package com.github.shekohex.whisperpp.providers.realtime

import okhttp3.Request
import org.json.JSONObject

/**
 * The two wire shapes of OpenAI's `/v1/realtime` transcription sessions. [GA] is the current API
 * (`session.update` with `type: "transcription"`, no beta header); [BETA] is the `realtime=v1`
 * preview (`transcription_session.update`) that some gateways and older servers still speak.
 * [OpenAiRealtimeSession] starts with [GA] and falls back to [BETA] once when the server rejects it.
 */
internal enum class RealtimeSchema {
    GA,
    BETA;

    fun applyHeaders(builder: Request.Builder) {
        if (this == BETA) builder.header("OpenAI-Beta", "realtime=v1")
    }

    /** First message on the socket: model, language, vocabulary prompt and turn detection. */
    fun sessionUpdate(modelId: String, language: String, prompt: String, vad: Vad): String {
        val transcription = JSONObject().put("model", modelId)
        val code = language.trim()
        if (code.isNotEmpty() && code != "auto") transcription.put("language", code)
        if (prompt.isNotBlank()) transcription.put("prompt", prompt)
        val turnDetection = turnDetection(vad)
        return when (this) {
            GA -> update(
                JSONObject()
                    .put("format", JSONObject().put("type", "audio/pcm").put("rate", API_SAMPLE_RATE))
                    .put("transcription", transcription)
                    .put("turn_detection", turnDetection),
            )
            BETA -> JSONObject()
                .put("type", BETA_UPDATE)
                .put(
                    "session",
                    JSONObject()
                        .put("input_audio_format", "pcm16")
                        .put("input_audio_transcription", transcription)
                        .put("turn_detection", turnDetection),
                )
                .toString()
        }
    }

    /** Stop sequence step 1: server VAD off, so nothing is committed behind our back. */
    fun disableTurnDetection(): String = when (this) {
        GA -> update(JSONObject().put("turn_detection", JSONObject.NULL))
        BETA -> JSONObject()
            .put("type", BETA_UPDATE)
            .put("session", JSONObject().put("turn_detection", JSONObject.NULL))
            .toString()
    }

    /** Whether an echoed session carries no active turn detection (null or absent). */
    fun vadDisabledIn(event: JSONObject): Boolean {
        val session = event.optJSONObject("session") ?: return true
        val input = session.optJSONObject("audio")?.optJSONObject("input")
        return session.optJSONObject("turn_detection") == null && input?.optJSONObject("turn_detection") == null
    }

    private fun update(input: JSONObject): String = JSONObject()
        .put("type", "session.update")
        .put(
            "session",
            JSONObject().put("type", "transcription").put("audio", JSONObject().put("input", input)),
        )
        .toString()

    private fun turnDetection(vad: Vad): Any = when (vad) {
        Vad.None -> JSONObject.NULL
        is Vad.ServerVad -> JSONObject().apply {
            put("type", "server_vad")
            vad.threshold?.let { put("threshold", it) }
            vad.prefixPaddingMs?.let { put("prefix_padding_ms", it) }
            put("silence_duration_ms", vad.silenceDurationMs)
        }
    }

    companion object {
        const val API_SAMPLE_RATE = 24_000
        private const val BETA_UPDATE = "transcription_session.update"

        /** `session.updated` (GA) or `transcription_session.updated` (beta): the update was applied. */
        fun isSessionUpdated(type: String): Boolean = type == "session.updated" || type == "transcription_session.updated"
    }
}
