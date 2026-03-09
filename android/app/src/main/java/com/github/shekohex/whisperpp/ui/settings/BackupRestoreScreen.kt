package com.github.shekohex.whisperpp.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.NavHostController
import com.github.shekohex.whisperpp.data.ImportAnalysis
import com.github.shekohex.whisperpp.data.RestoreMode
import com.github.shekohex.whisperpp.data.RestoreRepairArea
import com.github.shekohex.whisperpp.data.RestoreSelectionType
import com.github.shekohex.whisperpp.data.RestoreSummary
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_MANIFEST
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS
import com.github.shekohex.whisperpp.data.SecretsStoreProviderCredentials
import com.github.shekohex.whisperpp.data.SettingsBackupEnvelope
import com.github.shekohex.whisperpp.data.SettingsBackupRepository
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

const val BACKUP_RESTORE_HOME_STATUS_KEY = "backup_restore_home_status"
const val BACKUP_RESTORE_REPAIR_COUNT_KEY = "backup_restore_repair_count"

private data class ExportRequest(
    val password: String,
    val exportedAtUtc: String,
)

private enum class BackupNoticeTone {
    Info,
    Success,
    Warning,
    Error,
}

private data class BackupNotice(
    val title: String,
    val message: String,
    val tone: BackupNoticeTone,
)

private val backupFileNameFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.of("UTC"))

