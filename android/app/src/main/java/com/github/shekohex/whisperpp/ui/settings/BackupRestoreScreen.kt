package com.github.shekohex.whisperpp.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
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
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_MANIFEST
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS
import com.github.shekohex.whisperpp.data.SecretsStoreProviderCredentials
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

    var exportNotice by remember { mutableStateOf<BackupNotice?>(null) }
    var showExportPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var exportPassword by rememberSaveable { mutableStateOf("") }
    var exportPasswordConfirmation by rememberSaveable { mutableStateOf("") }
    var exportPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var exportPasswordError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportRequest by remember { mutableStateOf<ExportRequest?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    fun clearExportDialog() {
        showExportPasswordDialog = false
        exportPassword = ""
        exportPasswordConfirmation = ""
        exportPasswordVisible = false
        exportPasswordError = null
    }

    fun updateHomeStatus(status: String) {
        navController.previousBackStackEntry?.savedStateHandle?.set(BACKUP_RESTORE_HOME_STATUS_KEY, status)
        navController.previousBackStackEntry?.savedStateHandle?.set(BACKUP_RESTORE_REPAIR_COUNT_KEY, 0)
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
                    message = "$summary. The file includes ${SETTINGS_BACKUP_CATEGORY_MANIFEST.size} categories and uses the .whisperpp-backup format.",
                    tone = BackupNoticeTone.Success,
                )
                updateHomeStatus(summary)
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

    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { clearExportDialog() },
            title = { Text("Protect backup with a password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Every export is encrypted. Provider credentials can be included, so there is no plain backup path.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedPasswordField(
                        value = exportPassword,
                        label = "Backup password",
                        visible = exportPasswordVisible,
                        onValueChange = {
                            exportPassword = it
                            exportPasswordError = null
                        },
                    )
                    OutlinedPasswordField(
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
                    text = "Create password-encrypted backups from settings. Import preview and restore controls live on this screen too.",
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
                            message = "This export uses the stable backup manifest so import preview can show exactly what the file contains.",
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
                                        "Sensitive content included. API keys stay inside the encrypted backup only."
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
                            enabled = !isExporting,
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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BackupNoticeCard(
                            title = "Restore preview comes next",
                            message = "This route is ready for the dedicated import flow. The next step adds file pick, overwrite or merge choice, password gate, preview, and completion reporting here.",
                            tone = BackupNoticeTone.Info,
                            leadingIcon = Icons.Default.Info,
                        )
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
private fun OutlinedPasswordField(
    value: String,
    label: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    )
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
        BackupNoticeTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        BackupNoticeTone.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        BackupNoticeTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
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

private fun buildBackupFileName(exportedAtUtc: String): String {
    val instant = runCatching { Instant.parse(exportedAtUtc) }.getOrElse { Instant.now() }
    return "whisperpp_backup_${backupFileNameFormatter.format(instant)}.whisperpp-backup"
}

private fun formatBackupDisplayTime(exportedAtUtc: String): String {
    val instant = runCatching { Instant.parse(exportedAtUtc) }.getOrElse { return exportedAtUtc }
    return backupDisplayFormatter.format(instant)
}
