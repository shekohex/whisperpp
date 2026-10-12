package com.github.shekohex.whisperpp.ui.analytics

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BAR_GAP = 2.dp
private val BAR_RADIUS = 4.dp
private const val LABEL_EVERY_HOURS = 6

/** 24-bin histogram of sessions per local hour; bars grow from the baseline, mirrored in RTL. */
@Composable
fun PeakHoursChart(
    hourlySessions: List<Int>,
    modifier: Modifier = Modifier,
) {
    val bins = remember(hourlySessions) {
        List(AnalyticsSnapshot.HOURS_PER_DAY) { hour -> hourlySessions.getOrElse(hour) { 0 }.coerceAtLeast(0) }
    }
    val max = bins.max()
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) { hourFormatter(locale) }
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val peakHour = bins.indices.maxByOrNull { bins[it] }?.takeIf { max > 0 }
    val summary = if (peakHour == null) {
        stringResource(R.string.insights_peak_hours_empty)
    } else {
        pluralStringResource(
            R.plurals.insights_peak_hours_sessions_summary,
            bins[peakHour],
            formatter.format(LocalTime.of(peakHour, 0)),
            bins[peakHour],
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clearAndSetSemantics { contentDescription = summary },
        ) {
            val gap = BAR_GAP.toPx()
            val width = (size.width - gap * (bins.size - 1)) / bins.size
            val radius = CornerRadius(BAR_RADIUS.toPx().coerceAtMost(width / 2))
            // Hairline track so empty hours still read as "zero" rather than missing.
            val trackHeight = 2.dp.toPx()
            bins.forEachIndexed { hour, count ->
                val start = hour * (width + gap)
                val x = if (rtl) size.width - start - width else start
                val height = if (max == 0) 0f else (count / max.toFloat()) * size.height
                drawRect(
                    color = trackColor,
                    topLeft = Offset(x, size.height - trackHeight),
                    size = Size(width, trackHeight),
                )
                if (height > 0f) {
                    val bar = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = x,
                                top = size.height - height,
                                right = x + width,
                                bottom = size.height,
                                topLeftCornerRadius = radius,
                                topRightCornerRadius = radius,
                            ),
                        )
                    }
                    drawPath(bar, barColor)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            (0 until AnalyticsSnapshot.HOURS_PER_DAY step LABEL_EVERY_HOURS).forEach { hour ->
                Text(
                    text = formatter.format(LocalTime.of(hour, 0)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // The chart above already announces this summary.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** "9 AM" or "09" depending on the locale's 12/24-hour preference. */
private fun hourFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "j"), locale)

@ThemePreviews
@Composable
private fun PeakHoursChartPreview() {
    PreviewSurface {
        PeakHoursChart(
            hourlySessions = listOf(0, 0, 0, 0, 0, 0, 1, 3, 8, 12, 9, 6, 4, 5, 7, 6, 4, 3, 2, 4, 6, 3, 1, 0),
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
