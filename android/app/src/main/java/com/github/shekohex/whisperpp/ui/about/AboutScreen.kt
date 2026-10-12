package com.github.shekohex.whisperpp.ui.about

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsItem
import com.github.shekohex.whisperpp.ui.settings.common.SettingsToggle
import com.github.shekohex.whisperpp.ui.settings.common.appVersionName
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.updater.InstallResult
import com.github.shekohex.whisperpp.updater.UpdateAvailableCard
import com.github.shekohex.whisperpp.updater.UpdateCheckResult
import com.github.shekohex.whisperpp.updater.UpdateCheckWorker
import com.github.shekohex.whisperpp.updater.updateErrorText

/** Version, update channel / check / download, advanced options and licenses. */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    showUpdate: Boolean = false,
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = containerViewModel { container, _ ->
        AboutViewModel(container, appContext, appVersionName(appContext))
    }
    val verboseLogs by viewModel.verboseLogs.collectAsStateWithLifecycle()
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    if (showLicenses) OpenSourceLicensesDialog(onDismiss = { showLicenses = false })

    PageScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsGroup(title = stringResource(R.string.about_group_app)) {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.about_version_label),
                        subtitle = viewModel.versionName,
                        onClick = {},
                    )
                    SettingsItem(
                        icon = Icons.Default.Description,
                        title = stringResource(R.string.about_licenses_title),
                        subtitle = stringResource(R.string.about_licenses_summary),
                        onClick = { showLicenses = true },
                    )
                }
            }
            item {
                UpdateSection(viewModel = viewModel, showUpdate = showUpdate)
            }
            item {
                SettingsGroup(title = stringResource(R.string.about_group_advanced)) {
                    SettingsToggle(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.about_verbose_logs_title),
                        checked = verboseLogs ?: false,
                        onCheckedChange = viewModel::setVerboseLogs,
                    )
                    Text(
                        text = stringResource(R.string.about_verbose_logs_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    SettingsItem(
                        icon = Icons.Default.Speed,
                        title = stringResource(R.string.about_provider_advanced_title),
                        subtitle = stringResource(R.string.about_provider_advanced_summary),
                        onClick = { onNavigate(Destination.ProviderList) },
                    )
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.about_diagnostics_title),
                        subtitle = stringResource(R.string.about_diagnostics_summary),
                        onClick = { onNavigate(Destination.PrivacySafety) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateSection(viewModel: AboutViewModel, showUpdate: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val checkResult by viewModel.checkResult.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val installResult by viewModel.installResult.collectAsStateWithLifecycle()
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(showUpdate, channel) {
        val loaded = channel
        if (showUpdate && loaded != null) viewModel.checkOnceOnOpen(loaded)
    }
    LaunchedEffect(installResult) {
        if (installResult is InstallResult.PermissionRequired) showPermissionDialog = true
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showPermissionDialog && viewModel.canInstallPackages()) {
                showPermissionDialog = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null) },
            title = { Text(stringResource(R.string.about_update_permission_title)) },
            text = { Text(stringResource(R.string.about_update_permission_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::openInstallPermissionSettings) {
                    Text(stringResource(R.string.about_update_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.component_cancel))
                }
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.settings_check_for_updates)) {
        val stableLabel = stringResource(R.string.settings_update_channel_stable)
        val nightlyLabel = stringResource(R.string.settings_update_channel_nightly)
        SettingsDropdown(
            label = stringResource(R.string.settings_update_channel),
            value = if (channel == AboutViewModel.CHANNEL_NIGHTLY) nightlyLabel else stableLabel,
            options = listOf(false, true),
            optionLabel = { nightly -> if (nightly) nightlyLabel else stableLabel },
            onSelect = viewModel::setChannel,
            enabled = channel != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        UpdateCheckWorker.needsNotificationPermission(context)
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.checkForUpdate()
                },
                enabled = !isChecking && channel != null,
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.update_checking))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_check_for_updates))
                }
            }
        }

        when (val result = checkResult) {
            is UpdateCheckResult.Available -> UpdateAvailableCard(
                release = result.release,
                downloadState = downloadState,
                onDownload = viewModel::download,
                onInstall = viewModel::install,
                onCancel = viewModel::cancelDownload,
                modifier = Modifier.padding(16.dp),
            )
            is UpdateCheckResult.UpToDate -> Text(
                text = stringResource(R.string.update_up_to_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
            is UpdateCheckResult.Error -> Text(
                text = updateErrorText(result.error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            null -> Unit
        }
        if (installResult is InstallResult.Error) {
            Text(
                text = stringResource(R.string.update_install_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
