package com.github.shekohex.whisperpp.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryGroupingTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val now = at(2026, 10, 12, 9, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun groupsConsecutiveEntriesByLocalDay_newestFirst() {
        val entries = listOf(
            historyEntry(at(2026, 10, 12, 8, 30), "today b"),
            historyEntry(at(2026, 10, 12, 0, 5), "today a"),
            historyEntry(at(2026, 10, 11, 23, 59), "yesterday"),
            historyEntry(at(2026, 10, 3, 12, 0), "older"),
        )

        val groups = groupByDay(entries, now, zone)

        assertEquals(
            listOf(HistoryDay.Today, HistoryDay.Yesterday, HistoryDay.On(LocalDate.of(2026, 10, 3))),
            groups.map { it.day },
        )
        assertEquals(listOf(listOf("today b", "today a"), listOf("yesterday"), listOf("older")), groups.map { g -> g.entries.map { it.rawText } })
    }

    @Test
    fun emptyList_hasNoGroups() {
        assertTrue(groupByDay(emptyList(), now, zone).isEmpty())
    }

    @Test
    fun dayBoundariesFollowTheGivenZone() {
        // 23:30 UTC on the 11th is already the 12th in UTC+2.
        val entry = historyEntry(at(2026, 10, 11, 23, 30), "late")

        val inCairo = groupByDay(listOf(entry), now, ZoneId.of("Africa/Cairo"))

        assertEquals(HistoryDay.Today, inCairo.single().day)
    }
}
