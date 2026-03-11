package com.github.shekohex.whisperpp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsDayBucket
import com.github.shekohex.whisperpp.analytics.AnalyticsFormatter
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

private data class HomeAnalyticsMetric(
    val title: String,
    val value: String,
)

@Composable
fun AnalyticsDashboardCard(
    snapshot: AnalyticsSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val metrics = remember(snapshot, context) {
        listOf(
            HomeAnalyticsMetric(
                title = context.getString(R.string.analytics_metric_minutes_short),
                value = snapshot.totalRecordingDurationMinutes.toString(),
            ),
            HomeAnalyticsMetric(
                title = context.getString(R.string.analytics_metric_wpm_short),
                value = snapshot.averageWordsPerMinute.toString(),
            ),
            HomeAnalyticsMetric(
                title = context.getString(R.string.analytics_metric_keystrokes_short),
                value = formatCompactCount(snapshot.estimatedKeystrokesSaved),
            ),
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("analytics_home_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.analytics_home_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = AnalyticsFormatter.formatTimeSaved(snapshot.estimatedTimeSavedMinutes),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = if (snapshot.estimatedTimeSavedMinutes > 0) {
                            stringResource(R.string.analytics_home_subtitle_active)
                        } else {
                            stringResource(R.string.analytics_home_subtitle_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    )
                }
                AnalyticsTrendPreview(
                    buckets = snapshot.dailyBuckets,
                    modifier = Modifier.width(116.dp),
                    compact = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                metrics.forEach { metric ->
                    HomeMetricTile(
                        title = metric.title,
                        value = metric.value,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AnalyticsTrendPreview(
    buckets: List<AnalyticsDayBucket>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val safeBuckets = if (buckets.isEmpty()) List(7) { AnalyticsDayBucket(date = java.time.LocalDate.now().minusDays((6 - it).toLong())) } else buckets.takeLast(7)
    val maxMinutes = safeBuckets.maxOfOrNull { it.estimatedTimeSavedMinutes } ?: 0
    val barWidth = if (compact) 10.dp else 18.dp
    val chartHeight = if (compact) 88.dp else 132.dp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            safeBuckets.forEach { bucket ->
                val fraction = when {
                    maxMinutes > 0 -> bucket.estimatedTimeSavedMinutes.toFloat() / maxMinutes.toFloat()
                    else -> 0f
                }
                val barFraction = max(fraction, if (bucket.estimatedTimeSavedMinutes > 0) 0.18f else 0f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.34f else 0.5f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (barFraction == 0f) 0.14f else barFraction)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                            .background(
                                if (bucket.estimatedTimeSavedMinutes > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (compact) 0.12f else 0.1f)
                                },
                            ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            safeBuckets.forEach { bucket ->
                Text(
                    text = bucket.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (safeBuckets.all { it.estimatedTimeSavedMinutes == 0 }) {
            Text(
                text = stringResource(R.string.analytics_trend_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeMetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatCompactCount(value: Int): String {
    return when {
        value >= 1000 -> String.format(Locale.US, "%.1fk", value / 1000f)
        value <= 0 -> "0"
        else -> value.toString()
    }
}
