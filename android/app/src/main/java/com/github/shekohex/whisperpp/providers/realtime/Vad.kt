package com.github.shekohex.whisperpp.providers.realtime

/** Turn detection sent in the session update (`turn_detection`). */
sealed interface Vad {
    /** `turn_detection: null`: the whole take is one buffer, committed by [OpenAiRealtimeSession.commit]. */
    data object None : Vad

    /**
     * Server VAD commits each utterance on silence, so transcripts (and partials) arrive while speaking.
     * Only the fields that are set are sent: the default is exactly `{type, silence_duration_ms}`, the
     * smallest shape every OpenAI-compatible gateway accepts.
     */
    data class ServerVad(
        val silenceDurationMs: Int = 400,
        val threshold: Double? = null,
        val prefixPaddingMs: Int? = null,
    ) : Vad
}
