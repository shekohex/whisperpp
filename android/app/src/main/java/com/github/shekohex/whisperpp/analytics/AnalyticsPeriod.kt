package com.github.shekohex.whisperpp.analytics

/** Insights period selector (§4.9). [days] is null for all time. */
enum class AnalyticsPeriod(val days: Int?) {
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    LAST_90_DAYS(90),
    ALL(null),
}
