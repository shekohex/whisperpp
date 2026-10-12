package com.github.shekohex.whisperpp.domain.dictation

/** Why a take needs attention (or what went wrong without losing the text). Never carries text. */
enum class HistoryFailureReason {
    /** The recorder broke mid-take; the audio is whatever was captured. */
    RECORDER_ERROR,
    TRANSCRIPTION_FAILED,

    /** The provider could not be reached (offline, DNS, timeout, TLS). */
    NETWORK_ERROR,
    EMPTY_TRANSCRIPT,

    /** Enhancement failed; the raw text was used instead. */
    ENHANCEMENT_FAILED,

    /** The editor rejected the text or there was no field to put it in. */
    INSERT_FAILED,

    /** Sending or inserting was blocked (secure field, per-app policy) or the focus moved away. */
    INSERT_BLOCKED,

    /** Cancelled or aborted after the audio was captured. */
    CANCELLED,

    /** The app was killed or stopped while the take was being processed. */
    INTERRUPTED,
    ;

    companion object {
        fun fromName(name: String?): HistoryFailureReason? = entries.firstOrNull { it.name == name }
    }
}
