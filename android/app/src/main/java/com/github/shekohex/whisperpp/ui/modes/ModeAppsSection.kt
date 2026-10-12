package com.github.shekohex.whisperpp.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.components.SettingsRow
import com.github.shekohex.whisperpp.ui.settings.common.LauncherApp
import com.github.shekohex.whisperpp.ui.settings.common.mergeAndFilterApps
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Apps that use this mode; the picker shows which mode owns an app now (it moves here on save). */
@Composable
internal fun ModeAppsSection(state: ModeEditViewModel.UiState, onToggle: (String) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val labels = remember(state.installedApps) { state.installedApps.associate { it.packageName to it.label } }
    SectionCard(title = stringResource(R.string.modes_edit_apps)) {
        Text(
            text = stringResource(R.string.modes_edit_apps_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        state.draft.appPackages.forEach { pkg ->
            val previousOwner = state.ownerOf(pkg)
            val appLabel = labels[pkg] ?: pkg
            SettingsRow(
                title = appLabel,
                supporting = previousOwner?.let { stringResource(R.string.modes_edit_apps_moves_from, it.name) } ?: pkg,
                trailing = {
                    IconButton(onClick = { onToggle(pkg) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.modes_edit_apps_remove_named, appLabel))
                    }
                },
            )
        }
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.modes_edit_apps_add), modifier = Modifier.padding(start = Spacing.sm))
        }
    }
    if (showPicker) {
        ModeAppPickerDialog(state = state, onToggle = onToggle, onDismiss = { showPicker = false })
    }
}

@Composable
private fun ModeAppPickerDialog(
    state: ModeEditViewModel.UiState,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val assigned = state.draft.appPackages
    val extras = remember(assigned, state.otherModes) { assigned + state.otherModes.flatMap { it.appPackages } }
    val apps = remember(state.installedApps, extras, query) { mergeAndFilterApps(state.installedApps, extras, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modes_edit_apps_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.modes_edit_apps_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = PICKER_LIST_MAX_HEIGHT)) {
                    items(apps, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app = app,
                            checked = app.packageName in assigned,
                            ownerName = state.ownerOf(app.packageName)?.name,
                            onToggle = { onToggle(app.packageName) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.modes_done)) } },
    )
}

@Composable
private fun AppPickerRow(app: LauncherApp, checked: Boolean, ownerName: String?, onToggle: () -> Unit) {
    val supporting = when {
        ownerName == null -> app.packageName
        checked -> stringResource(R.string.modes_edit_apps_moves_from, ownerName)
        else -> stringResource(R.string.modes_edit_apps_used_by, ownerName)
    }
    SettingsRow(
        title = app.label,
        supporting = supporting,
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        trailing = { Checkbox(checked = checked, onCheckedChange = null) },
    )
}

private val PICKER_LIST_MAX_HEIGHT = 360.dp
