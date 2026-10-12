package com.github.shekohex.whisperpp.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.modes.LegacyModesMigration
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** Asks before the one-way legacy → modes migration, offering a backup export first (§4.10). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegacyMigrationSheet(
    preview: LegacyModesMigration.Preview,
    migrating: Boolean,
    onExportBackup: () -> Unit,
    onMigrate: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LegacyMigrationContent(
            preview = preview,
            migrating = migrating,
            onExportBackup = onExportBackup,
            onMigrate = onMigrate,
        )
    }
}

@Composable
private fun LegacyMigrationContent(
    preview: LegacyModesMigration.Preview,
    migrating: Boolean,
    onExportBackup: () -> Unit,
    onMigrate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.modes_migration_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.modes_migration_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preview.appMappingCount > 0) {
            SummaryLine(
                Icons.Filled.Apps,
                pluralStringResource(R.plurals.modes_migration_app_mappings, preview.appMappingCount, preview.appMappingCount),
            )
        }
        if (preview.languageProfileCount > 0) {
            SummaryLine(
                Icons.Filled.Translate,
                pluralStringResource(
                    R.plurals.modes_migration_language_profiles,
                    preview.languageProfileCount,
                    preview.languageProfileCount,
                ),
            )
        }
        if (preview.presetCount > 0) {
            SummaryLine(
                Icons.Filled.Description,
                pluralStringResource(R.plurals.modes_migration_presets, preview.presetCount, preview.presetCount),
            )
        }
        Text(
            text = pluralStringResource(
                R.plurals.modes_migration_result,
                preview.modesToAdd.size,
                preview.modesToAdd.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.modes_migration_one_way),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onExportBackup, enabled = !migrating, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.modes_migration_export_first))
        }
        Button(onClick = onMigrate, enabled = !migrating, modifier = Modifier.fillMaxWidth()) {
            if (migrating) {
                val progressDescription = stringResource(R.string.modes_migration_in_progress)
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .semantics { contentDescription = progressDescription },
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.modes_migration_migrate_now))
            }
        }
    }
}

@Composable
private fun SummaryLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@ThemePreviews
@Composable
private fun LegacyMigrationContentPreview() {
    PreviewSurface {
        LegacyMigrationContent(
            preview = LegacyModesMigration.Preview(
                alreadyMigrated = false,
                appMappingCount = 3,
                languageProfileCount = 1,
                presetCount = 2,
                modesToAdd = emptyList(),
                defaultModeUpdate = null,
                promptsToAdd = emptyList(),
                resultingModes = emptyList(),
            ),
            migrating = false,
            onExportBackup = {},
            onMigrate = {},
        )
    }
}
