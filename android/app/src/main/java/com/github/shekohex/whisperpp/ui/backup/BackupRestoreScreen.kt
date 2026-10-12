package com.github.shekohex.whisperpp.ui.backup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.RestoreMode
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

    var showExportPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingImportUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingImportMode by rememberSaveable { mutableStateOf<RestoreMode?>(null) }
    var showRestoreModeSheet by rememberSaveable { mutableStateOf(false) }
    var showImportPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var importPasswordError by rememberSaveable { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val request = pendingExport
        pendingExport = null
        if (uri == null || request == null) {
            viewModel.onExportCancelled()
        } else {
            viewModel.export(uri, password = request.first, exportedAtUtc = request.second, onMessage = toast)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pendingImportUri = uri
        pendingImportMode = null
        viewModel.resetImport()
        showRestoreModeSheet = true
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
                    onExport = { showExportPasswordDialog = true },
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
                        val awaitingPassword = pendingImportUri != null && pendingImportMode != null &&
                            state.importAnalysis == null && !showImportPasswordDialog && !state.isAnalyzingImport
                        if (awaitingPassword) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(onClick = { showRestoreModeSheet = true }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.backup_change_mode))
                                }
                                OutlinedButton(onClick = { showImportPasswordDialog = true }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.backup_enter_password))
                                }
                            }
                        }
                        state.importAnalysis?.let { analysis ->
                            ImportPreviewCard(
                                analysis = analysis,
                                selectedMergeCategories = state.selectedMergeCategories,
                                onToggleCategory = viewModel::toggleCategory,
                                onConfirmRestore = {
                                    viewModel.applyImport(
                                        onApplied = {
                                            pendingImportUri = null
                                            pendingImportMode = null
                                        },
                                        onMessage = toast,
                                    )
                                },
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

    if (showRestoreModeSheet) {
        RestoreModeSheet(
            onDismiss = {
                showRestoreModeSheet = false
                pendingImportUri = null
            },
            onSelect = { mode ->
                pendingImportMode = mode
                showRestoreModeSheet = false
                showImportPasswordDialog = true
            },
        )
    }

    if (showExportPasswordDialog) {
        ExportPasswordDialog(
            onDismiss = { showExportPasswordDialog = false },
            onConfirm = { password ->
                val exportedAtUtc = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
                pendingExport = password to exportedAtUtc
                showExportPasswordDialog = false
                viewModel.onExportStarted()
                exportLauncher.launch(buildBackupFileName(exportedAtUtc))
            },
        )
    }

    if (showImportPasswordDialog) {
        ImportPasswordDialog(
            error = importPasswordError,
            onDismiss = {
                showImportPasswordDialog = false
                importPasswordError = null
                pendingImportUri = null
                pendingImportMode = null
            },
            onErrorCleared = { importPasswordError = null },
            onConfirm = { password ->
                val uri = pendingImportUri
                val mode = pendingImportMode
                when {
                    password.isBlank() -> importPasswordError = R.string.backup_error_import_password_blank
                    uri == null || mode == null -> importPasswordError = R.string.backup_error_no_file
                    else -> {
                        showImportPasswordDialog = false
                        viewModel.analyze(uri, mode, password) { error ->
                            importPasswordError = error
                            showImportPasswordDialog = error != null
                        }
                    }
                }
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
