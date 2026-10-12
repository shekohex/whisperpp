package com.github.shekohex.whisperpp.ui.analytics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsPeriod
import com.github.shekohex.whisperpp.analytics.AnalyticsPeriodSnapshot
import com.github.shekohex.whisperpp.ui.components.ConfirmDialog
import com.github.shekohex.whisperpp.ui.components.ContentMaxWidth
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.components.WidthPreviews
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.Spacing

const val INSIGHTS_RESET_TAG = "insights_reset"

private val TWO_COLUMN_MIN_WIDTH = 840.dp
private val INSIGHTS_MAX_WIDTH = 1_200.dp

/** "Insights" destination (§4.9): period totals, activity calendar, peak hours, model usage/performance. */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = containerViewModel { container, _ ->
        AnalyticsViewModel(container.analyticsRepository, container.settingsRepository.providers)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PageScaffold(
        title = stringResource(R.string.insights_title),
        onBack = onBack,
        actions = { SettingsHelpAction(SettingsHelpTopic.ANALYTICS) },
        maxContentWidth = INSIGHTS_MAX_WIDTH,
    ) {
        val loaded = state
        if (loaded == null) {
            LoadingState()
        } else {
            InsightsContent(
                state = loaded,
                onSelectPeriod = viewModel::selectPeriod,
                onConfirmReset = viewModel::reset,
            )
        }
    }
}

