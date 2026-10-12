package com.github.shekohex.whisperpp.ui.home

import android.content.ClipData
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.domain.analytics.MomentumCopy
import com.github.shekohex.whisperpp.domain.setup.SetupStatus
import com.github.shekohex.whisperpp.ui.components.ContentMaxWidth
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.RefreshOnResume
import com.github.shekohex.whisperpp.ui.components.WidthPreviews
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.Spacing
import kotlinx.coroutines.launch

private val TWO_COLUMN_MAX_WIDTH = 1_200.dp
private val EXPANDED_MIN_WIDTH = 840.dp

/** Home = dashboard (§4.9): greeting, hero, setup checklist, recent transcripts. */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    viewModel: HomeViewModel = containerViewModel { container, _ -> HomeViewModel(container) },
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val setupStatus by viewModel.setupStatus.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val needsAttention by viewModel.needsAttention.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refreshSetupStatus)

    val activity = LocalActivity.current
    val expanded = activity != null && calculateWindowSizeClass(activity).widthSizeClass == WindowWidthSizeClass.Expanded
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val copy: (TranscriptHistoryEntry) -> Unit = { entry ->
        if (entry.hasText) scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(resources.getString(R.string.app_name), entry.finalText)))
            snackbarHostState.showSnackbar(resources.getString(R.string.home_recent_copied))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        val current = summary
        if (current == null) {
            LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val issues = rememberSetupIssues(current, backupStatus.repairCount, onNavigate)
        val sections = HomeSections(
            snapshot = analytics ?: AnalyticsSnapshot(),
            issues = issues,
            setupStatus = setupStatus,
            hasProviders = current.providers.isNotEmpty(),
            recent = recent,
            needsAttention = needsAttention,
            onNavigate = onNavigate,
            onCopy = copy,
            onMicRequested = viewModel::setMicRequested,
            onMicResult = viewModel::refreshSetupStatus,
        )
        HomeContent(sections = sections, expanded = expanded, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

/** Stateless dashboard body: stacked on phones, two columns when [expanded]. */
@Composable
private fun HomeContent(sections: HomeSections, expanded: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = if (expanded) TWO_COLUMN_MAX_WIDTH else ContentMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "greeting") { Greeting(sections.snapshot) }
            if (expanded) {
                item(key = "columns") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            sections.Hero()
                            sections.Setup()
                        }
                        Column(Modifier.weight(1f)) { sections.Recent() }
                    }
                }
            } else {
                item(key = "hero") { sections.Hero() }
                item(key = "setup") { sections.Setup() }
                item(key = "recent") { sections.Recent() }
            }
        }
    }
}

/** Shared card set so phone (stacked) and expanded (two columns) layouts stay identical. */
private class HomeSections(
    val snapshot: AnalyticsSnapshot,
    private val issues: List<HomeSetupIssue>,
    private val setupStatus: SetupStatus?,
    private val hasProviders: Boolean,
    private val recent: List<TranscriptHistoryEntry>,
    private val needsAttention: Int,
    private val onNavigate: (Destination) -> Unit,
    private val onCopy: (TranscriptHistoryEntry) -> Unit,
    private val onMicRequested: () -> Unit,
    private val onMicResult: () -> Unit,
) {
    @Composable
    fun Hero() {
        DashboardHeroCard(
            lifetimeWords = snapshot.lifetimeWords,
            timeSavedSeconds = snapshot.lifetimeTimeSavedSeconds,
            onViewInsights = { onNavigate(Destination.Insights) },
        )
    }

    @Composable
    fun Setup() {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (issues.isNotEmpty()) HomeSetupBanner(issues = issues)
            if (setupStatus != null && !setupStatus.isComplete) {
                SetupChecklistCard(
                    status = setupStatus,
                    hasProviders = hasProviders,
                    onNavigate = onNavigate,
                    onMicRequested = onMicRequested,
                    onMicResult = onMicResult,
                )
            }
        }
    }

    @Composable
    fun Recent() {
        if (recent.isEmpty() && needsAttention == 0) return
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            RecordingsAttentionCard(
                count = needsAttention,
                onOpen = { onNavigate(Destination.History(failedOnly = true)) },
            )
            if (recent.isNotEmpty()) {
                RecentTranscriptsCard(
                    entries = recent,
                    onOpen = { entry -> onNavigate(Destination.HistoryDetail(entry.id)) },
                    onCopy = onCopy,
                    onSeeAll = { onNavigate(Destination.History()) },
                )
            }
        }
    }
}

@Composable
private fun Greeting(snapshot: AnalyticsSnapshot) {
    val variant = MomentumCopy.pick(
        lifetimeSessions = snapshot.lifetimeSessions,
        last7DaysSessions = snapshot.dailyBuckets.map { it.completedSessionCount },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(variant.textRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsHelpAction(SettingsHelpTopic.HOME)
    }
}

private fun MomentumCopy.Variant.textRes(): Int = when (this) {
    MomentumCopy.Variant.GET_STARTED -> R.string.home_momentum_get_started
    MomentumCopy.Variant.WELCOME_BACK -> R.string.home_momentum_welcome_back
    MomentumCopy.Variant.KEEP_GOING -> R.string.home_momentum_keep_going
    MomentumCopy.Variant.ON_A_ROLL -> R.string.home_momentum_on_a_roll
}

@WidthPreviews
@Composable
private fun HomeContentPreview() {
    PreviewSurface {
        BoxWithConstraints {
            HomeContent(
                sections = HomeSections(
                    snapshot = AnalyticsSnapshot(lifetimeWords = 8_880, lifetimeTimeSavedSeconds = 2 * 3_600L + 5 * 60L),
                    issues = emptyList(),
                    setupStatus = null,
                    hasProviders = true,
                    recent = sampleTranscripts(),
                    needsAttention = 2,
                    onNavigate = {},
                    onCopy = {},
                    onMicRequested = {},
                    onMicResult = {},
                ),
                expanded = maxWidth >= EXPANDED_MIN_WIDTH,
            )
        }
    }
}

@Composable
private fun rememberSetupIssues(
    summary: HomeViewModel.Summary,
    repairCount: Int,
    onNavigate: (Destination) -> Unit,
): List<HomeSetupIssue> {
    val resources = LocalResources.current
    return remember(summary, repairCount, resources) { buildSetupIssues(summary, repairCount, resources, onNavigate) }
}
