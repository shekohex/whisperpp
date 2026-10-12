package com.github.shekohex.whisperpp.ui.home

import android.content.ClipData
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.RefreshOnResume
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.Spacing
import kotlinx.coroutines.launch

private val TWO_COLUMN_MAX_WIDTH = 1_200.dp

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
    RefreshOnResume(viewModel::refreshSetupStatus)

    val activity = LocalActivity.current
    val expanded = activity != null && calculateWindowSizeClass(activity).widthSizeClass == WindowWidthSizeClass.Expanded
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val copy: (TranscriptHistoryEntry) -> Unit = { entry ->
        scope.launch {
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
            onNavigate = onNavigate,
            onCopy = copy,
            onMicRequested = viewModel::setMicRequested,
            onMicResult = viewModel::refreshSetupStatus,
        )
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = TWO_COLUMN_MAX_WIDTH).fillMaxWidth(),
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
}

/** Shared card set so phone (stacked) and expanded (two columns) layouts stay identical. */
private class HomeSections(
    val snapshot: AnalyticsSnapshot,
    private val issues: List<HomeSetupIssue>,
    private val setupStatus: SetupStatus?,
    private val hasProviders: Boolean,
    private val recent: List<TranscriptHistoryEntry>,
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
        if (recent.isEmpty()) return
        RecentTranscriptsCard(
            entries = recent,
            onOpen = { entry -> onNavigate(Destination.HistoryDetail(entry.id)) },
            onCopy = onCopy,
            onSeeAll = { onNavigate(Destination.History) },
        )
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

@Composable
private fun rememberSetupIssues(
    summary: HomeViewModel.Summary,
    repairCount: Int,
    onNavigate: (Destination) -> Unit,
): List<HomeSetupIssue> {
    val resources = LocalResources.current
    return remember(summary, repairCount, resources) { buildSetupIssues(summary, repairCount, resources, onNavigate) }
}
