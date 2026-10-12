package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which day an entry belongs to, relative to "now". */
sealed interface HistoryDay {
    data object Today : HistoryDay

    data object Yesterday : HistoryDay

    data class On(val date: LocalDate) : HistoryDay
}

data class HistoryDayGroup(
    val day: HistoryDay,
    val entries: List<TranscriptHistoryEntry>,
)

/** Groups [entries] (kept in their given order, newest first) into consecutive local-day groups. */
fun groupByDay(
    entries: List<TranscriptHistoryEntry>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<HistoryDayGroup> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val groups = mutableListOf<HistoryDayGroup>()
    var currentDate: LocalDate? = null
    var current = mutableListOf<TranscriptHistoryEntry>()
    fun flush() {
        val date = currentDate ?: return
        val day = when (date) {
            today -> HistoryDay.Today
            today.minusDays(1) -> HistoryDay.Yesterday
            else -> HistoryDay.On(date)
        }
        groups += HistoryDayGroup(day, current)
    }
    for (entry in entries) {
        val date = Instant.ofEpochMilli(entry.createdAt).atZone(zone).toLocalDate()
        if (date != currentDate) {
            flush()
            currentDate = date
            current = mutableListOf()
        }
        current += entry
    }
    flush()
    return groups
}
