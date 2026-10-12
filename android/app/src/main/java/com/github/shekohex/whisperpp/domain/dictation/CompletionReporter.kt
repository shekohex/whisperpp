package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.modes.OutputMode

/** Final reporting of a run whose text reached its destination: analytics and the history entry. */
internal class CompletionReporter(
    private val takeHistory: TakeHistory,
    private val emit: (SessionEvent) -> Unit,
) {
    /** Text reached the field: analytics event plus the history entry's completion. */
    fun complete(r: DictationRun, raw: String, finalText: String) {
        val stats = r.stats()
        emit(
            SessionEvent.Completed(
                rawText = raw,
                finalText = finalText,
                durationMs = r.durationMs,
                focus = r.focus,
                runId = r.id,
                stats = stats,
                inserted = r.options.outputMode != OutputMode.CLIPBOARD,
                autoSend = r.options.autoSend,
            )
        )
        takeHistory.complete(r, raw, finalText, stats)
    }
}
