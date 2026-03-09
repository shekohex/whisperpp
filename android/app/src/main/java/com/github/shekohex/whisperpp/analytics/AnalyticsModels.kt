package com.github.shekohex.whisperpp.analytics

import java.time.LocalDate

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
    val estimatedTimeSavedMinutes: Int = 0,
    val dailyBuckets: List<AnalyticsDayBucket> = emptyList(),
)

data class AnalyticsDayBucket(
    val date: LocalDate,
    val completedSessionCount: Int = 0,
    val cancelledSessionCount: Int = 0,
    val recordingDurationMillis: Long = 0,
    val rawWordCount: Int = 0,
    val finalInsertedWordCount: Int = 0,
    val insertedCharacterCount: Int = 0,
    val estimatedTimeSavedMinutes: Int = 0,
)
