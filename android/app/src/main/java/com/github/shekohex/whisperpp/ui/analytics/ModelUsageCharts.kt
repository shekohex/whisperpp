package com.github.shekohex.whisperpp.ui.analytics

import androidx.annotation.StringRes
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.ModelUsage
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

private val BAR_HEIGHT = 8.dp

/** Share of uses per provider/model, as horizontal bars. */
@Composable
fun ModelUsageChart(
    usages: List<ModelUsage>,
    labels: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val shares = remember(usages) { ModelShare.from(usages) }
    val otherLabel = stringResource(R.string.insights_model_other)
    val rows = shares.map { share ->
        val label = share.key?.let { labels[it] ?: it } ?: otherLabel
        BarRow(label, stringResource(R.string.insights_percent, share.percent), share.percent / 100f)
    }
    val summary = chartSummary(R.string.insights_model_usage_title, rows)
    BarList(rows = rows, color = MaterialTheme.colorScheme.primary, summary = summary, modifier = modifier)
}

/** Average transcription and enhancement latency per model; bars share one seconds scale. */
@Composable
fun ModelPerformanceChart(
    usages: List<ModelUsage>,
    labels: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val transcription = stringResource(R.string.insights_latency_transcription)
    val enhancement = stringResource(R.string.insights_latency_enhancement)
    val resources = LocalResources.current
    val entries = usages.flatMap { usage ->
        val label = labels[usage.key] ?: usage.key
        listOfNotNull(
            usage.averageTranscriptionLatencyMs?.let {
                resources.getString(R.string.insights_latency_row_label, label, transcription) to it
            },
            usage.averageEnhancementLatencyMs?.let {
                resources.getString(R.string.insights_latency_row_label, label, enhancement) to it
            },
        )
    }.take(ModelShare.MAX_ROWS * 2)
    val max = entries.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val rows = entries.map { (label, ms) ->
        BarRow(label, resources.getString(R.string.insights_seconds, StatFormat.formatSeconds(ms, locale)), ms / max.toFloat())
    }
    val summary = chartSummary(R.string.insights_model_performance_title, rows)
    BarList(rows = rows, color = MaterialTheme.colorScheme.tertiary, summary = summary, modifier = modifier)
}

private data class BarRow(val label: String, val value: String, val fraction: Float)

/** Screen-reader summary: the chart title, then "label: value" per row. */
@Composable
private fun chartSummary(@StringRes titleRes: Int, rows: List<BarRow>): String {
    val resources = LocalResources.current
    val separator = stringResource(R.string.insights_summary_separator)
    val entries = rows.joinToString(separator = separator) { row ->
        resources.getString(R.string.insights_chart_entry, row.label, row.value)
    }
    return stringResource(R.string.insights_chart_summary, stringResource(titleRes), entries)
}

@Composable
private fun BarList(rows: List<BarRow>, color: Color, summary: String, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(color = track, cornerRadius = radius)
                    val width = size.width * row.fraction.coerceIn(0f, 1f)
                    if (width > 0f) {
                        val x = if (rtl) size.width - width else 0f
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, 0f),
                            size = Size(width.coerceAtLeast(size.height), size.height),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
    }
}

internal val SAMPLE_MODEL_USAGE = listOf(
    ModelUsage("openai", "whisper-1", sessions = 120, words = 9_000, transcriptionLatencyMs = 120 * 1_400L),
    ModelUsage("groq", "whisper-large-v3", sessions = 60, words = 4_000, transcriptionLatencyMs = 60 * 600L),
    ModelUsage("openai", "gpt-5-mini", enhancementSessions = 80, enhancementLatencyMs = 80 * 2_100L),
)

@ThemePreviews
@Composable
private fun ModelChartsPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            ModelUsageChart(usages = SAMPLE_MODEL_USAGE, labels = emptyMap())
            ModelPerformanceChart(usages = SAMPLE_MODEL_USAGE, labels = emptyMap())
        }
    }
}
