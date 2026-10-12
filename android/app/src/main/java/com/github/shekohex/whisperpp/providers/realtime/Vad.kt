package com.github.shekohex.whisperpp.providers.realtime

/** Turn detection sent in `transcription_session.update` (`turn_detection`). */
sealed interface Vad {
    /** `turn_detection: null`: the whole take is one buffer, committed by [OpenAiRealtimeSession.commit]. */
    data object None : Vad

    /** Server VAD commits each utterance on silence, so transcripts (and partials) arrive while speaking. */
    data class ServerVad(
        val threshold: Double = 0.5,
        val prefixPaddingMs: Int = 300,
        val silenceDurationMs: Int = 500,
    ) : Vad
}
