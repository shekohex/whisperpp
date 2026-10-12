package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.util.Log
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.domain.dictation.SessionEvent
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeStyle

private const val TAG = "SessionEventHandler"

/** Turns [SessionEvent]s from the dictation session into toasts, notices, feedback, clipboard and bookkeeping. */
class SessionEventHandler(private val deps: Deps) {
    class Deps(
        val context: Context,
        val ui: KeyboardUiState,
        val feedback: KeyboardFeedback,
        val clipboard: ClipboardAccess,
        val undo: KeyboardUndoState,
        val notices: EnhancementNoticeState,
        val analytics: DictationAnalyticsReporter,
        val autoLearn: AutoLearnObserver,
        val completion: DictationCompletionHandler,
        val blockedNotice: () -> String,
    )

    private val context = deps.context

    fun onEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.Notice -> context.showShortToast(context.getString(event.resId))
            is SessionEvent.NoticeText -> context.showShortToast(event.text)
            SessionEvent.ShortPressHint -> deps.ui.showLongPressHint.value = true
            is SessionEvent.RecordingFailed -> {
                if (event.kind != AudioRecorder.ErrorKind.CANCELLED) {
                    context.showShortToast(context.getString(recorderErrorMessageRes(event.kind)))
                }
            }
            is SessionEvent.TranscriptionFailed -> {
                Log.e(TAG, "Transcription failed")
                context.showShortToast(context.getString(R.string.dictation_transcription_failed, event.message))
                deps.feedback.perform()
            }
            SessionEvent.TranscriptionEmpty -> {
                context.showShortToast(context.getString(R.string.toast_transcription_empty))
                deps.feedback.perform()
            }
            SessionEvent.SendBlocked -> context.showShortToast(deps.blockedNotice())
            is SessionEvent.Committed -> {
                deps.undo.showQuickActionIfAvailable()
                deps.feedback.perform()
            }
            is SessionEvent.CommitBlockedByFocus -> deps.feedback.perform()
            is SessionEvent.CopyToClipboard -> {
                deps.clipboard.copy(event.text)
                context.showShortToast(context.getString(R.string.dictation_copied_mode))
            }
            is SessionEvent.CommitFailed -> {
                val message = when (event.reason) {
                    SessionEvent.CommitFailed.Reason.NO_FIELD -> R.string.dictation_copied_no_field
                    SessionEvent.CommitFailed.Reason.BLOCKED -> R.string.dictation_copied_blocked
                    SessionEvent.CommitFailed.Reason.INSERT_FAILED -> R.string.dictation_insert_failed
                }
                // A rejected insert stays in the pending-commit bar (Insert here / Copy) instead.
                if (event.reason != SessionEvent.CommitFailed.Reason.INSERT_FAILED) deps.clipboard.copy(event.text)
                context.showShortToast(context.getString(message))
            }
            is SessionEvent.EnhancementNotice -> deps.notices.show(
                event.text,
                if (event.isError) EnhancementNoticeStyle.ERROR else EnhancementNoticeStyle.INFO,
            )
            SessionEvent.EnhancementApplied -> deps.undo.refreshEnhancement()
            is SessionEvent.EnhancementKeptRaw -> deps.notices.show(
                context.getString(R.string.keyboard_enhancement_kept_raw, event.label),
                EnhancementNoticeStyle.INFO,
            )
            is SessionEvent.Completed -> {
                deps.analytics.onCompleted(event)
                deps.autoLearn.onCompleted(event)
                deps.completion.onCompleted(event)
            }
            is SessionEvent.Discarded -> deps.analytics.onDiscarded(event)
        }
    }
}
