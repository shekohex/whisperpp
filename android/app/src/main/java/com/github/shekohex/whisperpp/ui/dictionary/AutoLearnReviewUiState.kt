package com.github.shekohex.whisperpp.ui.dictionary

import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnCoordinator
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide review sheet state: [AutoLearnEventHost] renders the single sheet; Dictionary's
 * "Review now" and the `ProposalsReady` event open it.
 */
object AutoLearnReviewUiState {
    private val sheetOpenState = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = sheetOpenState.asStateFlow()

    private val reviewingState = MutableStateFlow(false)
    val reviewing: StateFlow<Boolean> = reviewingState.asStateFlow()

    fun openSheet() {
        sheetOpenState.value = true
    }

    fun closeSheet() {
        sheetOpenState.value = false
    }

    /** Runs a manual review once at a time; the sheet shows progress meanwhile. */
    fun review(coordinator: AutoLearnCoordinator) {
        if (!reviewingState.compareAndSet(expect = false, update = true)) return
        coordinator.requestReview(AutoLearnReviewTrigger.MANUAL).invokeOnCompletion { reviewingState.value = false }
    }
}
