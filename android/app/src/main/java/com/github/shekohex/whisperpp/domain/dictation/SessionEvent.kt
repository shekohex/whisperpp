package com.github.shekohex.whisperpp.domain.dictation

import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.dictation.FocusKey

/** One-shot outputs of [DictationSession] for toasts, notices, undo affordances and analytics. */
sealed interface SessionEvent {
    data class Notice(@param:StringRes val resId: Int) : SessionEvent

    data class NoticeText(val text: String) : SessionEvent

    /** Released too early to count as a recording (< min duration). */
    data object ShortPressHint : SessionEvent

    data class RecordingFailed(val kind: AudioRecorder.ErrorKind?) : SessionEvent

    /** The provider call failed; distinct from [TranscriptionEmpty] (F-07). */
    data class TranscriptionFailed(val message: String) : SessionEvent

    data object TranscriptionEmpty : SessionEvent

    /** Sending audio/text is not allowed for the current editor (secure field or per-app policy). */
    data object SendBlocked : SessionEvent

    /** Raw text was inserted; dictation undo is available for the focus. */
    data class Committed(val text: String) : SessionEvent

    /** The transcript belongs to another field; UI offers Insert here / Copy / Discard (G-07). */
    data class CommitBlockedByFocus(val text: String) : SessionEvent

    /**
     * Nowhere to insert (no editor, or sending got blocked); UI falls back to the clipboard.
     * [Reason.INSERT_FAILED]: the editor rejected the insert; the text is held in the pending-commit bar.
     */
    data class CommitFailed(val text: String, val reason: Reason) : SessionEvent {
        enum class Reason { NO_FIELD, BLOCKED, INSERT_FAILED }
    }

    data class EnhancementNotice(val text: String, val isError: Boolean) : SessionEvent

    /** Enhanced text replaced the raw segment; enhancement undo is available. */
    data object EnhancementApplied : SessionEvent

    /** Enhancement finished but the field lost focus, so the raw text stays. [label] names provider/model. */
    data class EnhancementKeptRaw(val label: String) : SessionEvent

    /**
     * Terminal analytics: text was inserted into [focus] by run [runId]. Follow-ups such as auto-send
     * target [focus] only, never whatever field is focused when the event is handled.
     */
    data class Completed(
        val rawText: String,
        val finalText: String,
        val durationMs: Long,
        val focus: FocusKey,
        val runId: Long,
    ) : SessionEvent

    /** Terminal analytics: a started recording was discarded. */
    data class Discarded(val durationMs: Long) : SessionEvent
}
