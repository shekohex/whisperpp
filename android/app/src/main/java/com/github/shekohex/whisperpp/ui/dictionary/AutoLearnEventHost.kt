package com.github.shekohex.whisperpp.ui.dictionary

import android.content.res.Resources
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnCoordinator
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnEvent
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnMutationSummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * While the app is in the foreground: "Learned N corrections" with Undo (for the 30 s window) on
 * `Learned`, the review sheet on `ProposalsReady`, and a short error on `Failed`. "Immediately"
 * learns silently in the background; nothing is shown while the app is closed.
 */
@Composable
fun AutoLearnEventHost(snackbarHostState: SnackbarHostState) {
    val coordinator = LocalContext.current.appContainer.autoLearnCoordinator
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(coordinator, lifecycleOwner, resources) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coordinator.events.collect { event ->
                when (event) {
                    is AutoLearnEvent.Learned -> launch { showLearned(resources, snackbarHostState, coordinator, event.summary) }
                    is AutoLearnEvent.ProposalsReady -> AutoLearnReviewUiState.openSheet()
                    is AutoLearnEvent.Failed -> launch {
                        snackbarHostState.showSnackbar(resources.getString(R.string.autolearn_review_failed))
                    }
                }
            }
        }
    }
    val sheetOpen by AutoLearnReviewUiState.sheetOpen.collectAsStateWithLifecycle()
    val reviewing by AutoLearnReviewUiState.reviewing.collectAsStateWithLifecycle()
    if (sheetOpen) {
        AutoLearnReviewSheet(onDismiss = AutoLearnReviewUiState::closeSheet, reviewing = reviewing)
    }
}

private suspend fun showLearned(
    resources: Resources,
    host: SnackbarHostState,
    coordinator: AutoLearnCoordinator,
    summary: AutoLearnMutationSummary,
) {
    val count = summary.corrections.size
    val remaining = AutoLearnMutationSummary.UNDO_WINDOW_MS - (System.currentTimeMillis() - summary.appliedAt)
    if (remaining <= 0) return
    val result = withTimeoutOrNull(remaining) {
        host.showSnackbar(
            message = resources.getQuantityString(R.plurals.autolearn_learned, count, count),
            actionLabel = resources.getString(R.string.autolearn_undo),
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
    }
    if (result != SnackbarResult.ActionPerformed) return
    val undone = coordinator.undo(summary)
    host.showSnackbar(resources.getString(if (undone) R.string.autolearn_undone else R.string.autolearn_undo_expired))
}

/** Dictionary tab icon with the number of corrections waiting for review. */
@Composable
fun DictionaryTabIcon(icon: ImageVector) {
    val coordinator = LocalContext.current.appContainer.autoLearnCoordinator
    val count by coordinator.proposalCount.collectAsStateWithLifecycle(initialValue = 0)
    if (count <= 0) {
        Icon(icon, contentDescription = null)
        return
    }
    val description = pluralStringResource(R.plurals.autolearn_badge_description, count, count)
    BadgedBox(badge = { Badge { Text(count.toString()) } }) {
        Icon(icon, contentDescription = description)
    }
}
