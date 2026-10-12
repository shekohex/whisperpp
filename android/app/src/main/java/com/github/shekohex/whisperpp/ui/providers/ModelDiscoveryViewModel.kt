package com.github.shekohex.whisperpp.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.domain.providers.DiscoveredCache
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.domain.providers.DiscoveryUnsupported
import com.github.shekohex.whisperpp.domain.providers.ModelDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives [ModelDiscoverySheet]: shows the cached listing first, refreshes on demand and turns the
 * user's choice into models to add. Persistence and error text are injected for tests.
 */
class ModelDiscoveryViewModel(
    private val loadCache: suspend () -> DiscoveredCache?,
    private val saveCache: suspend (DiscoveredCache) -> Unit,
    private val errorMessage: (Throwable) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    enum class Filter { ALL, STT, TEXT, REALTIME }

    enum class AddAction { SELECTED, ALL_STT, ALL_TEXT }

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(
            val models: List<DiscoveredModel>,
            val selectedIds: Set<String> = emptySet(),
            val filter: Filter = Filter.ALL,
            val fetchedAtMillis: Long? = null,
        ) : UiState {
            val visibleModels: List<DiscoveredModel>
                get() = models.filter { it.matches(filter) }
        }
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var discovery: ModelDiscovery? = null
    private var job: Job? = null

    /**
     * Shows the cached listing for [discovery]'s provider, or fetches when there is none or it was
     * listed for another endpoint/type.
     */
    fun open(discovery: ModelDiscovery?) {
        this.discovery = discovery
        job?.cancel()
        job = viewModelScope.launch {
            val expected = discovery?.identity
            val cached = loadCache()?.takeIf { expected == null || it.identity == expected }
            if (cached != null && cached.models.isNotEmpty()) {
                _uiState.value = UiState.Loaded(cached.models, fetchedAtMillis = cached.fetchedAtMillis)
            } else {
                fetch()
            }
        }
    }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch { fetch() }
    }

    fun toggle(modelId: String) = updateLoaded { state ->
        val selected = if (modelId in state.selectedIds) state.selectedIds - modelId else state.selectedIds + modelId
        state.copy(selectedIds = selected)
    }

    fun setFilter(filter: Filter) = updateLoaded { it.copy(filter = filter) }

    /** Models chosen by [action]; clears the selection. */
    fun take(action: AddAction): List<DiscoveredModel> {
        val state = _uiState.value as? UiState.Loaded ?: return emptyList()
        val chosen = when (action) {
            AddAction.SELECTED -> state.models.filter { it.id in state.selectedIds }
            AddAction.ALL_STT -> state.models.filter { it.capabilities.stt }
            AddAction.ALL_TEXT -> state.models.filter { it.capabilities.text }
        }
        _uiState.value = state.copy(selectedIds = emptySet())
        return chosen
    }

    private suspend fun fetch() {
        val source = discovery
        if (source == null) {
            _uiState.value = UiState.Error(errorMessage(DiscoveryUnsupported.NoListingEndpoint))
            return
        }
        val previous = _uiState.value as? UiState.Loaded
        _uiState.value = UiState.Loading
        source.listModels().fold(
            onSuccess = { models ->
                val cache = DiscoveredCache(clock(), models, source.identity)
                saveCache(cache)
                val ids = models.mapTo(HashSet()) { it.id }
                _uiState.value = UiState.Loaded(
                    models = models,
                    selectedIds = previous?.selectedIds.orEmpty().filterTo(HashSet()) { it in ids },
                    filter = previous?.filter ?: Filter.ALL,
                    fetchedAtMillis = cache.fetchedAtMillis,
                )
            },
            onFailure = { error -> _uiState.value = UiState.Error(errorMessage(error)) },
        )
    }

    private fun updateLoaded(transform: (UiState.Loaded) -> UiState.Loaded) {
        _uiState.update { state -> if (state is UiState.Loaded) transform(state) else state }
    }

    companion object {
        /**
         * Appends [discovered] models that [existing] lacks, with their discovered capabilities.
         * Existing (possibly hand-edited) models are kept as they are.
         */
        fun merge(existing: List<ModelConfig>, discovered: List<DiscoveredModel>): List<ModelConfig> {
            val ids = existing.mapTo(HashSet()) { it.id.trim() }
            val added = discovered
                .filter { ids.add(it.id.trim()) }
                .map { model ->
                    val capabilities = if (model.isClassified) model.capabilities else ModelCapabilities.TEXT
                    ModelConfig(id = model.id, name = model.displayName.ifBlank { model.id }, capabilities = capabilities)
                }
            return existing + added
        }
    }
}

private fun DiscoveredModel.matches(filter: ModelDiscoveryViewModel.Filter): Boolean = when (filter) {
    ModelDiscoveryViewModel.Filter.ALL -> true
    ModelDiscoveryViewModel.Filter.STT -> capabilities.stt
    ModelDiscoveryViewModel.Filter.TEXT -> capabilities.text
    ModelDiscoveryViewModel.Filter.REALTIME -> capabilities.realtime
}
