package com.github.shekohex.whisperpp.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationAnalyticsSessionTest {

    @Test
    fun finalizeEmitsCompletedOutcomeOnlyOnce() {
        val session = DictationAnalyticsSession(sessionId = 42L)

        session.recordRawTranscript("hello world")

        val firstOutcome = session.finalize(durationMs = 15_000)
        val duplicateFinalize = session.finalize(durationMs = 16_000)
        val cancelAfterFinalize = session.cancel(durationMs = 17_000)

        assertTrue(firstOutcome is DictationAnalyticsSession.Outcome.Completed)
        firstOutcome as DictationAnalyticsSession.Outcome.Completed
        assertEquals(42L, firstOutcome.sessionId)
        assertEquals(15_000L, firstOutcome.durationMs)
        assertEquals("hello world", firstOutcome.rawText)
        assertEquals("hello world", firstOutcome.finalInsertedText)
        assertNull(duplicateFinalize)
        assertNull(cancelAfterFinalize)
    }

    @Test
    fun finalizeUsesReplacementTextWithoutDoubleCounting() {
        val session = DictationAnalyticsSession(sessionId = 7L)

        session.recordRawTranscript("raw words")
        session.recordFinalInsertedText("enhanced final words")

        val outcome = session.finalize(durationMs = 30_000)
        val duplicateFinalize = session.finalize(durationMs = 31_000)

        assertTrue(outcome is DictationAnalyticsSession.Outcome.Completed)
        outcome as DictationAnalyticsSession.Outcome.Completed
        assertEquals("raw words", outcome.rawText)
        assertEquals("enhanced final words", outcome.finalInsertedText)
        assertNull(duplicateFinalize)
    }

    @Test
    fun cancelEmitsCancelledOutcomeWithoutCompletedPayload() {
        val session = DictationAnalyticsSession(sessionId = 9L)

        session.recordRawTranscript("ignored raw")

        val cancelled = session.cancel(durationMs = 45_000)
        val finalizeAfterCancel = session.finalize(durationMs = 46_000)

        assertTrue(cancelled is DictationAnalyticsSession.Outcome.Cancelled)
        cancelled as DictationAnalyticsSession.Outcome.Cancelled
        assertEquals(9L, cancelled.sessionId)
        assertEquals(45_000L, cancelled.durationMs)
        assertNull(finalizeAfterCancel)
    }
}
