package com.github.shekohex.whisperpp.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsFormatterTest {

    @Test
    fun formatTimeSaved_usesMinutesForSmallTotals_andHoursOnceAggregateGrows() {
        assertEquals("12 min", AnalyticsFormatter.formatTimeSaved(12))
        assertEquals("2h 5m", AnalyticsFormatter.formatTimeSaved(125))
    }

    @Test
    fun calculateWordsPerMinute_usesFinalInsertedWordsOnly_andReturnsZeroForEmptyInputs() {
        assertEquals(120, AnalyticsFormatter.calculateWordsPerMinute(finalInsertedWords = 60, durationMillis = 30_000))
        assertEquals(0, AnalyticsFormatter.calculateWordsPerMinute(finalInsertedWords = 0, durationMillis = 30_000))
        assertEquals(0, AnalyticsFormatter.calculateWordsPerMinute(finalInsertedWords = 60, durationMillis = 0))
    }

    @Test
    fun formatHeroSummary_keepsTypingComparisonPrimary_withSoftEstimateCaveat() {
        val summary = AnalyticsFormatter.formatHeroSummary(timeSavedMinutes = 42)

        assertTrue(summary.contains("typing", ignoreCase = true))
        assertTrue(summary.contains("estimate", ignoreCase = true))
        assertFalse(summary.contains("privacy", ignoreCase = true))
    }
}
