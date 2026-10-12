package com.github.shekohex.whisperpp.analytics

import java.time.LocalDate

/**
 * Analytics state. Lifetime fields never roll off; [activityCalendar] holds the last
 * [AnalyticsRepository.WINDOW_DAYS] days (16 weeks, oldest first, missing days zero-filled) and
 * [dailyBuckets] its last 7 days. Never contains transcript text.
 */
data class AnalyticsSnapshot(
    val totalCompletedSessions: Int = 0,
    val totalCancelledSessions: Int = 0,
    val totalRecordingDurationMillis: Long = 0,
    val totalRecordingDurationMinutes: Int = 0,
    val totalRawWordCount: Int = 0,
    val totalFinalInsertedWordCount: Int = 0,
    val averageWordsPerMinute: Int = 0,
    val totalInsertedCharacterCount: Int = 0,
    val estimatedKeystrokesSaved: Int = 0,
    /** Lifetime time saved, in minutes (hero number). */
    val estimatedTimeSavedMinutes: Int = 0,
    val dailyBuckets: List<AnalyticsDayBucket> = emptyList(),
    val lifetimeWords: Long = 0,
    val lifetimeSessions: Long = 0,
    val lifetimeRecordingSeconds: Long = 0,
    val lifetimeTimeSavedSeconds: Long = 0,
    /** 24 bins, index = local hour of day. */
    val lifetimeHourlySessions: List<Int> = List(HOURS_PER_DAY) { 0 },
    /** Lifetime usage per provider/model, most sessions first. */
    val modelUsage: List<ModelUsage> = emptyList(),
    val activityCalendar: List<AnalyticsDayBucket> = emptyList(),
) {
    /** Totals for [period]; [AnalyticsPeriod.ALL] uses the lifetime counters. */
    fun periodSnapshot(period: AnalyticsPeriod): AnalyticsPeriodSnapshot {
        val days = period.days
        if (days == null) {
            return AnalyticsPeriodSnapshot(
                period = period,
                words = lifetimeWords,
                sessions = lifetimeSessions,
                recordingSeconds = lifetimeRecordingSeconds,
                timeSavedSeconds = lifetimeTimeSavedSeconds,
                hourlySessions = lifetimeHourlySessions,
                modelUsage = modelUsage,
                dailyBuckets = activityCalendar,
            )
        }
        val buckets = activityCalendar.takeLast(days)
        return AnalyticsPeriodSnapshot(
            period = period,
            words = buckets.sumOf { it.finalInsertedWordCount.toLong() },
            sessions = buckets.sumOf { it.completedSessionCount.toLong() },
            recordingSeconds = buckets.sumOf { it.recordingDurationMillis } / 1_000L,
            timeSavedSeconds = buckets.sumOf { it.timeSavedSeconds },
            hourlySessions = (0 until HOURS_PER_DAY).map { hour -> buckets.sumOf { it.hourlySessions.getOrElse(hour) { 0 } } },
            modelUsage = ModelUsage.merge(buckets.flatMap { it.modelUsage }),
            dailyBuckets = buckets,
        )
    }

    companion object {
        const val HOURS_PER_DAY = 24
    }
}

data class AnalyticsDayBucket(
    val date: LocalDate,
    val completedSessionCount: Int = 0,
    val cancelledSessionCount: Int = 0,
    val recordingDurationMillis: Long = 0,
    val rawWordCount: Int = 0,
    val finalInsertedWordCount: Int = 0,
    val insertedCharacterCount: Int = 0,
    val estimatedTimeSavedMinutes: Int = 0,
    val timeSavedSeconds: Long = 0,
    /** 24 bins; empty for days recorded before analytics schema 2. */
    val hourlySessions: List<Int> = emptyList(),
    val modelUsage: List<ModelUsage> = emptyList(),
)