@Composable
fun InsightsContent(
    state: AnalyticsViewModel.State,
    onSelectPeriod: (AnalyticsPeriod) -> Unit,
    onConfirmReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val totals = state.totals
    val calendar: @Composable (Modifier) -> Unit = { mod ->
        ChartCard(R.string.insights_calendar_title, R.string.insights_calendar_subtitle, mod) {
            ActivityCalendarChart(buckets = state.calendar)
        }
    }
    val hours: @Composable (Modifier) -> Unit = { mod ->
        ChartCard(R.string.insights_peak_hours_title, R.string.insights_peak_hours_subtitle, mod) {
            PeakHoursChart(hourlySessions = totals.hourlySessions)
        }
    }
    val usage: @Composable (Modifier) -> Unit = { mod ->
        ChartCard(R.string.insights_model_usage_title, R.string.insights_model_usage_subtitle, mod) {
            if (totals.modelUsage.isEmpty()) {
                EmptyChartText()
            } else {
                ModelUsageChart(usages = totals.modelUsage, labels = state.modelLabels)
            }
        }
    }
    val performance: @Composable (Modifier) -> Unit = { mod ->
        ChartCard(R.string.insights_model_performance_title, R.string.insights_model_performance_subtitle, mod) {
            if (totals.modelUsage.isEmpty()) {
                EmptyChartText()
            } else {
                ModelPerformanceChart(usages = totals.modelUsage, labels = state.modelLabels)
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= TWO_COLUMN_MIN_WIDTH
        LazyColumn(
            modifier = Modifier.widthIn(max = if (wide) INSIGHTS_MAX_WIDTH else ContentMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "period") { PeriodSelector(selected = state.period, onSelect = onSelectPeriod) }
            item(key = "tiles") { StatTiles(totals, singleRow = wide) }
            if (wide) {
                item(key = "activity") { ChartPair(calendar, hours) }
                item(key = "models") { ChartPair(usage, performance) }
            } else {
                item(key = "calendar") { calendar(Modifier) }
                item(key = "hours") { hours(Modifier) }
                item(key = "usage") { usage(Modifier) }
                item(key = "performance") { performance(Modifier) }
            }
            item(key = "reset") {
                OutlinedButton(
                    onClick = { confirmReset = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag(INSIGHTS_RESET_TAG),
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Text(stringResource(R.string.analytics_reset_action), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.analytics_reset_confirm_title),
            text = stringResource(R.string.analytics_reset_confirm_message),
            confirmLabel = stringResource(R.string.analytics_reset_confirm_action),
            dismissLabel = stringResource(R.string.analytics_reset_dismiss_action),
            destructive = true,
            onConfirm = {
                confirmReset = false
                onConfirmReset()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** Two chart cards side by side on wide screens. */
@Composable
private fun ChartPair(start: @Composable (Modifier) -> Unit, end: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        start(Modifier.weight(1f))
        end(Modifier.weight(1f))
    }
}

@Composable
private fun PeriodSelector(selected: AnalyticsPeriod, onSelect: (AnalyticsPeriod) -> Unit) {
    val periods = AnalyticsPeriod.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        periods.forEachIndexed { index, period ->
            val description = stringResource(period.descriptionRes())
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(stringResource(period.labelRes()))
            }
        }
    }
}

@StringRes
private fun AnalyticsPeriod.labelRes(): Int = when (this) {
    AnalyticsPeriod.LAST_7_DAYS -> R.string.insights_period_7d
    AnalyticsPeriod.LAST_30_DAYS -> R.string.insights_period_30d
    AnalyticsPeriod.LAST_90_DAYS -> R.string.insights_period_90d
    AnalyticsPeriod.ALL -> R.string.insights_period_all
}

@StringRes
private fun AnalyticsPeriod.descriptionRes(): Int = when (this) {
    AnalyticsPeriod.LAST_7_DAYS -> R.string.insights_period_7d_description
    AnalyticsPeriod.LAST_30_DAYS -> R.string.insights_period_30d_description
    AnalyticsPeriod.LAST_90_DAYS -> R.string.insights_period_90d_description
    AnalyticsPeriod.ALL -> R.string.insights_period_all_description
}

/** Words, sessions, recording time and time saved: a 2×2 grid, or one row of four on wide screens. */
@Composable
private fun StatTiles(totals: AnalyticsPeriodSnapshot, singleRow: Boolean) {
    val locale = LocalLocale.current.platformLocale
    val hm = stringResource(R.string.home_duration_hours_minutes)
    val m = stringResource(R.string.home_duration_minutes)
    val tiles = listOf(
        StatTileData(R.string.insights_stat_words, StatFormat.formatCompact(totals.words, locale), "insights_stat_words"),
        StatTileData(R.string.insights_stat_sessions, StatFormat.formatCompact(totals.sessions, locale), "insights_stat_sessions"),
        StatTileData(
            R.string.insights_stat_recording,
            StatFormat.formatDuration(totals.recordingSeconds, hm, m, locale),
            "insights_stat_recording",
        ),
        StatTileData(
            R.string.insights_stat_time_saved,
            StatFormat.formatDuration(totals.timeSavedSeconds, hm, m, locale),
            "insights_stat_time_saved",
        ),
    )
    val perRow = if (singleRow) tiles.size else 2
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                row.forEach { tile -> StatTile(tile.labelRes, tile.value, tile.tag, Modifier.weight(1f)) }
            }
        }
    }
}

private data class StatTileData(@StringRes val labelRes: Int, val value: String, val tag: String)

@Composable
private fun StatTile(@StringRes labelRes: Int, value: String, tag: String, modifier: Modifier = Modifier) {
    val label = stringResource(labelRes)
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

@Composable
private fun ChartCard(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SectionCard(title = stringResource(titleRes), modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun EmptyChartText() {
    Text(
        text = stringResource(R.string.insights_chart_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@ThemePreviews
@WidthPreviews
@Composable
private fun InsightsContentPreview() {
    val calendar = sampleCalendar()
    PreviewSurface {
        InsightsContent(
            state = AnalyticsViewModel.State(
                period = AnalyticsPeriod.LAST_30_DAYS,
                totals = AnalyticsPeriodSnapshot(
                    period = AnalyticsPeriod.LAST_30_DAYS,
                    words = 165_800,
                    sessions = 940,
                    recordingSeconds = 18 * 3_600L,
                    timeSavedSeconds = 41 * 3_600L + 30 * 60L,
                    hourlySessions = List(24) { hour -> if (hour in 8..22) (hour * 3) % 11 + 1 else 0 },
                    modelUsage = SAMPLE_MODEL_USAGE,
                    dailyBuckets = calendar.takeLast(30),
                ),
                calendar = calendar,
                modelLabels = emptyMap(),
            ),
            onSelectPeriod = {},
            onConfirmReset = {},
        )
    }
}
