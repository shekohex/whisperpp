package com.github.shekohex.whisperpp.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.modes.LegacyModesMigration
import com.github.shekohex.whisperpp.ui.components.ConfirmDialog
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.components.SettingsRow
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.settings.common.loadLauncherApps
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Modes tab (§4.10): mode cards, "New mode", and the legacy migration prompt until it ran. */
@Composable
fun ModesScreen(onNavigate: (Destination) -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = containerViewModel { container, _ ->
        ModesViewModel(container, loadApps = { loadLauncherApps(appContext) })
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showMigration by rememberSaveable { mutableStateOf(false) }
    var migrationAutoShown by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pending = state.pendingMigration
    LaunchedEffect(pending != null) {
        if (pending != null && !migrationAutoShown) {
            migrationAutoShown = true
            showMigration = true
        }
        if (pending == null) showMigration = false
    }

    val errorText = error?.let {
        stringResource(
            when (it) {
                ModesViewModel.Error.ACTION_FAILED -> R.string.modes_error_action
                ModesViewModel.Error.MIGRATION_FAILED -> R.string.modes_error_migration
            },
        )
    }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.clearError()
        }
    }

    PageScaffold(
        title = stringResource(R.string.modes_title),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate(Destination.ModeEdit()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.modes_new_mode)) },
            )
        },
    ) {
        if (state.loading) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else {
            ModesList(
                state = state,
                onNavigate = onNavigate,
                onReviewMigration = { showMigration = true },
                onDelete = { pendingDeleteId = it },
                viewModel = viewModel,
            )
        }
    }

    if (showMigration && pending != null) {
        LegacyMigrationSheet(
            preview = pending,
            migrating = state.migrating,
            onExportBackup = {
                showMigration = false
                onNavigate(Destination.BackupRestore)
            },
            onMigrate = viewModel::migrate,
            onDismiss = { showMigration = false },
        )
    }

    val deleting = state.cards.firstOrNull { it.mode.id == pendingDeleteId }?.mode
    if (deleting != null) {
        ConfirmDialog(
            title = stringResource(R.string.modes_delete_title, deleting.name),
            text = stringResource(R.string.modes_delete_body),
            confirmLabel = stringResource(R.string.modes_action_delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(deleting.id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun ModesList(
    state: ModesViewModel.UiState,
    onNavigate: (Destination) -> Unit,
    onReviewMigration: () -> Unit,
    onDelete: (String) -> Unit,
    viewModel: ModesViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = FAB_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        state.pendingMigration?.let { preview ->
            item(key = "migration") { MigrationBanner(preview = preview, onReview = onReviewMigration) }
        }
        item(key = "intro") {
            Text(
                text = stringResource(R.string.modes_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.cards, key = { it.mode.id }) { card ->
            val copyName = stringResource(R.string.modes_copy_name, card.mode.name)
            ModeCard(
                card = card,
                onClick = { onNavigate(Destination.ModeEdit(card.mode.id)) },
                onEnabledChange = { viewModel.setEnabled(card.mode.id, it) },
                onSetDefault = { viewModel.setDefault(card.mode.id) },
                onDuplicate = { viewModel.duplicate(card.mode, copyName) },
                onDelete = { onDelete(card.mode.id) },
            )
        }
        if (state.pendingMigration != null) {
            item(key = "legacy") { LegacySettingsSection(onNavigate = onNavigate) }
        }
    }
}

@Composable
private fun MigrationBanner(preview: LegacyModesMigration.Preview, onReview: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.modes_migration_banner_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            val ruleCount = preview.appMappingCount + preview.languageProfileCount + preview.presetCount
            Text(
                text = pluralStringResource(R.plurals.modes_migration_banner_rules, ruleCount, ruleCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = onReview) { Text(stringResource(R.string.modes_migration_review)) }
        }
    }
}

/** The pre-modes screens, reachable only here and only until the migration ran. */
@Composable
private fun LegacySettingsSection(onNavigate: (Destination) -> Unit) {
    SectionCard(title = stringResource(R.string.modes_legacy_title)) {
        SettingsRow(
            title = stringResource(R.string.modes_app_mappings_title),
            supporting = stringResource(R.string.modes_app_mappings_summary),
            leadingIcon = Icons.Filled.Apps,
            onClick = { onNavigate(Destination.AppMappings) },
        )
        SettingsRow(
            title = stringResource(R.string.modes_language_defaults_title),
            supporting = stringResource(R.string.modes_language_defaults_summary),
            leadingIcon = Icons.Filled.Translate,
            onClick = { onNavigate(Destination.LanguageDefaults) },
        )
        SettingsRow(
            title = stringResource(R.string.modes_legacy_note_title),
            supporting = stringResource(R.string.modes_legacy_note),
            leadingIcon = Icons.Filled.History,
        )
    }
}

private val FAB_CLEARANCE = Spacing.xxl * 3
