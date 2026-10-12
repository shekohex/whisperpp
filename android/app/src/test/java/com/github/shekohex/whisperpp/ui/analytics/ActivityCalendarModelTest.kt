package com.github.shekohex.whisperpp.ui.analytics

import com.github.shekohex.whisperpp.analytics.AnalyticsDayBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ActivityCalendarModelTest {
    // 2026-10-12 is a Monday.
    private val today = LocalDate.of(2026, 10, 12)

    private fun window(days: Int, sessions: (Int) -> Int = { 0 }) = (days - 1 downTo 0).map { offset ->
        AnalyticsDayBucket(date = today.minusDays(offset.toLong()), completedSessionCount = sessions(offset))
    }

    @Test
    fun sixteenWeeks_alignedToFirstDayOfWeek() {
        val model = ActivityCalendarModel.from(window(112), DayOfWeek.MONDAY)

        // 112 days ending on a Monday span 17 Monday-start weeks; the first starts before the data.
        assertEquals(17, model.weeks.size)
        model.weeks.forEach { assertEquals(7, it.size) }
        assertNull(model.weeks.first()[0])
        assertEquals(today, model.weeks.last()[0]?.date)
        assertNull(model.weeks.last()[1])
        assertEquals(112, model.totalDays)
    }

    @Test
    fun sundayStart_shiftsColumns() {
        val model = ActivityCalendarModel.from(window(7), DayOfWeek.SUNDAY)

        assertEquals(today.minusDays(1), model.weeks.last()[0]?.date)
        assertEquals(today, model.weeks.last()[1]?.date)
    }

    @Test
    fun levels_scaleToBusiestDay() {
        assertEquals(0, ActivityCalendarModel.level(0, 10))
        assertEquals(1, ActivityCalendarModel.level(1, 10))
        assertEquals(2, ActivityCalendarModel.level(5, 10))
        assertEquals(3, ActivityCalendarModel.level(7, 10))
        assertEquals(4, ActivityCalendarModel.level(10, 10))
        assertEquals(0, ActivityCalendarModel.level(3, 0))
    }

    @Test
    fun summary_countsActiveDaysAndBusiest() {
        val model = ActivityCalendarModel.from(window(14) { offset -> if (offset % 2 == 0) offset + 1 else 0 }, DayOfWeek.MONDAY)

        assertEquals(7, model.activeDays)
        assertEquals(today.minusDays(12), model.busiest?.date)
        assertEquals(13, model.busiest?.sessions)
        assertEquals(4, model.busiest?.level)
    }

    @Test
    fun empty_hasNoWeeks() {
        val model = ActivityCalendarModel.from(emptyList(), DayOfWeek.MONDAY)

        assertEquals(0, model.weeks.size)
        assertNull(model.busiest)
    }
}
