package com.github.shekohex.whisperpp.ui.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.ConfirmDialog
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** History tab (§4.8): search, day-grouped list, multi-select, export, history settings. */
@Composable
fun HistoryScreen(onNavigate: (Destination) -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = containerViewModel { container, _ ->
        HistoryViewModel(
            repository = container.transcriptHistoryRepository,
            settingsStore = SettingsRepositoryHistorySettings(container.settingsRepository),
            appResolver = PackageManagerAppResolver(appContext.packageManager),
            writeText = { uri, text -> writeTextToUri(appContext, uri, text) },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreenContent(
        state = state,
        messages = viewModel.messages,
        actions = HistoryActions(
            onOpen = { onNavigate(Destination.HistoryDetail(it)) },
            onQueryChange = viewModel::onQueryChange,
            onToggleSelection = viewModel::toggleSelection,
            onToggleSelectAll = viewModel::toggleSelectAll,
            onClearSelection = viewModel::clearSelection,
            onDeleteSelected = viewModel::deleteSelected,
            onDeleteAll = viewModel::deleteAll,
            onExportAll = viewModel::exportAll,
            onShareText = viewModel::selectedShareText,
            onEnabledChange = viewModel::setEnabled,
            onKeepAudioChange = viewModel::setKeepAudio,
            onRetentionChange = viewModel::setRetentionDays,
        ),
    )
}

@Composable
internal fun HistoryScreenContent(
    state: HistoryViewModel.UiState,
    messages: Flow<HistoryViewModel.Message>,
    actions: HistoryActions,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHost = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteSelected by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(messages) {
        messages.collect { snackbarHost.showSnackbar(resources.getString(it.textRes)) }
    }
    BackHandler(enabled = state.isSelecting, onBack = actions.onClearSelection)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(actions.onExportAll)
    }

    PageScaffold(
        title = if (state.isSelecting) {
            pluralStringResource(R.plurals.history_selected_count, state.selectedIds.size, state.selectedIds.size)
        } else {
            stringResource(R.string.nav_history)
        },
        largeTitle = !state.isSelecting,
        onBack = if (state.isSelecting) actions.onClearSelection else null,
        snackbarHost = { SnackbarHost(snackbarHost) },
        actions = {
            if (state.isSelecting) {
                SelectionActions(
                    allSelected = state.allSelected,
                    onToggleSelectAll = actions.onToggleSelectAll,
                    onShare = { shareText(context, actions.onShareText()) },
                    onDelete = { confirmDeleteSelected = true },
                )
            } else {
                OverflowMenu(
                    hasEntries = state.totalCount > 0,
                    onExportAll = { exportLauncher.launch(historyExportFileName(LocalDate.now())) },
                    onDeleteAll = { confirmDeleteAll = true },
                    onSettings = { showSettings = true },
                )
            }
        },
    ) {
        HistoryBody(state = state, actions = actions)
    }

    if (showSettings) {
        HistorySettingsSheet(
            settings = state.settings,
            onEnabledChange = actions.onEnabledChange,
            onKeepAudioChange = actions.onKeepAudioChange,
            onRetentionChange = actions.onRetentionChange,
            onDismiss = { showSettings = false },
        )
    }
    if (confirmDeleteSelected) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_selected_title),
            text = pluralStringResource(R.plurals.history_delete_selected_message, state.selectedIds.size, state.selectedIds.size),
            confirmLabel = stringResource(R.string.history_delete),
            destructive = true,
            onConfirm = {
                confirmDeleteSelected = false
                actions.onDeleteSelected()
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_all_title),
            text = stringResource(R.string.history_delete_all_message),
            confirmLabel = stringResource(R.string.history_delete_all),
            destructive = true,
            onConfirm = {
                confirmDeleteAll = false
                actions.onDeleteAll()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

@Composable
private fun HistoryBody(state: HistoryViewModel.UiState, actions: HistoryActions) {
    Column(modifier = Modifier.fillMaxSize()) {
        HistorySearchField(
            query = state.query,
            onQueryChange = actions.onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        val disabledBanner: (@Composable () -> Unit)? = if (state.settings.enabled) {
            null
        } else {
            { HistoryDisabledBanner(onEnable = { actions.onEnabledChange(true) }) }
        }
        when {
            state.isLoading -> LoadingState(Modifier.fillMaxSize())
            state.entries.isNotEmpty() -> HistoryList(
                entries = state.entries,
                apps = state.apps,
                selectedIds = state.selectedIds,
                onOpen = actions.onOpen,
                onToggleSelection = actions.onToggleSelection,
                modifier = Modifier.fillMaxSize(),
                header = disabledBanner,
            )
            state.isSearching -> EmptyState(
                icon = Icons.Default.SearchOff,
                title = stringResource(R.string.history_no_results_title),
                body = stringResource(R.string.history_no_results_body),
                modifier = Modifier.fillMaxSize(),
            )
            !state.settings.enabled -> EmptyState(
                icon = Icons.Default.History,
                title = stringResource(R.string.history_disabled_title),
                body = stringResource(R.string.history_disabled_body),
                modifier = Modifier.fillMaxSize(),
                action = {
                    Button(onClick = { actions.onEnabledChange(true) }) {
                        Text(stringResource(R.string.history_enable))
                    }
                },
            )
            else -> EmptyState(
                icon = Icons.Default.History,
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SelectionActions(
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    IconButton(onClick = onToggleSelectAll) {
        Icon(
            Icons.Default.SelectAll,
            contentDescription = stringResource(
                if (allSelected) R.string.history_deselect_all else R.string.history_select_all,
            ),
        )
    }
    IconButton(onClick = onShare) {
        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.history_share_selected))
    }
    IconButton(onClick = onDelete) {
        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_delete))
    }
}

@Composable
private fun OverflowMenu(
    hasEntries: Boolean,
    onExportAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onSettings: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.history_more_options))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_export_all)) },
            enabled = hasEntries,
            onClick = {
                expanded = false
                onExportAll()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_delete_all)) },
            enabled = hasEntries,
            onClick = {
                expanded = false
                onDeleteAll()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_settings_title)) },
            onClick = {
                expanded = false
                onSettings()
            },
        )
    }
}
