package com.github.shekohex.whisperpp.ui.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StatFormatTest {
    private val hm = "%1\$dh %2\$dm"
    private val m = "%1\$dm"

    @Test
    fun formatDuration_hoursAndMinutes() {
        assertEquals("41h 30m", StatFormat.formatDuration(41 * 3_600L + 30 * 60L + 59, hm, m, Locale.US))
        assertEquals("1h 0m", StatFormat.formatDuration(3_600L, hm, m, Locale.US))
    }

    @Test
    fun formatDuration_underAnHour_andNegative() {
        assertEquals("30m", StatFormat.formatDuration(30 * 60L, hm, m, Locale.US))
        assertEquals("0m", StatFormat.formatDuration(59L, hm, m, Locale.US))
        assertEquals("0m", StatFormat.formatDuration(-5L, hm, m, Locale.US))
    }

    @Test
    fun formatCompact_thousandsAndMillions() {
        assertEquals("950", StatFormat.formatCompact(950, Locale.US))
        assertEquals("165.8K", StatFormat.formatCompact(165_800, Locale.US))
        assertEquals("12.3K", StatFormat.formatCompact(12_390, Locale.US))
        assertEquals("21K", StatFormat.formatCompact(21_000, Locale.US))
        assertEquals("999.9K", StatFormat.formatCompact(999_999, Locale.US))
        assertEquals("2M", StatFormat.formatCompact(2_000_000, Locale.US))
        assertEquals("1.5M", StatFormat.formatCompact(1_530_000, Locale.US))
        assertEquals("0", StatFormat.formatCompact(-3, Locale.US))
    }

    @Test
    fun formatCompact_usesLocaleDecimalSeparator() {
        assertEquals("165,8K", StatFormat.formatCompact(165_800, Locale.GERMANY))
    }

    @Test
    fun formatSeconds_oneDecimal() {
        assertEquals("1.2", StatFormat.formatSeconds(1_250, Locale.US))
        assertEquals("0.0", StatFormat.formatSeconds(40, Locale.US))
    }

    @Test
    fun percent_handlesZeroTotal() {
        assertEquals(0, StatFormat.percent(5, 0))
        assertEquals(33, StatFormat.percent(1, 3))
        assertEquals(100, StatFormat.percent(3, 3))
    }
}
