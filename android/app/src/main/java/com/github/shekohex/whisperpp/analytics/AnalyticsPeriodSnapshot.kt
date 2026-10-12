package com.github.shekohex.whisperpp.analytics

/** Insights stat tiles and charts for one [AnalyticsPeriod]. */
data class AnalyticsPeriodSnapshot(
    val period: AnalyticsPeriod,
    val words: Long = 0,
    val sessions: Long = 0,
    val recordingSeconds: Long = 0,
    val timeSavedSeconds: Long = 0,
    /** 24 bins, index = local hour of day ("Peak dictation hours"). */
    val hourlySessions: List<Int> = List(AnalyticsSnapshot.HOURS_PER_DAY) { 0 },
    /** Most sessions first ("Model usage" / "Model performance"). */
    val modelUsage: List<ModelUsage> = emptyList(),
    /** Oldest first. */
    val dailyBuckets: List<AnalyticsDayBucket> = emptyList(),
)
