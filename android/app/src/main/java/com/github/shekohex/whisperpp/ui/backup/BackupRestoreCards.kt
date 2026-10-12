package com.github.shekohex.whisperpp.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ImportAnalysis
import com.github.shekohex.whisperpp.data.RestoreMode
import com.github.shekohex.whisperpp.data.RestoreRepairArea
import com.github.shekohex.whisperpp.data.RestoreSelectionType
import com.github.shekohex.whisperpp.data.RestoreSummary
import com.github.shekohex.whisperpp.data.SettingsBackupCategoryManifestEntry
import com.github.shekohex.whisperpp.ui.backup.BackupNotice.Tone
import com.github.shekohex.whisperpp.ui.navigation.Destination
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val backupFileNameFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.of("UTC"))

private val backupDisplayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

@Composable
internal fun BackupIdentityRow(
    label: String,
    value: String,
    icon: ImageVector,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(icon, contentDescription = null) },
    )
}

@Composable
internal fun BackupCategoryRow(
    title: String,
    summary: String,
    sensitive: Boolean,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(summary)
                if (sensitive) {
                    Text(
                        text = "Sensitive content",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (sensitive) Icons.Default.Security else Icons.Default.Backup,
                contentDescription = null,
            )
        },
    )
}

/** A checkbox row for content that is only exported / restored when the user opts in. */
@Composable
internal fun BackupOptInRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
internal fun BackupNoticeCard(
    title: String,
    message: String,
    tone: Tone,
    leadingIcon: ImageVector,
) {
    val containerColor = when (tone) {
        Tone.Info -> MaterialTheme.colorScheme.secondaryContainer
        Tone.Success -> MaterialTheme.colorScheme.primaryContainer
        Tone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        Tone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        Tone.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        Tone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        Tone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        Tone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
internal fun ImportPreviewCard(
    analysis: ImportAnalysis,
    selectedMergeCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onConfirmRestore: () -> Unit,
    categoryLabelById: Map<String, SettingsBackupCategoryManifestEntry>,
    isApplyingImport: Boolean,
) {
    val canConfirm = analysis.restoreMode == RestoreMode.OVERWRITE || selectedMergeCategories.isNotEmpty()

    Column(
        modifier = Modifier.testTag("backup_restore_preview_card"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackupNoticeCard(
            title = "Preview before restore",
            message = "Backup from ${formatBackupDisplayTime(analysis.exportedAtUtc)} · app ${analysis.backupAppVersionName} · ${restoreModeLabel(analysis.restoreMode)}",
            tone = Tone.Info,
            leadingIcon = Icons.Default.Info,
        )

        if (analysis.restoreMode == RestoreMode.MERGE) {
            BackupNoticeCard(
                title = "Conflict behavior",
                message = "Merge restores only the categories you keep selected here. When the same item exists locally, the imported value wins.",
                tone = Tone.Info,
                leadingIcon = Icons.Default.Warning,
            )
        }

        if (analysis.warnings.isNotEmpty()) {
            SummarySectionCard(
                title = "Warnings",
                items = analysis.warnings.map { it.message },
            )
        }

        if (analysis.skippedItems.isNotEmpty()) {
            SummarySectionCard(
                title = "Skipped invalid or unsupported items",
                items = analysis.skippedItems.map { skipped ->
                    val label = categoryLabelById[skipped.categoryId]?.label ?: skipped.categoryId
                    val itemKey = skipped.itemKey?.let { " ($it)" }.orEmpty()
                    "$label$itemKey: ${skipped.reason}"
                },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Categories in this backup",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                analysis.categoryPreviews.forEachIndexed { index, preview ->
                    if (preview.isAvailable && !preview.includedByDefault) {
                        // Opt-in category (transcript history): unchecked until the user asks for it.
                        BackupOptInRow(
                            title = stringResource(R.string.backup_history_restore_checkbox),
                            summary = stringResource(
                                R.string.backup_history_restore_summary,
                                preview.importedItemCount,
                                preview.existingItemCount,
                            ),
                            checked = selectedMergeCategories.contains(preview.categoryId),
                            onCheckedChange = { onToggleCategory(preview.categoryId) },
                        )
                    } else if (analysis.restoreMode == RestoreMode.MERGE && preview.selectable) {
                        FilterChip(
                            selected = selectedMergeCategories.contains(preview.categoryId),
                            onClick = { onToggleCategory(preview.categoryId) },
                            label = {
                                Text(
                                    "${preview.label} · ${preview.resultingItemCount} resulting ${backupPluralize(preview.resultingItemCount, "item", "items")}",
                                )
                            },
                            leadingIcon = if (preview.containsSensitiveContent) {
                                { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text(preview.label, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = when {
                                            !preview.isAvailable -> "Not present in this backup."
                                            preview.conflictKeys.isNotEmpty() -> "${preview.importedItemCount} imported, ${preview.existingItemCount} existing, ${preview.conflictKeys.size} conflict ${backupPluralize(preview.conflictKeys.size, "item", "items")}."
                                            else -> "${preview.importedItemCount} imported, ${preview.existingItemCount} existing, ${preview.resultingItemCount} after restore."
                                        },
                                    )
                                    if (preview.containsSensitiveContent) {
                                        Text(
                                            text = "Sensitive content included.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (preview.containsSensitiveContent) Icons.Default.Security else Icons.Default.Backup,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    if (index != analysis.categoryPreviews.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        Button(
            onClick = onConfirmRestore,
            modifier = Modifier.fillMaxWidth(),
            enabled = canConfirm && !isApplyingImport,
        ) {
            if (isApplyingImport) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(12.dp))
                Text("Applying restore…")
            } else {
                Text(
                    if (analysis.restoreMode == RestoreMode.OVERWRITE) {
                        "Confirm overwrite restore"
                    } else {
                        "Confirm restore for ${selectedMergeCategories.size} ${backupPluralize(selectedMergeCategories.size, "category", "categories")}"
                    },
                )
            }
        }
    }
}

@Composable
internal fun RestoreSummaryCard(
    summary: RestoreSummary,
    categoryLabelById: Map<String, SettingsBackupCategoryManifestEntry>,
    onNavigate: (Destination) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_restore_summary_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Completion summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (summary.repairChecklist.isNotEmpty()) {
                    "Restore finished, but some selections or credentials still need repair."
                } else {
                    "Restore finished and Whisper++ kept you in settings to review the outcome."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SummarySectionCard(
                title = "Applied categories",
                items = summary.appliedCategories.map { categoryId ->
                    categoryLabelById[categoryId]?.label ?: categoryId
                }.ifEmpty { listOf("None") },
            )
            SummarySectionCard(
                title = "Skipped items",
                items = summary.skippedItems.map { skipped ->
                    val label = categoryLabelById[skipped.categoryId]?.label ?: skipped.categoryId
                    val itemKey = skipped.itemKey?.let { " ($it)" }.orEmpty()
                    "$label$itemKey: ${skipped.reason}"
                }.ifEmpty { listOf("None") },
            )
            SummarySectionCard(
                title = "Cleared selections",
                items = summary.clearedSelections.map { cleared ->
                    "${restoreSelectionLabel(cleared.selectionType)}: ${cleared.reason}"
                }.ifEmpty { listOf("None") },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Repair checklist",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (summary.repairChecklist.isEmpty()) {
                    Text(
                        text = "None",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    summary.repairChecklist.forEach { repairEntry ->
                        val action = repairActionFor(repairEntry.area)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = repairEntry.message,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val (destination, label) = action
                                TextButton(onClick = { onNavigate(destination) }) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySectionCard(
    title: String,
    items: List<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            items.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun repairActionFor(area: RestoreRepairArea): Pair<Destination, String> {
    return when (area) {
        RestoreRepairArea.PROVIDER_CREDENTIALS -> Destination.ProviderList to "Open providers"
        RestoreRepairArea.ACTIVE_STT,
        RestoreRepairArea.ACTIVE_TEXT,
        RestoreRepairArea.COMMAND_TEXT
        -> Destination.ProviderSelections to "Open provider selections"
    }
}

private fun restoreSelectionLabel(type: RestoreSelectionType): String {
    return when (type) {
        RestoreSelectionType.ACTIVE_STT -> "Dictation"
        RestoreSelectionType.ACTIVE_TEXT -> "Enhancement"
        RestoreSelectionType.COMMAND_TEXT -> "Command mode"
    }
}

private fun restoreModeLabel(mode: RestoreMode): String {
    return when (mode) {
        RestoreMode.OVERWRITE -> "Overwrite"
        RestoreMode.MERGE -> "Merge"
    }
}

internal fun buildBackupFileName(exportedAtUtc: String): String {
    val instant = runCatching { Instant.parse(exportedAtUtc) }.getOrElse { Instant.now() }
    return "whisperpp_backup_${backupFileNameFormatter.format(instant)}.whisperpp-backup"
}

internal fun formatBackupDisplayTime(exportedAtUtc: String): String {
    val instant = runCatching { Instant.parse(exportedAtUtc) }.getOrElse { return exportedAtUtc }
    return backupDisplayFormatter.format(instant)
}

internal fun backupPluralize(count: Int, singular: String, plural: String): String {
    return if (count == 1) singular else plural
}
