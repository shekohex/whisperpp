package com.github.shekohex.whisperpp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Day-grouped transcript list; [header] (banner) scrolls with it. */
@Composable
fun HistoryList(
    entries: List<TranscriptHistoryEntry>,
    apps: Map<String, HistoryAppInfo>,
    selectedIds: Set<Long>,
    onOpen: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val nowMillis = remember(entries) { System.currentTimeMillis() }
    val groups = remember(entries, nowMillis) { groupByDay(entries, nowMillis) }
    val isSelecting = selectedIds.isNotEmpty()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (header != null) item(key = "header") { header() }
        groups.forEach { group ->
            item(key = "day-${group.day}") { HistoryDayHeader(group.day) }
            items(group.entries, key = { it.id }) { entry ->
                HistoryRow(
                    entry = entry,
                    app = entry.appPackage?.let(apps::get),
                    nowMillis = nowMillis,
                    isSelecting = isSelecting,
                    isSelected = entry.id in selectedIds,
                    onClick = { if (isSelecting) onToggleSelection(entry.id) else onOpen(entry.id) },
                    onToggleSelection = { onToggleSelection(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun HistoryDayHeader(day: HistoryDay) {
    val locale = LocalConfiguration.current.locales[0]
    val label = when (day) {
        HistoryDay.Today -> stringResource(R.string.history_day_today)
        HistoryDay.Yesterday -> stringResource(R.string.history_day_yesterday)
        is HistoryDay.On -> remember(day.date, locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day.date)
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = Spacing.md, start = 4.dp)
            .semantics { heading() },
    )
}
