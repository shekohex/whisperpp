package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.dictation.DictationRun.TakeResolution
import java.io.File

/**
 * Keeps a run's take in history (§4.8): the row is written as soon as the recording has audio, every
 * pipeline stage updates it, and a take that does not finish is marked FAILED with its audio kept.
 * Resolves each take exactly once; without a history recorder every call is a no-op.
 */
internal class TakeHistory(
    private val deps: DictationSession.Deps,
    private val emit: (SessionEvent) -> Unit,
) {
    /** A transcript held back from its field (focus moved, insert rejected); resolved when the user acts on it. */
    class HeldTake(val ticket: HistoryTicket, val rawText: String, val enhancedText: String?, val stats: CompletionStats)

    /** The recording stopped with audio: save the take before anything can go wrong. */
    fun begin(r: DictationRun, file: File, durationMs: Long, transcribing: Boolean = true) {
        val history = deps.history ?: return
        val plan = r.plan ?: return
        val ticket = history.begin(
            HistoryRecorder.Take(
                file = file,
                durationMs = durationMs,
                appPackage = r.focus.packageName,
                modeId = plan.options.modeId,
                stats = r.stats(),
            )
        )
        r.ticket = ticket
        if (transcribing) history.transcribing(ticket)
    }

    /** A partial capture (recorder error, interrupted run) is worth keeping only if it holds real audio. */
    fun saveFailedCapture(r: DictationRun, file: File, durationMs: Long, reason: HistoryFailureReason): Boolean {
        if (r.ticket != null || deps.history == null) return fail(r, reason)
        if (!file.isFile || file.length() < MIN_SALVAGE_BYTES) return false
        begin(r, file, durationMs, transcribing = false)
        return fail(r, reason)
    }

    fun transcribed(r: DictationRun, rawText: String) {
        val ticket = r.ticket ?: return
        if (r.resolution == TakeResolution.OPEN) deps.history?.transcribed(ticket, rawText)
    }

    fun enhancing(r: DictationRun) {
        val ticket = r.ticket ?: return
        if (r.resolution == TakeResolution.OPEN) deps.history?.enhancing(ticket)
    }

    fun complete(r: DictationRun, rawText: String, finalText: String, stats: CompletionStats) {
        val ticket = r.ticket ?: return
        if (r.resolution != TakeResolution.OPEN) return
        r.resolution = TakeResolution.COMPLETED
        deps.history?.complete(ticket, rawText, finalText.takeIf { it != rawText }, stats, r.enhancementFailed)
    }

    /** Marks the take FAILED (once); true when its audio is now safe in history. */
    fun fail(r: DictationRun, reason: HistoryFailureReason, rawText: String? = null, enhancedText: String? = null): Boolean {
        val ticket = r.ticket ?: return false
        when (r.resolution) {
            TakeResolution.COMPLETED -> return false
            TakeResolution.FAILED -> return true
            TakeResolution.OPEN -> Unit
        }
        r.resolution = TakeResolution.FAILED
        deps.history?.fail(ticket, reason, rawText, enhancedText)
        return true
    }

    /** [fail], then tells the user the recording is safe in History; true when a take was saved. */
    fun failAndNotify(
        r: DictationRun,
        reason: HistoryFailureReason,
        rawText: String? = null,
        enhancedText: String? = null,
    ): Boolean {
        val saved = fail(r, reason, rawText, enhancedText)
        if (saved) emit(SessionEvent.RecordingSaved(reason))
        return saved
    }

    /**
     * Last word on a run's take, from the session's single terminal transition. Audio is never deleted
     * here once history has the take; one the pipeline did not resolve is a cancel or abort and is
     * saved as FAILED. Without history the audio is simply removed.
     */
    fun settle(r: DictationRun) {
        if (r.ticket == null && r.salvageOnFinish) {
            r.plan?.let { saveFailedCapture(r, it.audioFile, r.durationMs, r.abortReason ?: HistoryFailureReason.INTERRUPTED) }
        }
        if (r.ticket == null) {
            r.plan?.audioFile?.delete()
            return
        }
        if (r.resolution == TakeResolution.OPEN) {
            failAndNotify(r, r.abortReason ?: HistoryFailureReason.CANCELLED)
        }
    }

    /** The take of a held transcript, once the user inserted, copied or dismissed it. */
    fun resolveHeld(held: HeldTake) {
        deps.history?.complete(held.ticket, held.rawText, held.enhancedText, held.stats)
    }

    /** Holds a failed take's text for the pending-commit bar and records the failure. */
    fun hold(r: DictationRun, reason: HistoryFailureReason, rawText: String, finalText: String): HeldTake? {
        val ticket = r.ticket ?: return null
        val enhanced = finalText.takeIf { it != rawText }
        fail(r, reason, rawText, enhanced)
        return HeldTake(ticket, rawText, enhanced, r.stats())
    }

    companion object {
        /** About 0.1 s of 16 kHz mono audio past the WAV header. */
        const val MIN_SALVAGE_BYTES = 44L + 3_200L
    }
}
