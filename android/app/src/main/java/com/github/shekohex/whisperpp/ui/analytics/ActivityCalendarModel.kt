package com.github.shekohex.whisperpp.ui.analytics

import com.github.shekohex.whisperpp.analytics.AnalyticsDayBucket
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Week-column grid for the Insights activity calendar ("Consistency, at a glance"). */
data class ActivityCalendarModel(
    /** Oldest week first; each week has 7 slots starting at the locale's first day, null outside the data. */
    val weeks: List<List<Cell?>>,
    val activeDays: Int,
    val totalDays: Int,
    val busiest: Cell?,
) {
    data class Cell(val date: LocalDate, val sessions: Int, val level: Int)

    companion object {
        const val LEVELS = 5

        /** [buckets] oldest first (zero-filled days); [firstDayOfWeek] from the user's locale. */
        fun from(buckets: List<AnalyticsDayBucket>, firstDayOfWeek: DayOfWeek): ActivityCalendarModel {
            if (buckets.isEmpty()) return ActivityCalendarModel(emptyList(), 0, 0, null)
            val max = buckets.maxOf { it.completedSessionCount }
            val byDate = buckets.associateBy { it.date }
            val first = buckets.first().date
            val last = buckets.last().date
            val start = first.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val weeks = generateSequence(start) { it.plusWeeks(1) }
                .takeWhile { !it.isAfter(last) }
                .map { weekStart ->
                    (0L until 7L).map { offset ->
                        val date = weekStart.plusDays(offset)
                        byDate[date]?.let { Cell(date, it.completedSessionCount, level(it.completedSessionCount, max)) }
                    }
                }
                .toList()
            val cells = weeks.flatten().filterNotNull()
            return ActivityCalendarModel(
                weeks = weeks,
                activeDays = cells.count { it.sessions > 0 },
                totalDays = cells.size,
                busiest = cells.filter { it.sessions > 0 }.maxByOrNull { it.sessions },
            )
        }

        /** 0 for no sessions, else 1..4 relative to the busiest day. */
        fun level(sessions: Int, max: Int): Int {
            if (sessions <= 0 || max <= 0) return 0
            val steps = LEVELS - 1
            return ((sessions.toLong() * steps + max - 1) / max).toInt().coerceIn(1, steps)
        }
    }
}
