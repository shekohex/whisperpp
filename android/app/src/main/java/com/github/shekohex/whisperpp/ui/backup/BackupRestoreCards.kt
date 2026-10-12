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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ImportAnalysis
import com.github.shekohex.whisperpp.data.RestoreMode
import com.github.shekohex.whisperpp.data.RestoreRepairArea
import com.github.shekohex.whisperpp.data.RestoreSummary
import com.github.shekohex.whisperpp.ui.backup.BackupNotice.Tone
import com.github.shekohex.whisperpp.ui.navigation.Destination
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val backupFileNameFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.of("UTC"))


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
                        text = stringResource(R.string.backup_sensitive_content),
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
    isApplyingImport: Boolean,
) {
    val canConfirm = analysis.restoreMode == RestoreMode.OVERWRITE || selectedMergeCategories.isNotEmpty()

    Column(
        modifier = Modifier.testTag("backup_restore_preview_card"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackupNoticeCard(
            title = stringResource(R.string.backup_preview_title),
            message = stringResource(
                R.string.backup_preview_message,
                formatBackupDisplayTime(analysis.exportedAtUtc),
                analysis.backupAppVersionName,
                stringResource(restoreModeLabel(analysis.restoreMode)),
            ),
            tone = Tone.Info,
            leadingIcon = Icons.Default.Info,
        )

        if (analysis.restoreMode == RestoreMode.MERGE) {
            BackupNoticeCard(
                title = stringResource(R.string.backup_conflict_title),
                message = stringResource(R.string.backup_conflict_message),
                tone = Tone.Info,
                leadingIcon = Icons.Default.Warning,
            )
        }

        if (analysis.warnings.isNotEmpty()) {
            SummarySectionCard(
                title = stringResource(R.string.backup_warnings_title),
                items = analysis.warnings.map { restoreWarningText(it, analysis) },
            )
        }

        if (analysis.skippedItems.isNotEmpty()) {
            SummarySectionCard(
                title = stringResource(R.string.backup_skipped_invalid_title),
                items = analysis.skippedItems.map { skippedItemText(it) },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_categories_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                analysis.categoryPreviews.forEachIndexed { index, preview ->
                    val label = backupCategoryLabel(preview.categoryId, preview.label)
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
                                    pluralStringResource(
                                        R.plurals.backup_merge_chip_label,
                                        preview.resultingItemCount,
                                        label,
                                        preview.resultingItemCount,
                                    ),
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
                            headlineContent = { Text(label, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = when {
                                            !preview.isAvailable -> stringResource(R.string.backup_preview_not_present)
                                            preview.conflictKeys.isNotEmpty() -> pluralStringResource(
                                                R.plurals.backup_preview_counts_conflicts,
                                                preview.conflictKeys.size,
                                                preview.importedItemCount,
                                                preview.existingItemCount,
                                                preview.conflictKeys.size,
                                            )
                                            else -> stringResource(
                                                R.string.backup_preview_counts,
                                                preview.importedItemCount,
                                                preview.existingItemCount,
                                                preview.resultingItemCount,
                                            )
                                        },
                                    )
                                    if (preview.containsSensitiveContent) {
                                        Text(
                                            text = stringResource(R.string.backup_sensitive_included),
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
                Text(stringResource(R.string.backup_applying_restore))
            } else {
                Text(
                    if (analysis.restoreMode == RestoreMode.OVERWRITE) {
                        stringResource(R.string.backup_confirm_overwrite)
                    } else {
                        pluralStringResource(
                            R.plurals.backup_confirm_merge,
                            selectedMergeCategories.size,
                            selectedMergeCategories.size,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun RestoreSummaryCard(
    summary: RestoreSummary,
    onNavigate: (Destination) -> Unit,
) {
    val missingCredentials = missingCredentialProviderIds(summary.repairChecklist)
    val providerNames = summary.repairChecklist
        .mapNotNull { entry -> entry.providerId?.let { id -> entry.providerName?.let { name -> id to name } } }
        .toMap()
    val none = listOf(stringResource(R.string.backup_summary_none))
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
                text = stringResource(R.string.backup_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (summary.repairChecklist.isNotEmpty()) {
                    stringResource(R.string.backup_summary_needs_repair)
                } else {
                    stringResource(R.string.backup_summary_done)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SummarySectionCard(
                title = stringResource(R.string.backup_summary_applied),
                items = summary.appliedCategories.map { backupCategoryLabel(it) }.ifEmpty { none },
            )
            SummarySectionCard(
                title = stringResource(R.string.backup_summary_skipped),
                items = summary.skippedItems.map { skippedItemText(it) }.ifEmpty { none },
            )
            SummarySectionCard(
                title = stringResource(R.string.backup_summary_cleared),
                items = summary.clearedSelections.map { clearedSelectionText(it, missingCredentials, providerNames) }
                    .ifEmpty { none },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.backup_summary_repair),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                if (summary.repairChecklist.isEmpty()) {
                    Text(
                        text = none.first(),
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
                                    text = repairEntryText(repairEntry, missingCredentials),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val (destination, label) = action
                                TextButton(onClick = { onNavigate(destination) }) {
                                    Text(stringResource(label))
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
                modifier = Modifier.semantics { heading() },
            )
            items.forEach { item ->
                Text(
                    text = stringResource(R.string.backup_bullet_item, item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun repairActionFor(area: RestoreRepairArea): Pair<Destination, Int> {
    return when (area) {
        RestoreRepairArea.PROVIDER_CREDENTIALS -> Destination.ProviderList to R.string.backup_repair_open_providers
        RestoreRepairArea.ACTIVE_STT,
        RestoreRepairArea.ACTIVE_TEXT,
        RestoreRepairArea.COMMAND_TEXT
        -> Destination.ProviderSelections to R.string.backup_repair_open_selections
    }
}

internal fun buildBackupFileName(exportedAtUtc: String): String {
    val instant = runCatching { Instant.parse(exportedAtUtc) }.getOrElse { Instant.now() }
    return "whisperpp_backup_${backupFileNameFormatter.format(instant)}.whisperpp-backup"
}

internal fun formatBackupDisplayTime(exportedAtUtc: String): String {
    val instant = runCatching { Instant.parse(exportedAtUtc) }.getOrElse { return exportedAtUtc }
    // Built per call so it follows the current (per-app) locale and time zone.
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
