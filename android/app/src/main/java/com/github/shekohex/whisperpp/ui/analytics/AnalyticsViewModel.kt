package com.github.shekohex.whisperpp.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Local analytics; [snapshot] is `null` until loaded so "not loaded" never looks like zero (H-10). */
class AnalyticsViewModel(private val repository: AnalyticsRepository) : ViewModel() {
    val snapshot: StateFlow<AnalyticsSnapshot?> = repository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun reset() {
        viewModelScope.launch { repository.resetAnalytics() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
