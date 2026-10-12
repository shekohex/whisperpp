package com.github.shekohex.whisperpp.ui.analytics

import java.text.DecimalFormatSymbols
import java.util.Locale

/** Pure number/duration formatting for the dashboard and Insights; units come from string resources. */
object StatFormat {
    data class HoursMinutes(val hours: Long, val minutes: Long)

    private const val THOUSAND = 1_000L
    private const val MILLION = 1_000_000L

    fun splitDuration(totalSeconds: Long): HoursMinutes {
        val minutes = totalSeconds.coerceAtLeast(0) / 60
        return HoursMinutes(hours = minutes / 60, minutes = minutes % 60)
    }

    /**
     * "41h 30m" with [hoursMinutesTemplate] = `%1$dh %2$dm`; "30m" with [minutesTemplate] = `%1$dm`
     * when under an hour.
     */
    fun formatDuration(
        totalSeconds: Long,
        hoursMinutesTemplate: String,
        minutesTemplate: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val parts = splitDuration(totalSeconds)
        return if (parts.hours > 0) {
            String.format(locale, hoursMinutesTemplate, parts.hours, parts.minutes)
        } else {
            String.format(locale, minutesTemplate, parts.minutes)
        }
    }

    /** 950 → "950", 12_300 → "12.3K", 165_800 → "165.8K", 2_000_000 → "2M". */
    fun formatCompact(value: Long, locale: Locale = Locale.getDefault()): String {
        val safe = value.coerceAtLeast(0)
        return when {
            safe < THOUSAND -> String.format(locale, "%d", safe)
            safe < MILLION -> withSuffix(safe, THOUSAND, "K", locale)
            else -> withSuffix(safe, MILLION, "M", locale)
        }
    }

    /** Milliseconds as seconds with one decimal: 1_250 → "1.2" (or "1,2" by locale). */
    fun formatSeconds(millis: Long, locale: Locale = Locale.getDefault()): String {
        val tenths = millis.coerceAtLeast(0) / 100
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return String.format(locale, "%d%s%d", tenths / 10, separator, tenths % 10)
    }

    /** Whole percent of [part] in [total]; 0 when [total] is 0. */
    fun percent(part: Long, total: Long): Int =
        if (total <= 0) 0 else ((part.coerceAtLeast(0) * 100.0) / total).toInt().coerceIn(0, 100)

    private fun withSuffix(value: Long, unit: Long, suffix: String, locale: Locale): String {
        // Truncate, so 999_950 never reads "1000.0K" and 12_390 reads "12.3K" like the reference.
        val tenths = value / (unit / 10)
        val text = if (tenths % 10 == 0L) {
            String.format(locale, "%d", tenths / 10)
        } else {
            String.format(locale, "%d%s%d", tenths / 10, DecimalFormatSymbols.getInstance(locale).decimalSeparator, tenths % 10)
        }
        return text + suffix
    }
}
