package com.github.shekohex.whisperpp.domain.dictation

import java.io.File

/**
 * Durable record of a take for [DictationSession] (§4.8). The session hands over each take as soon as
 * it has audio ([begin]) and reports progress; the implementation applies the history settings
 * (disabled, keep-audio) and owns the file from [begin] on. Every method returns immediately and
 * applies updates in call order; nothing may throw into the session.
 */
interface HistoryRecorder {
    data class Take(
        /** The finished recording; must live where history keeps recordings, or it is not tracked. */
        val file: File,
        val durationMs: Long,
        val appPackage: String?,
        val modeId: String?,
        /** Provider, STT model and language the take is transcribed with. */
        val stats: CompletionStats,
    )

    /** Writes the take as [HistoryStatus.RECORDED] and takes ownership of [Take.file]. */
    fun begin(take: Take): HistoryTicket

    fun transcribing(ticket: HistoryTicket)

    /** Raw transcript text is known; stored right away so a later failure cannot lose it. */
    fun transcribed(ticket: HistoryTicket, rawText: String)

    fun enhancing(ticket: HistoryTicket)

    /**
     * Text reached its destination. [enhancedText] is null when no enhanced text replaced [rawText].
     * [enhancementFailed] marks a completed take whose enhancement fell back to the raw text.
     */
    fun complete(
        ticket: HistoryTicket,
        rawText: String,
        enhancedText: String?,
        stats: CompletionStats,
        enhancementFailed: Boolean = false,
    )

    /** The take did not finish. Whatever text exists is kept, and so is the audio. */
    fun fail(ticket: HistoryTicket, reason: HistoryFailureReason, rawText: String? = null, enhancedText: String? = null)
}
