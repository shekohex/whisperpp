package com.github.shekohex.whisperpp.analytics

class DictationAnalyticsSession(
    val sessionId: Long,
) {
    private var rawTranscript: String? = null
    private var finalInsertedTranscript: String? = null
    private var terminalOutcomeRecorded = false

    fun recordRawTranscript(rawText: String) {
        if (terminalOutcomeRecorded) return
        rawTranscript = rawText.trim().takeIf { it.isNotEmpty() }
    }

    fun recordFinalInsertedText(finalInsertedText: String) {
        if (terminalOutcomeRecorded) return
        finalInsertedTranscript = finalInsertedText.trim().takeIf { it.isNotEmpty() }
    }

    fun finalize(durationMs: Long): Outcome? {
        if (terminalOutcomeRecorded) return null
        val rawText = rawTranscript ?: return null
        terminalOutcomeRecorded = true
        return Outcome.Completed(
            sessionId = sessionId,
            durationMs = durationMs.coerceAtLeast(0L),
            rawText = rawText,
            finalInsertedText = finalInsertedTranscript ?: rawText,
        )
    }

    fun cancel(durationMs: Long): Outcome? {
        if (terminalOutcomeRecorded) return null
        terminalOutcomeRecorded = true
        return Outcome.Cancelled(
            sessionId = sessionId,
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    sealed class Outcome {
        abstract val sessionId: Long
        abstract val durationMs: Long

        data class Completed(
            override val sessionId: Long,
            override val durationMs: Long,
            val rawText: String,
            val finalInsertedText: String,
        ) : Outcome()

        data class Cancelled(
            override val sessionId: Long,
            override val durationMs: Long,
        ) : Outcome()
    }
}
