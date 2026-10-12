package com.github.shekohex.whisperpp.ui.history

import android.net.Uri

/** Everything the history screen can ask of its ViewModel. */
data class HistoryActions(
    val onOpen: (Long) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (HistoryFilter) -> Unit,
    val onToggleSelection: (Long) -> Unit,
    val onToggleSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onDeleteSelected: () -> Unit,
    val onDeleteAll: () -> Unit,
    val onExportAll: (Uri) -> Unit,
    val onShareText: () -> String,
    val onEnabledChange: (Boolean) -> Unit,
    val onKeepAudioChange: (Boolean) -> Unit,
    val onRetentionChange: (Int) -> Unit,
)
