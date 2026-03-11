package com.github.shekohex.whisperpp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsFormatter
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot

private data class AnalyticsStat(
    val label: String,
    val value: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    snapshot: AnalyticsSnapshot,
    onBack: () -> Unit,
    onConfirmReset: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifetimeStats = remember(snapshot, context) {
        listOf(
            AnalyticsStat(
                label = context.getString(R.string.analytics_metric_minutes),
                value = snapshot.totalRecordingDurationMinutes.toString(),
            ),
            AnalyticsStat(
                label = context.getString(R.string.analytics_metric_wpm),
                value = snapshot.averageWordsPerMinute.toString(),
            ),
            AnalyticsStat(
                label = context.getString(R.string.analytics_metric_keystrokes),
                value = snapshot.estimatedKeystrokesSaved.toString(),
            ),
        )
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.analytics_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.analytics_back))
                    }
                },
                actions = {
                    actions?.invoke()
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnalyticsHeroCard(snapshot = snapshot)

            SettingsGroup(title = stringResource(R.string.analytics_trend_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.analytics_trend_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnalyticsTrendPreview(
                        buckets = snapshot.dailyBuckets,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.analytics_lifetime_totals_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    lifetimeStats.forEach { stat ->
                        LifetimeStatRow(
                            label = stat.label,
                            value = stat.value,
                            valueTag = when (stat.label) {
                                context.getString(R.string.analytics_metric_minutes) -> "analytics_metric_minutes_value"
                                context.getString(R.string.analytics_metric_wpm) -> "analytics_metric_wpm_value"
                                else -> "analytics_metric_keystrokes_value"
                            },
                        )
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.analytics_session_breakdown_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BreakdownRow(
                        label = stringResource(R.string.analytics_completed_sessions),
                        value = snapshot.totalCompletedSessions.toString(),
                        valueTag = "analytics_completed_sessions_value",
                    )
                    BreakdownRow(
                        label = stringResource(R.string.analytics_cancelled_sessions),
                        value = snapshot.totalCancelledSessions.toString(),
                        valueTag = "analytics_cancelled_sessions_value",
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.analytics_word_breakdown_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BreakdownRow(
                        label = stringResource(R.string.analytics_raw_words),
                        value = snapshot.totalRawWordCount.toString(),
                        valueTag = "analytics_raw_words_value",
                    )
                    BreakdownRow(
                        label = stringResource(R.string.analytics_final_words),
                        value = snapshot.totalFinalInsertedWordCount.toString(),
                        valueTag = "analytics_final_words_value",
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.analytics_reset_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.analytics_reset_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    ) {
                        Text(stringResource(R.string.analytics_reset_action))
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars),
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.analytics_reset_confirm_title)) },
            text = { Text(stringResource(R.string.analytics_reset_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onConfirmReset()
                }) {
                    Text(stringResource(R.string.analytics_reset_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.analytics_reset_dismiss_action))
                }
            },
        )
    }
}

@Composable
private fun AnalyticsHeroCard(snapshot: AnalyticsSnapshot) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.analytics_hero_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = AnalyticsFormatter.formatTimeSaved(snapshot.estimatedTimeSavedMinutes),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = AnalyticsFormatter.formatHeroSummary(snapshot.estimatedTimeSavedMinutes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun LifetimeStatRow(
    label: String,
    value: String,
    valueTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.analytics_lifetime_totals_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                modifier = Modifier.testTag(valueTag),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    valueTag: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value,
                modifier = Modifier.testTag(valueTag),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
