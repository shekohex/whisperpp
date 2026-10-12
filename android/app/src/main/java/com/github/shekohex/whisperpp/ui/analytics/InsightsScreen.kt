package com.github.shekohex.whisperpp.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel

/** "Insights" destination: the analytics dashboard backed by [AnalyticsViewModel]. */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = containerViewModel { container, _ -> AnalyticsViewModel(container.analyticsRepository) },
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val loaded = snapshot ?: return LoadingState()
    AnalyticsDashboardScreen(
        snapshot = loaded,
        onBack = onBack,
        onConfirmReset = viewModel::reset,
        actions = { SettingsHelpAction(SettingsHelpTopic.ANALYTICS) },
    )
}
