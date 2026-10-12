package com.github.shekohex.whisperpp.ui.history

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * History tab: debounced search, multi-selection, delete / export and the history preferences.
 * Owns all I/O; the screen only renders [state] and forwards intents.
 */
class HistoryViewModel(
    private val repository: TranscriptHistoryRepository,
    private val settingsStore: HistorySettingsStore,
    private val appResolver: HistoryAppResolver,
    /** Writes [text] to a SAF document the user picked. */
    private val writeText: suspend (Uri, String) -> Unit,
    initialFilter: HistoryFilter = HistoryFilter.ALL,
) : ViewModel() {
    data class UiState(
        val query: String = "",
        val entries: List<TranscriptHistoryEntry> = emptyList(),
        val isLoading: Boolean = true,
        val totalCount: Int = 0,
        val selectedIds: Set<Long> = emptySet(),
        val settings: HistorySettings = HistorySettings(),
        val apps: Map<String, HistoryAppInfo> = emptyMap(),
        val filter: HistoryFilter = HistoryFilter.ALL,
        /** Failed takes the user can still retry, whatever the filter or search. */
        val needsAttentionCount: Int = 0,
    ) {
        val isSelecting: Boolean get() = selectedIds.isNotEmpty()
        val allSelected: Boolean get() = entries.isNotEmpty() && selectedIds.size == entries.size
        val isSearching: Boolean get() = query.isNotBlank()
    }

    /** One-shot feedback for the snackbar. */
    enum class Message(@StringRes val textRes: Int) {
        EXPORTED(R.string.history_exported),
        EXPORT_FAILED(R.string.history_export_failed),
        LOAD_FAILED(R.string.history_load_failed),
        DELETE_FAILED(R.string.history_delete_failed),
        SETTINGS_FAILED(R.string.history_settings_failed),
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val messageChannel = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = messageChannel.receiveAsFlow()

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(initialFilter)
    private val resolvedPackages = mutableSetOf<String>()

    init {
        _state.update { it.copy(filter = initialFilter) }
        observeEntries()
        observeSettings()
        observeCount()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeEntries() {
        viewModelScope.launch {
            query
                .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged()
                .flatMapLatest { repository.search(it) }
                .combine(filter) { entries, selected -> entries.filtered(selected) }
                .catch {
                    _state.update { s -> s.copy(isLoading = false) }
                    messageChannel.send(Message.LOAD_FAILED)
                }
                .collect { entries ->
                    _state.update { s ->
                        val visibleIds = entries.mapTo(HashSet()) { it.id }
                        s.copy(entries = entries, isLoading = false, selectedIds = s.selectedIds.filterTo(HashSet()) { it in visibleIds })
                    }
                    resolveApps(entries)
                }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.settings.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
    }

    private fun observeCount() {
        viewModelScope.launch {
            repository.count.collect { count -> _state.update { it.copy(totalCount = count) } }
        }
        viewModelScope.launch {
            repository.needsAttentionCount.collect { count -> _state.update { it.copy(needsAttentionCount = count) } }
        }
    }

    private fun List<TranscriptHistoryEntry>.filtered(selected: HistoryFilter) =
        if (selected == HistoryFilter.NEEDS_ATTENTION) filter { it.needsAttention } else this

    private fun resolveApps(entries: List<TranscriptHistoryEntry>) {
        val missing = entries.mapNotNull { it.appPackage }.filter { resolvedPackages.add(it) }.distinct()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val resolved = missing.mapNotNull { pkg -> appResolver.resolve(pkg)?.let { pkg to it } }.toMap()
            if (resolved.isNotEmpty()) _state.update { it.copy(apps = it.apps + resolved) }
        }
    }

    fun onFilterChange(value: HistoryFilter) {
        filter.value = value
        _state.update { it.copy(filter = value) }
    }

    fun onQueryChange(value: String) {
        query.value = value
        _state.update { it.copy(query = value) }
    }

    // Selection.

    fun toggleSelection(id: Long) = _state.update { s ->
        if (s.entries.none { it.id == id }) return@update s
        s.copy(selectedIds = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id)
    }

    fun selectAll() = _state.update { s -> s.copy(selectedIds = s.entries.mapTo(HashSet()) { it.id }) }

    fun toggleSelectAll() = if (_state.value.allSelected) clearSelection() else selectAll()

    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }

    /** Text of the selected entries for the share sheet. */
    fun selectedShareText(): String {
        val s = _state.value
        return buildShareText(s.entries.filter { it.id in s.selectedIds })
    }

    // Actions.

    fun deleteSelected() {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        launchGuarded(Message.DELETE_FAILED) {
            repository.delete(ids)
            clearSelection()
        }
    }

    fun deleteAll() = launchGuarded(Message.DELETE_FAILED) {
        repository.deleteAll()
        clearSelection()
    }

    fun exportAll(uri: Uri) = exportTo { json -> writeText(uri, json) }

    internal fun exportTo(sink: suspend (json: String) -> Unit) = launchGuarded(Message.EXPORT_FAILED) {
        sink(repository.exportJson())
        messageChannel.send(Message.EXPORTED)
    }

    // Preferences.

    fun setEnabled(enabled: Boolean) = launchGuarded(Message.SETTINGS_FAILED) { settingsStore.setEnabled(enabled) }

    fun setKeepAudio(keepAudio: Boolean) = launchGuarded(Message.SETTINGS_FAILED) { settingsStore.setKeepAudio(keepAudio) }

    fun setRetentionDays(days: Int) = launchGuarded(Message.SETTINGS_FAILED) {
        settingsStore.setRetentionDays(days)
        // Apply the new window right away instead of at the next app start.
        if (days > 0) repository.pruneExpired(days)
    }

    private fun launchGuarded(failure: Message, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messageChannel.send(failure)
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
