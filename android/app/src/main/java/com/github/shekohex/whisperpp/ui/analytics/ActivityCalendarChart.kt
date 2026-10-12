package com.github.shekohex.whisperpp.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsDayBucket
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields

private val CELL_GAP = 2.dp
private const val DAYS_PER_WEEK = 7

/** Sequential single-hue ramp: empty cells on the container tone, then four steps toward primary. */
@Composable
internal fun calendarLevelColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val empty = scheme.surfaceContainerHighest
    return List(ActivityCalendarModel.LEVELS) { level ->
        if (level == 0) empty else lerp(empty, scheme.primary, level / (ActivityCalendarModel.LEVELS - 1f))
    }
}

/** 16-week heatmap: one column per week (newest at the end side, mirrored in RTL), one row per weekday. */
@Composable
fun ActivityCalendarChart(
    buckets: List<AnalyticsDayBucket>,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val model = remember(buckets, locale) {
        ActivityCalendarModel.from(buckets, WeekFields.of(locale).firstDayOfWeek)
    }
    val colors = calendarLevelColors()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val summary = calendarSummary(model)
    val columns = model.weeks.size.coerceAtLeast(1)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(columns / DAYS_PER_WEEK.toFloat())
                .clearAndSetSemantics { contentDescription = summary },
        ) {
            val gap = CELL_GAP.toPx()
            val cell = ((size.width - gap * (columns - 1)) / columns).coerceAtLeast(1f)
            val radius = CornerRadius(cell * 0.2f)
            model.weeks.forEachIndexed { column, week ->
                val startX = column * (cell + gap)
                val x = if (rtl) size.width - startX - cell else startX
                week.forEachIndexed { row, day ->
                    if (day != null) {
                        drawRoundRect(
                            color = colors[day.level],
                            topLeft = Offset(x, row * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
        CalendarLegend(colors)
    }
}

@Composable
private fun calendarSummary(model: ActivityCalendarModel): String {
    val busiest = model.busiest
    val base = pluralStringResource(
        R.plurals.insights_calendar_active_summary,
        model.totalDays,
        model.activeDays,
        model.totalDays,
    )
    if (busiest == null) return base
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalLocale.current.platformLocale)
        .format(busiest.date)
    val busiestText = pluralStringResource(
        R.plurals.insights_calendar_busiest_sessions,
        busiest.sessions,
        date,
        busiest.sessions,
    )
    return stringResource(R.string.insights_summary_join, base, busiestText)
}

@Composable
private fun CalendarLegend(colors: List<Color>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelStyle = MaterialTheme.typography.labelSmall
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        Text(stringResource(R.string.insights_legend_less), style = labelStyle, color = labelColor)
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
        Text(stringResource(R.string.insights_legend_more), style = labelStyle, color = labelColor)
    }
}

internal fun sampleCalendar(today: LocalDate = LocalDate.of(2026, 10, 12)): List<AnalyticsDayBucket> =
    (111 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val sessions = ((offset * 7 + offset / 5) % 9).let { if (it < 3) 0 else it - 2 }
        AnalyticsDayBucket(date = date, completedSessionCount = sessions)
    }

@ThemePreviews
@Composable
private fun ActivityCalendarChartPreview() {
    PreviewSurface {
        ActivityCalendarChart(buckets = sampleCalendar(), modifier = Modifier.padding(Spacing.lg))
    }
}
