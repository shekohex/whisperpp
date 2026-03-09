package com.github.shekohex.whisperpp.analytics

import kotlin.math.max
import kotlin.math.roundToInt

object AnalyticsFormatter {
    const val TYPING_BASELINE_WORDS_PER_MINUTE = 40

    fun formatTimeSaved(totalMinutes: Int): String {
        val safeMinutes = max(totalMinutes, 0)
        val hours = safeMinutes / 60
        val minutes = safeMinutes % 60
        return if (hours == 0) {
            "$safeMinutes min"
        } else {
            "${hours}h ${minutes}m"
        }
    }

    fun formatHeroSummary(timeSavedMinutes: Int): String {
        val formatted = formatTimeSaved(timeSavedMinutes)
        return if (timeSavedMinutes > 0) {
            "You've saved about $formatted compared to typing so far, based on a gentle estimate."
        } else {
            "Your time-saved story will appear here as you keep choosing Whisper++ over typing, based on a gentle estimate."
        }
    }

    fun calculateWordsPerMinute(finalInsertedWords: Int, durationMillis: Long): Int {
        if (finalInsertedWords <= 0 || durationMillis <= 0L) {
            return 0
        }
        return ((finalInsertedWords * 60_000.0) / durationMillis.toDouble()).roundToInt()
    }

    fun calculateTimeSavedMinutes(finalInsertedWords: Int, durationMillis: Long): Int {
        if (finalInsertedWords <= 0 || durationMillis <= 0L) {
            return 0
        }
        val typingMinutes = finalInsertedWords.toDouble() / TYPING_BASELINE_WORDS_PER_MINUTE.toDouble()
        val actualMinutes = durationMillis.toDouble() / 60_000.0
        return max((typingMinutes - actualMinutes).roundToInt(), 0)
    }

    fun estimateKeystrokesSaved(insertedCharacterCount: Int): Int {
        return max(insertedCharacterCount, 0)
    }
}
