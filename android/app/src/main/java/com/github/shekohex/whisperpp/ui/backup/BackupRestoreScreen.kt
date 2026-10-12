package com.github.shekohex.whisperpp.ui.backup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_MANIFEST
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS
import com.github.shekohex.whisperpp.ui.backup.BackupNotice.Tone
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.appVersionName
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel = containerViewModel { container, _ ->
        BackupRestoreViewModel(container, appContext, appVersionName(appContext))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast: (Int) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        viewModel.onExportDestinationPicked(uri, onMessage = toast)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportFilePicked(uri)
    }

    // The picker is a one-shot side effect of reaching ChoosingDestination.
    // Remembered across recreation so a rotation does not reopen the picker.
    val exportStage = state.exportStage
    var launchedExportAt by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(exportStage) {
        if (exportStage is ExportStage.ChoosingDestination && launchedExportAt != exportStage.exportedAtUtc) {
            launchedExportAt = exportStage.exportedAtUtc
            exportLauncher.launch(buildBackupFileName(exportStage.exportedAtUtc))
        }
    }

    PageScaffold(
        title = stringResource(R.string.more_backup_title),
        onBack = onBack,
        actions = { SettingsHelpAction(SettingsHelpTopic.BACKUP_RESTORE) },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.backup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ExportSection(
                    appVersionName = viewModel.appVersionName,
                    notice = state.exportNotice,
                    isExporting = state.isExporting,
                    includeHistory = state.includeHistory,
                    onIncludeHistoryChange = viewModel::setIncludeHistory,
                    enabled = !state.isBusy,
                    onExport = viewModel::startExport,
                )
            }
            item {
                SettingsGroup(title = stringResource(R.string.backup_import_group)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BackupNoticeCard(
                            title = stringResource(R.string.backup_restore_flow_title),
                            message = stringResource(R.string.backup_restore_flow_message),
                            tone = Tone.Info,
                            leadingIcon = Icons.Default.Download,
                        )
                        state.importNotice?.let { notice ->
                            NoticeCard(notice, Icons.Default.Download)
                        }
                        ProgressButton(
                            busy = state.isAnalyzingImport,
                            busyLabel = stringResource(R.string.backup_decrypting),
                            label = stringResource(R.string.backup_choose_file),
                            icon = Icons.Default.Download,
                            enabled = !state.isBusy,
                            onClick = {
                                viewModel.resetImport()
                                importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                        state.importAnalysis?.let { analysis ->
                            ImportPreviewCard(
                                analysis = analysis,
                                selectedMergeCategories = state.selectedMergeCategories,
                                onToggleCategory = viewModel::toggleCategory,
                                onConfirmRestore = { viewModel.applyImport(onMessage = toast) },
                                isApplyingImport = state.isApplyingImport,
                            )
                        }
                        state.restoreSummary?.let { summary ->
                            RestoreSummaryCard(summary = summary, onNavigate = onNavigate)
                        }
                    }
                }
            }
        }
    }

    when (val stage = state.restoreStage) {
        is RestoreStage.ChoosingMode -> RestoreModeSheet(
            onDismiss = viewModel::cancelRestoreWizard,
            onSelect = viewModel::onRestoreModeChosen,
        )
        is RestoreStage.EnteringPassword -> ImportPasswordDialog(
            error = stage.error,
            onDismiss = viewModel::cancelRestoreWizard,
            onErrorCleared = viewModel::clearPasswordError,
            onConfirm = viewModel::submitImportPassword,
        )
        RestoreStage.Idle, is RestoreStage.Analyzing -> Unit
    }

    if (exportStage == ExportStage.EnteringPassword) {
        ExportPasswordDialog(
            onDismiss = viewModel::dismissExportPassword,
            onConfirm = { password ->
                val exportedAtUtc = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
                viewModel.onExportPasswordConfirmed(password, exportedAtUtc)
            },
        )
    }
}

@Composable
private fun ExportSection(
    appVersionName: String,
    notice: BackupNotice?,
    isExporting: Boolean,
    includeHistory: Boolean,
    onIncludeHistoryChange: (Boolean) -> Unit,
    enabled: Boolean,
    onExport: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.backup_export_group)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackupNoticeCard(
                title = stringResource(R.string.backup_included_title),
                message = stringResource(R.string.backup_included_message),
                tone = Tone.Info,
                leadingIcon = Icons.Default.Info,
            )
            BackupIdentityRow(
                label = stringResource(R.string.backup_app_version),
                value = appVersionName,
                icon = Icons.Default.Backup,
            )
            BackupIdentityRow(
                label = stringResource(R.string.backup_export_time),
                value = stringResource(R.string.backup_export_time_value),
                icon = Icons.Default.CheckCircle,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val defaultCategories = SETTINGS_BACKUP_CATEGORY_MANIFEST.filter { it.includedByDefault }
                defaultCategories.forEachIndexed { index, category ->
                    BackupCategoryRow(
                        title = backupCategoryLabel(category.id, category.label),
                        summary = if (category.id == SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS) {
                            stringResource(R.string.backup_credentials_summary)
                        } else {
                            stringResource(R.string.backup_category_default_summary)
                        },
                        sensitive = category.containsSensitiveContent,
                    )
                    if (index != defaultCategories.lastIndex) {
                        HorizontalDivider()
                    }
                }
                HorizontalDivider()
                BackupOptInRow(
                    title = stringResource(R.string.backup_history_include_title),
                    summary = stringResource(R.string.backup_history_include_summary),
                    checked = includeHistory,
                    onCheckedChange = onIncludeHistoryChange,
                    enabled = enabled,
                )
            }
            notice?.let { NoticeCard(it, Icons.Default.CloudUpload) }
            ProgressButton(
                busy = isExporting,
                busyLabel = stringResource(R.string.backup_creating),
                label = stringResource(R.string.backup_create),
                icon = Icons.Default.CloudUpload,
                enabled = enabled,
                onClick = onExport,
            )
        }
    }
}

@Composable
private fun ProgressButton(
    busy: Boolean,
    busyLabel: String,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.size(12.dp))
            Text(busyLabel)
        } else {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(12.dp))
            Text(label)
        }
    }
}

@Composable
private fun NoticeCard(notice: BackupNotice, defaultIcon: ImageVector) {
    val message = stringResource(notice.message, *notice.formatArgs.toTypedArray())
    val extra = notice.extraMessage?.let { stringResource(it) }
    BackupNoticeCard(
        title = stringResource(notice.title),
        message = if (extra == null) message else stringResource(R.string.backup_notice_with_extra, message, extra),
        tone = notice.tone,
        leadingIcon = if (notice.tone == Tone.Error) Icons.Default.Warning else defaultIcon,
    )
}