private val backupDisplayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    dataStore: DataStore<Preferences>,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val appVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
    val credentials = remember(context) {
        SecretsStoreProviderCredentials(SecretsStore(context))
    }
    val repository = remember(dataStore, credentials, appVersionName) {
        SettingsBackupRepository(
            dataStore = dataStore,
            credentialSource = credentials,
            credentialSink = credentials,
            gson = gson,
            currentAppVersionNameProvider = { appVersionName },
        )
    }
    val categoryLabelById = remember {
        SETTINGS_BACKUP_CATEGORY_MANIFEST.associateBy { it.id }
    }

    var exportNotice by remember { mutableStateOf<BackupNotice?>(null) }
    var importNotice by remember { mutableStateOf<BackupNotice?>(null) }
    var importAnalysis by remember { mutableStateOf<ImportAnalysis?>(null) }
    var restoreSummary by remember { mutableStateOf<RestoreSummary?>(null) }
    var selectedMergeCategories by remember { mutableStateOf(setOf<String>()) }

    var showExportPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var exportPassword by rememberSaveable { mutableStateOf("") }
    var exportPasswordConfirmation by rememberSaveable { mutableStateOf("") }
    var exportPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var exportPasswordError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportRequest by remember { mutableStateOf<ExportRequest?>(null) }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportMode by remember { mutableStateOf<RestoreMode?>(null) }
    var showRestoreModeSheet by rememberSaveable { mutableStateOf(false) }
    var showImportPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var importPassword by rememberSaveable { mutableStateOf("") }
    var importPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var importPasswordError by rememberSaveable { mutableStateOf<String?>(null) }

    var isExporting by remember { mutableStateOf(false) }
    var isAnalyzingImport by remember { mutableStateOf(false) }
    var isApplyingImport by remember { mutableStateOf(false) }

    fun updateHomeStatus(status: String, repairCount: Int) {
        navController.previousBackStackEntry?.savedStateHandle?.set(BACKUP_RESTORE_HOME_STATUS_KEY, status)
        navController.previousBackStackEntry?.savedStateHandle?.set(BACKUP_RESTORE_REPAIR_COUNT_KEY, repairCount)
    }

    fun clearExportDialog() {
        showExportPasswordDialog = false
        exportPassword = ""
        exportPasswordConfirmation = ""
        exportPasswordVisible = false
        exportPasswordError = null
    }

    fun clearImportPasswordDialog(clearPendingImport: Boolean) {
        showImportPasswordDialog = false
        importPassword = ""
        importPasswordVisible = false
        importPasswordError = null
        if (clearPendingImport) {
            pendingImportUri = null
            pendingImportMode = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val request = pendingExportRequest
        pendingExportRequest = null
        if (uri == null || request == null) {
            isExporting = false
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                val envelope = repository.exportEncryptedBackup(
                    password = request.password,
                    appVersionName = appVersionName,
                    exportedAtUtc = request.exportedAtUtc,
                )
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open the selected destination.")
                output.use { stream ->
                    stream.write(gson.toJson(envelope).toByteArray())
                }
                val summary = "Last export ${formatBackupDisplayTime(request.exportedAtUtc)}"
                exportNotice = BackupNotice(
                    title = "Encrypted backup ready",
                    message = "$summary. All ${SETTINGS_BACKUP_CATEGORY_MANIFEST.size} categories were written to a .whisperpp-backup file.",
                    tone = BackupNoticeTone.Success,
                )
                updateHomeStatus(summary, repairCount = 0)
                Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show()
            } catch (exception: Exception) {
                exportNotice = BackupNotice(
                    title = "Export failed",
                    message = exception.message ?: "Could not create the encrypted backup.",
                    tone = BackupNoticeTone.Error,
                )
                Toast.makeText(
                    context,
                    "Backup export failed: ${exception.message ?: "unknown error"}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                isExporting = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pendingImportUri = uri
        pendingImportMode = null
        importAnalysis = null
        restoreSummary = null
        selectedMergeCategories = emptySet()
        importNotice = null
        showRestoreModeSheet = true
    }

    if (showRestoreModeSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showRestoreModeSheet = false
                pendingImportUri = null
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Choose how to restore this backup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Overwrite restores every category found in the file. Merge lets you preview and include or exclude whole categories before applying anything.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                RestoreModeOption(
                    title = "Overwrite",
                    summary = "Replace every category present in the backup file.",
                    onClick = {
                        pendingImportMode = RestoreMode.OVERWRITE
                        showRestoreModeSheet = false
                        showImportPasswordDialog = true
                    },
                )
                RestoreModeOption(
                    title = "Merge",
                    summary = "Preview the backup first, then choose which categories to include. Imported values win conflicts.",
                    onClick = {
                        pendingImportMode = RestoreMode.MERGE
                        showRestoreModeSheet = false
                        showImportPasswordDialog = true
                    },
                )

                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { clearExportDialog() },
            title = { Text("Protect backup with a password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Every export is encrypted. Provider credentials can be included, so there is no plain export path.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PasswordField(
                        value = exportPassword,
                        label = "Backup password",
                        visible = exportPasswordVisible,
                        onValueChange = {
                            exportPassword = it
                            exportPasswordError = null
                        },
                    )
                    PasswordField(
                        value = exportPasswordConfirmation,
                        label = "Confirm password",
                        visible = exportPasswordVisible,
                        onValueChange = {
                            exportPasswordConfirmation = it
                            exportPasswordError = null
                        },
                    )
                    if (exportPasswordError != null) {
                        Text(
                            text = exportPasswordError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { exportPasswordVisible = !exportPasswordVisible }) {
                        Text(if (exportPasswordVisible) "Hide password" else "Show password")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            exportPassword.isBlank() -> exportPasswordError = "Enter a password before creating the backup."
                            exportPassword != exportPasswordConfirmation -> exportPasswordError = "Passwords do not match."
                            else -> {
                                val exportedAtUtc = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
                                pendingExportRequest = ExportRequest(
                                    password = exportPassword,
                                    exportedAtUtc = exportedAtUtc,
                                )
                                isExporting = true
                                clearExportDialog()
                                exportLauncher.launch(buildBackupFileName(exportedAtUtc))
                            }
                        }
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearExportDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showImportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { clearImportPasswordDialog(clearPendingImport = true) },
            title = { Text("Unlock backup preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter the password before previewing or applying anything from this backup.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PasswordField(
                        value = importPassword,
                        label = "Backup password",
                        visible = importPasswordVisible,
                        onValueChange = {
                            importPassword = it
                            importPasswordError = null
                        },
                    )
                    if (importPasswordError != null) {
                        Text(
                            text = importPasswordError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { importPasswordVisible = !importPasswordVisible }) {
                        Text(if (importPasswordVisible) "Hide password" else "Show password")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        val mode = pendingImportMode
                        when {
                            importPassword.isBlank() -> importPasswordError = "Enter the backup password to continue."
                            uri == null || mode == null -> importPasswordError = "Choose a backup file and restore mode first."
                            else -> {
                                showImportPasswordDialog = false
                                isAnalyzingImport = true
                                scope.launch {
                                    try {
                                        val raw = context.contentResolver.openInputStream(uri)
                                            ?.bufferedReader()
                                            ?.use { it.readText() }
                                            ?: error("Could not read the selected backup file.")
                                        val envelope = gson.fromJson(raw, SettingsBackupEnvelope::class.java)
                                            ?: error("Invalid backup file.")
                                        val analysis = repository.analyzeEncryptedBackup(
                                            envelope = envelope,
                                            password = importPassword,
                                            restoreMode = mode,
                                        )
                                        importAnalysis = analysis
                                        restoreSummary = null
                                        selectedMergeCategories = analysis.categoryPreviews
                                            .filter { it.selectable && it.includedByDefault }
                                            .map { it.categoryId }
                                            .toSet()
                                        importNotice = BackupNotice(
                                            title = "Preview ready",
                                            message = "Review the restore preview before confirming any changes.",
                                            tone = BackupNoticeTone.Info,
                                        )
                                        clearImportPasswordDialog(clearPendingImport = false)
                                    } catch (exception: Exception) {
                                        importPasswordError = exception.message ?: "The backup could not be decrypted or parsed."
                                        importNotice = BackupNotice(
                                            title = "Could not preview backup",
                                            message = importPasswordError.orEmpty(),
                                            tone = BackupNoticeTone.Error,
                                        )
                                        showImportPasswordDialog = true
                                    } finally {
                                        isAnalyzingImport = false
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Text("Preview restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearImportPasswordDialog(clearPendingImport = true) }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    SettingsHelpAction(SettingsScreen.BackupRestore.route)
                },
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Create password-encrypted backups, then restore them through file pick, mode choice, password, preview, and confirmation without leaving settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SettingsGroup(title = "Export encrypted backup") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BackupNoticeCard(
                            title = "Included categories",
                            message = "Every backup uses the stable category manifest so import preview can list exactly what the file contains, including sensitive contents.",
                            tone = BackupNoticeTone.Info,
                            leadingIcon = Icons.Default.Info,
                        )
                        BackupIdentityRow(
                            label = "App version",
                            value = appVersionName,
                            icon = Icons.Default.Backup,
                        )
                        BackupIdentityRow(
                            label = "Export timestamp",
                            value = "Generated when you create the file",
                            icon = Icons.Default.CheckCircle,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SETTINGS_BACKUP_CATEGORY_MANIFEST.forEachIndexed { index, category ->
                                BackupCategoryRow(
                                    title = category.label,
                                    summary = if (category.id == SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS) {
                                        "Sensitive content included. Provider credentials stay inside the encrypted backup only."
                                    } else {
                                        "Included in every encrypted backup file."
                                    },
                                    sensitive = category.containsSensitiveContent,
                                )
                                if (index != SETTINGS_BACKUP_CATEGORY_MANIFEST.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }
                        exportNotice?.let { notice ->
                            BackupNoticeCard(
                                title = notice.title,
                                message = notice.message,
                                tone = notice.tone,
                                leadingIcon = if (notice.tone == BackupNoticeTone.Error) Icons.Default.Warning else Icons.Default.CloudUpload,
                            )
                        }
                        Button(
                            onClick = { showExportPasswordDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting && !isAnalyzingImport && !isApplyingImport,
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.size(12.dp))
                                Text("Creating encrypted backup…")
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.size(12.dp))
                                Text("Create encrypted backup")
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = "Import backup") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BackupNoticeCard(
                            title = "Restore flow",
                            message = "Choose a file first. Then select Overwrite or Merge, enter the password, preview the changes, and explicitly confirm the restore.",
                            tone = BackupNoticeTone.Info,
                            leadingIcon = Icons.Default.Download,
                        )
                        importNotice?.let { notice ->
                            BackupNoticeCard(
                                title = notice.title,
                                message = notice.message,
                                tone = notice.tone,
                                leadingIcon = if (notice.tone == BackupNoticeTone.Error) Icons.Default.Warning else Icons.Default.Download,
                            )
                        }
                        Button(
                            onClick = {
                                importAnalysis = null
                                restoreSummary = null
                                selectedMergeCategories = emptySet()
                                importNotice = null
                                importLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting && !isAnalyzingImport && !isApplyingImport,
                        ) {
                            if (isAnalyzingImport) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.size(12.dp))
                                Text("Decrypting backup…")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.size(12.dp))
                                Text("Choose backup file")
                            }
                        }

                        if (pendingImportUri != null && pendingImportMode != null && importAnalysis == null && !showImportPasswordDialog && !isAnalyzingImport) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { showRestoreModeSheet = true },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Change mode")
                                }
                                OutlinedButton(
                                    onClick = { showImportPasswordDialog = true },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Enter password")
                                }
                            }
                        }

                        importAnalysis?.let { analysis ->
                            ImportPreviewCard(
                                analysis = analysis,
                                selectedMergeCategories = selectedMergeCategories,
                                onToggleCategory = { categoryId ->
                                    selectedMergeCategories = if (selectedMergeCategories.contains(categoryId)) {
                                        selectedMergeCategories - categoryId
                                    } else {
                                        selectedMergeCategories + categoryId
                                    }
                                },
                                onConfirmRestore = {
                                    isApplyingImport = true
                                    scope.launch {
                                        try {
                                            val summary = repository.applyImportAnalysis(
                                                analysis = analysis,
                                                includedCategoryIds = if (analysis.restoreMode == RestoreMode.MERGE) selectedMergeCategories else emptySet(),
                                            )
                                            restoreSummary = summary
                                            importAnalysis = null
                                            pendingImportUri = null
                                            pendingImportMode = null
                                            selectedMergeCategories = emptySet()
                                            val repairCount = summary.repairChecklist.size
                                            val homeStatus = if (repairCount > 0) {
                                                "Restore complete · $repairCount ${backupPluralize(repairCount, "repair item", "repair items")}"
                                            } else {
                                                "Restore complete"
                                            }
                                            updateHomeStatus(homeStatus, repairCount)
                                            importNotice = BackupNotice(
                                                title = if (repairCount > 0) "Restore complete with follow-up" else "Restore complete",
                                                message = if (repairCount > 0) {
                                                    "Review the repair checklist below before relying on the restored selections."
                                                } else {
                                                    "Restore finished and you stayed inside settings to review the completion summary."
                                                },
                                                tone = if (repairCount > 0) BackupNoticeTone.Warning else BackupNoticeTone.Success,
                                            )
                                            Toast.makeText(
                                                context,
                                                if (repairCount > 0) "Restore complete with repair items" else "Restore complete",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } catch (exception: Exception) {
                                            importNotice = BackupNotice(
                                                title = "Restore failed",
                                                message = exception.message ?: "The backup could not be applied.",
                                                tone = BackupNoticeTone.Error,
                                            )
                                            Toast.makeText(
                                                context,
                                                "Restore failed: ${exception.message ?: "unknown error"}",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        } finally {
                                            isApplyingImport = false
                                        }
                                    }
                                },
                                categoryLabelById = categoryLabelById,
                                isApplyingImport = isApplyingImport,
                            )
                        }

                        restoreSummary?.let { summary ->
                            RestoreSummaryCard(
                                summary = summary,
                                categoryLabelById = categoryLabelById,
                                onNavigate = { route -> navController.navigate(route) },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    )
}

@Composable
private fun RestoreModeOption(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupIdentityRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(icon, contentDescription = null) },
    )
}

@Composable
private fun BackupCategoryRow(
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

@Composable
private fun BackupNoticeCard(
    title: String,
    message: String,
    tone: BackupNoticeTone,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val containerColor = when (tone) {
        BackupNoticeTone.Info -> MaterialTheme.colorScheme.secondaryContainer
        BackupNoticeTone.Success -> MaterialTheme.colorScheme.primaryContainer
        BackupNoticeTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        BackupNoticeTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        BackupNoticeTone.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        BackupNoticeTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        BackupNoticeTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        BackupNoticeTone.Error -> MaterialTheme.colorScheme.onErrorContainer
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
private fun ImportPreviewCard(
    analysis: ImportAnalysis,
    selectedMergeCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onConfirmRestore: () -> Unit,
    categoryLabelById: Map<String, com.github.shekohex.whisperpp.data.SettingsBackupCategoryManifestEntry>,
    isApplyingImport: Boolean,
) {
    val canConfirm = analysis.restoreMode == RestoreMode.OVERWRITE || selectedMergeCategories.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BackupNoticeCard(
            title = "Preview before restore",
            message = "Backup from ${formatBackupDisplayTime(analysis.exportedAtUtc)} · app ${analysis.backupAppVersionName} · ${restoreModeLabel(analysis.restoreMode)}",
            tone = BackupNoticeTone.Info,
            leadingIcon = Icons.Default.Info,
        )

        if (analysis.restoreMode == RestoreMode.MERGE) {
            BackupNoticeCard(
                title = "Conflict behavior",
                message = "Merge restores only the categories you keep selected here. When the same item exists locally, the imported value wins.",
                tone = BackupNoticeTone.Info,
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
                    if (analysis.restoreMode == RestoreMode.MERGE && preview.selectable) {
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
private fun RestoreSummaryCard(
    summary: RestoreSummary,
    categoryLabelById: Map<String, com.github.shekohex.whisperpp.data.SettingsBackupCategoryManifestEntry>,
    onNavigate: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                                action?.let { (route, label) ->
                                    TextButton(onClick = { onNavigate(route) }) {
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

private fun repairActionFor(area: RestoreRepairArea): Pair<String, String>? {
    return when (area) {
        RestoreRepairArea.PROVIDER_CREDENTIALS -> SettingsScreen.Backend.route to "Open providers"
        RestoreRepairArea.ACTIVE_STT,
        RestoreRepairArea.ACTIVE_TEXT,
        RestoreRepairArea.COMMAND_TEXT
        -> SettingsScreen.ProviderSelections.route to "Open provider selections"
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

private fun buildBackupFileName(exportedAtUtc: String): String {
    val instant = runCatching { Instant.parse(exportedAtUtc) }.getOrElse { Instant.now() }
    return "whisperpp_backup_${backupFileNameFormatter.format(instant)}.whisperpp-backup"
}

private fun formatBackupDisplayTime(exportedAtUtc: String): String {
    val instant = runCatching { Instant.parse(exportedAtUtc) }.getOrElse { return exportedAtUtc }
    return backupDisplayFormatter.format(instant)
}

private fun backupPluralize(count: Int, singular: String, plural: String): String {
    return if (count == 1) singular else plural
}
