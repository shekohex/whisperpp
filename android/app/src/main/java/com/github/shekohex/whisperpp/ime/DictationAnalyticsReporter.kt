package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.analytics.AnalyticsModelSample
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.DictationAnalyticsSession
import com.github.shekohex.whisperpp.domain.dictation.CompletionStats
import com.github.shekohex.whisperpp.domain.dictation.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Records the outcome of a finished or discarded dictation run; only sizes and latencies, never text. */
class DictationAnalyticsReporter(
    private val scope: CoroutineScope,
    private val analyticsRepository: AnalyticsRepository,
) {
    fun onCompleted(event: SessionEvent.Completed) {
        val analytics = DictationAnalyticsSession(sessionId = 0L).apply {
            recordRawTranscript(event.rawText)
            recordFinalInsertedText(event.finalText)
        }
        analytics.finalize(event.durationMs, event.stats.toAnalyticsSample())?.let(::persist)
    }

    fun onDiscarded(event: SessionEvent.Discarded) {
        DictationAnalyticsSession(sessionId = 0L).cancel(event.durationMs)?.let(::persist)
    }

    private fun persist(outcome: DictationAnalyticsSession.Outcome) {
        scope.launch(Dispatchers.IO) { analyticsRepository.recordOutcome(outcome) }
    }
}

internal fun CompletionStats.toAnalyticsSample() = AnalyticsModelSample(
    providerId = providerId,
    modelId = sttModelId,
    transcriptionLatencyMs = transcriptionLatencyMs,
    enhancementProviderId = enhancementProviderId,
    enhancementModelId = enhancementModelId,
    enhancementLatencyMs = enhancementLatencyMs,
)
