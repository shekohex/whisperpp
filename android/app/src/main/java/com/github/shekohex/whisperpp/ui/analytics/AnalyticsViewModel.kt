package com.github.shekohex.whisperpp.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.analytics.AnalyticsDayBucket
import com.github.shekohex.whisperpp.analytics.AnalyticsPeriod
import com.github.shekohex.whisperpp.analytics.AnalyticsPeriodSnapshot
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.data.ServiceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Local analytics; [state] is `null` until loaded so "not loaded" never looks like zero (H-10). */
class AnalyticsViewModel(
    private val repository: AnalyticsRepository,
    providers: Flow<List<ServiceProvider>> = flowOf(emptyList()),
) : ViewModel() {

    data class State(
        val period: AnalyticsPeriod,
        val totals: AnalyticsPeriodSnapshot,
        /** Always the full 16-week window (oldest first), whatever the period. */
        val calendar: List<AnalyticsDayBucket>,
        /** "providerId/modelId" → display label. */
        val modelLabels: Map<String, String>,
    )

    private val periodState = MutableStateFlow(AnalyticsPeriod.LAST_30_DAYS)

    val state: StateFlow<State?> = combine(repository.snapshot, periodState, providers) { snapshot, period, list ->
        State(
            period = period,
            totals = snapshot.periodSnapshot(period),
            calendar = snapshot.activityCalendar,
            modelLabels = modelLabels(list),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun selectPeriod(period: AnalyticsPeriod) {
        periodState.value = period
    }

    fun reset() {
        viewModelScope.launch { repository.resetAnalytics() }
    }

    private fun modelLabels(providers: List<ServiceProvider>): Map<String, String> = buildMap {
        providers.forEach { provider ->
            provider.models.forEach { model -> put("${provider.id}/${model.id}", "${provider.name} · ${model.name}") }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
