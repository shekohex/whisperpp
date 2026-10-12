package com.github.shekohex.whisperpp.domain.dictation

/**
 * Where a recorded take is in its life (§4.8). A take is written as [RECORDED] as soon as audio
 * exists and moves forward through the pipeline; [FAILED] keeps the audio so it can be retried.
 */
enum class HistoryStatus {
    /** Audio is saved; transcription has not started. */
    RECORDED,
    TRANSCRIBING,

    /** Raw text exists; enhancement and delivery are still ahead. */
    TRANSCRIBED,
    ENHANCING,

    /** Text was delivered (or the user resolved the entry). */
    COMPLETED,

    /** Something went wrong; see the failure reason. The audio is kept until the entry is resolved. */
    FAILED,
    ;

    /** A pipeline stage that only a live run (or a retry) moves on; a dead process leaves these behind. */
    val isInFlight: Boolean
        get() = this == RECORDED || this == TRANSCRIBING || this == TRANSCRIBED || this == ENHANCING

    companion object {
        /** Unknown stored values (e.g. after a downgrade) read as [COMPLETED] so the entry stays usable. */
        fun fromName(name: String?): HistoryStatus = entries.firstOrNull { it.name == name } ?: COMPLETED
    }
}
