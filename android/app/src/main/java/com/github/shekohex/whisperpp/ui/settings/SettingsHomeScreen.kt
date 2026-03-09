package com.github.shekohex.whisperpp.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.github.shekohex.whisperpp.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.AUTO_RECORDING_START
import com.github.shekohex.whisperpp.AUTO_SWITCH_BACK
import com.github.shekohex.whisperpp.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.HAPTIC_FEEDBACK_ENABLED
import com.github.shekohex.whisperpp.PER_APP_SEND_POLICY_JSON
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.SOUND_EFFECTS_ENABLED
import com.github.shekohex.whisperpp.VERBOSE_NETWORK_LOGS_ENABLED
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot
import com.github.shekohex.whisperpp.analytics.analyticsDataStore
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_CLEANUP
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESET_ID_TONE_REWRITE
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.data.validateSelections
import org.json.JSONObject

private data class SettingsSetupIssue(
    val message: String,
    val actionLabel: String,
    val onFix: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    dataStore: DataStore<Preferences>,
    navController: NavHostController,
    showUpdate: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { SettingsRepository(dataStore) }
    val analyticsRepository = remember(context) { AnalyticsRepository(context.analyticsDataStore) }
    val homeEntry = remember(navController) { navController.getBackStackEntry(SettingsScreen.Main.route) }
    val settingsState by dataStore.data.collectAsState(initial = emptyPreferences())
    val providers by repository.providers.collectAsState(initial = emptyList())
    val promptProfiles by repository.promptProfiles.collectAsState(initial = emptyList())
    val appMappings by repository.appPromptMappings.collectAsState(initial = emptyList())
    val languageDefaults by repository.profiles.collectAsState(initial = emptyList())
    val basePrompt by repository.globalBasePrompt.collectAsState(initial = "")
    val enhancementPresetId by repository.enhancementPresetId.collectAsState(initial = TRANSFORM_PRESET_ID_CLEANUP)
    val commandPresetId by repository.commandPresetId.collectAsState(initial = TRANSFORM_PRESET_ID_TONE_REWRITE)
    val analyticsSnapshot by analyticsRepository.snapshot.collectAsState(initial = AnalyticsSnapshot())
    val backupRestoreStatus by homeEntry.savedStateHandle
        .getStateFlow(BACKUP_RESTORE_HOME_STATUS_KEY, "")
        .collectAsState()
    val backupRestoreRepairCount by homeEntry.savedStateHandle
        .getStateFlow(BACKUP_RESTORE_REPAIR_COUNT_KEY, 0)
        .collectAsState()
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var isImeEnabled by remember { mutableStateOf(isImeEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasMicPermission = granted },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                isImeEnabled = isImeEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val validation = remember(
        providers,
        settingsState[ACTIVE_STT_PROVIDER_ID],
        settingsState[ACTIVE_STT_MODEL_ID],
        settingsState[ACTIVE_TEXT_PROVIDER_ID],
        settingsState[ACTIVE_TEXT_MODEL_ID],
        settingsState[COMMAND_TEXT_PROVIDER_ID],
        settingsState[COMMAND_TEXT_MODEL_ID],
    ) {
        validateSelections(
            providers = providers,
            sttProviderId = settingsState[ACTIVE_STT_PROVIDER_ID].orEmpty(),
            sttModelId = settingsState[ACTIVE_STT_MODEL_ID].orEmpty(),
            textProviderId = settingsState[ACTIVE_TEXT_PROVIDER_ID].orEmpty(),
            textModelId = settingsState[ACTIVE_TEXT_MODEL_ID].orEmpty(),
            commandProviderId = settingsState[COMMAND_TEXT_PROVIDER_ID].orEmpty(),
            commandModelId = settingsState[COMMAND_TEXT_MODEL_ID].orEmpty(),
        )
    }

    LaunchedEffect(validation.keysToClear) {
        if (validation.keysToClear.isEmpty()) {
            return@LaunchedEffect
        }
        dataStore.edit { prefs ->
            validation.keysToClear.forEach(prefs::remove)
        }
    }

    val setupIssues = remember(providers, validation, settingsState, backupRestoreRepairCount) {
        buildList {
            if (providers.isEmpty()) {
                add(
                    SettingsSetupIssue(
                        message = "Add a provider before choosing dictation or enhancement defaults.",
                        actionLabel = "Add provider",
                        onFix = { navController.navigate(SettingsScreen.Backend.route) },
                    ),
                )
            }
            if (!validation.isSttValid) {
                add(
                    SettingsSetupIssue(
                        message = "Choose a valid dictation provider and model.",
                        actionLabel = "Fix dictation",
                        onFix = { navController.navigate(SettingsScreen.ProviderSelections.route) },
                    ),
                )
            }
            if (!validation.isTextValid) {
                add(
                    SettingsSetupIssue(
                        message = "Choose a valid enhancement provider and model.",
                        actionLabel = "Fix enhancement",
                        onFix = { navController.navigate(SettingsScreen.ProviderSelections.route) },
                    ),
                )
            }
            val commandOverridePresent = settingsState[COMMAND_TEXT_PROVIDER_ID].orEmpty().isNotBlank() ||
                settingsState[COMMAND_TEXT_MODEL_ID].orEmpty().isNotBlank()
            if (commandOverridePresent && !validation.isCommandOverrideValid) {
                add(
                    SettingsSetupIssue(
                        message = "Command override is stale. Clear it or choose a valid model.",
                        actionLabel = "Review override",
                        onFix = { navController.navigate(SettingsScreen.ProviderSelections.route) },
                    ),
                )
            }
            if (backupRestoreRepairCount > 0) {
                add(
                    SettingsSetupIssue(
                        message = "Last restore still needs $backupRestoreRepairCount ${pluralize(backupRestoreRepairCount, "repair step", "repair steps")}.",
                        actionLabel = "Review restore",
                        onFix = { navController.navigate(SettingsScreen.BackupRestore.route) },
                    ),
                )
            }
        }
    }

    val totalModels = remember(providers) { providers.sumOf { it.models.size } }
    val activeSttLabel = remember(providers, settingsState[ACTIVE_STT_PROVIDER_ID], settingsState[ACTIVE_STT_MODEL_ID]) {
        resolveSelectionLabel(
            providers = providers,
            providerId = settingsState[ACTIVE_STT_PROVIDER_ID].orEmpty(),
            modelId = settingsState[ACTIVE_STT_MODEL_ID].orEmpty(),
        )
    }
    val activeTextLabel = remember(providers, settingsState[ACTIVE_TEXT_PROVIDER_ID], settingsState[ACTIVE_TEXT_MODEL_ID]) {
        resolveSelectionLabel(
            providers = providers,
            providerId = settingsState[ACTIVE_TEXT_PROVIDER_ID].orEmpty(),
            modelId = settingsState[ACTIVE_TEXT_MODEL_ID].orEmpty(),
        )
    }
    val enhancementPresetTitle = presetById(enhancementPresetId)?.let { preset ->
        context.getString(preset.titleRes)
    } ?: context.getString(R.string.transform_preset_cleanup_title)
    val commandPresetTitle = presetById(commandPresetId)?.let { preset ->
        context.getString(preset.titleRes)
    } ?: context.getString(R.string.transform_preset_tone_rewrite_title)
    val blockedAppCount = remember(settingsState[PER_APP_SEND_POLICY_JSON]) {
        countBlockedApps(settingsState[PER_APP_SEND_POLICY_JSON])
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                actions = {
                    SettingsHelpAction(SettingsScreen.Main.route)
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (setupIssues.isNotEmpty()) {
                item {
                    SettingsSetupBanner(
                        issues = setupIssues,
                        onPrimaryAction = { setupIssues.firstOrNull()?.onFix?.invoke() },
                    )
                }
            }

            item {
                AnalyticsDashboardCard(
                    snapshot = analyticsSnapshot,
                    onClick = { navController.navigate(SettingsScreen.Analytics.route) },
                )
            }

            item {
                SettingsGroup(title = "Setup essentials") {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsSystemReadinessCard(
                            hasMicPermission = hasMicPermission,
                            isImeEnabled = isImeEnabled,
                            onGrantMic = {
                                if (hasMicPermission) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        },
                                    )
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onOpenIme = {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                        )

                        SettingsOverviewCard(
                            icon = Icons.Default.SettingsSuggest,
                            title = "Provider selections",
                            status = "Dictation: $activeSttLabel • Enhancement: $activeTextLabel",
                            guidance = if (validation.isSttValid && validation.isTextValid) {
                                "Review defaults, command override, and language-specific fallbacks."
                            } else {
                                "Choose valid STT and text models before relying on dictation or Smart Fix."
                            },
                            onClick = { navController.navigate(SettingsScreen.ProviderSelections.route) },
                        )

                        SettingsOverviewCard(
                            icon = Icons.Default.Dns,
                            title = "Providers & models",
                            status = when {
                                providers.isEmpty() -> "No providers configured"
                                else -> "${providers.size} ${pluralize(providers.size, "provider", "providers")} • $totalModels ${pluralize(totalModels, "model", "models")}"
                            },
                            guidance = if (providers.isEmpty()) {
                                "Add your first provider, endpoint, and model list here."
                            } else {
                                "Manage provider endpoints, credentials, and available model lists."
                            },
                            onClick = { navController.navigate(SettingsScreen.Backend.route) },
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Personalization") {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsOverviewCard(
                            icon = Icons.Default.TextFields,
                            title = "Prompts, profiles & mappings",
                            status = buildString {
                                append(if (basePrompt.isBlank()) "Base prompt off" else "Base prompt saved")
                                append(" • ${promptProfiles.size} ${pluralize(promptProfiles.size, "profile", "profiles")}")
                                append(" • ${appMappings.size} ${pluralize(appMappings.size, "mapping", "mappings")}")
                            },
                            guidance = if (promptProfiles.isEmpty()) {
                                "Create reusable prompt profiles, then map them to your most-used apps."
                            } else {
                                "Adjust the base prompt or drill in to review per-app prompt behavior."
                            },
                            onClick = { navController.navigate(SettingsScreen.PromptsProfiles.route) },
                        )

                        SettingsOverviewCard(
                            icon = Icons.Default.Translate,
                            title = "Per-language defaults",
                            status = when {
                                languageDefaults.isEmpty() -> "No language-specific overrides"
                                else -> "${languageDefaults.size} ${pluralize(languageDefaults.size, "language", "languages")} configured"
                            },
                            guidance = "Use language rules when one global provider setup is not enough.",
                            onClick = { navController.navigate(SettingsScreen.LanguageDefaults.route) },
                        )

                        SettingsOverviewCard(
                            icon = Icons.Default.AutoFixHigh,
                            title = "Transform presets",
                            status = "Enhancement: $enhancementPresetTitle • Command: $commandPresetTitle",
                            guidance = "Choose the defaults Whisper++ should use before dictation cleanup or command mode transforms.",
                            onClick = { navController.navigate(SettingsScreen.Presets.route) },
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Behavior & safety") {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsOverviewCard(
                            icon = Icons.Default.Keyboard,
                            title = "Keyboard behavior",
                            status = "Auto start ${onOff(settingsState[AUTO_RECORDING_START] ?: true)} • Haptics ${onOff(settingsState[HAPTIC_FEEDBACK_ENABLED] ?: true)} • Switch back ${onOff(settingsState[AUTO_SWITCH_BACK] ?: false)}",
                            guidance = "Tune recording automation, typing behavior, and IME feedback from one place.",
                            onClick = { navController.navigate(SettingsScreen.Keyboard.route) },
                        )

                        SettingsOverviewCard(
                            icon = Icons.Default.Security,
                            title = "Privacy & safety",
                            status = "${blockedAppCount} ${pluralize(blockedAppCount, "blocked app", "blocked apps")} • Verbose logs ${onOff(settingsState[VERBOSE_NETWORK_LOGS_ENABLED] ?: false)} • Sound ${onOff(settingsState[SOUND_EFFECTS_ENABLED] ?: true)}",
                            guidance = "Review disclosures, blocked apps, and diagnostics before sending text or audio externally.",
                            onClick = { navController.navigate(SettingsScreen.PrivacySafety.route) },
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Backup & restore") {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsOverviewCard(
                            icon = Icons.Default.Backup,
                            title = "Backup & restore",
                            status = backupRestoreStatus.ifBlank {
                                if (backupRestoreRepairCount > 0) {
                                    "Repair attention needed"
                                } else {
                                    "No backup yet"
                                }
                            },
                            guidance = if (backupRestoreRepairCount > 0) {
                                "Review the last restore summary, skipped items, and repair checklist before relying on those settings."
                            } else {
                                "Create password-encrypted backups, preview imports, and restore safely without leaving settings."
                            },
                            onClick = { navController.navigate(SettingsScreen.BackupRestore.route) },
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Maintenance",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SettingsOverviewCard(
                                icon = Icons.Default.Backup,
                                title = "Updates",
                                status = "Keep this area secondary unless you are checking releases or changing channels.",
                                guidance = "Use stable for reliability or nightly when you want the newest builds first.",
                                onClick = {},
                                showChevron = false,
                                enabled = false,
                            )
                            UpdateSettingsSection(dataStore, showUpdate)
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
private fun SettingsSetupBanner(
    issues: List<SettingsSetupIssue>,
    onPrimaryAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Setup required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "Whisper++ needs attention before dictation and enhancement are fully ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                issues.take(3).forEach { issue ->
                    Text(
                        text = "• ${issue.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            TextButton(
                onClick = onPrimaryAction,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Text(issues.firstOrNull()?.actionLabel ?: "Review setup")
            }
        }
    }
}

@Composable
private fun SettingsSystemReadinessCard(
    hasMicPermission: Boolean,
    isImeEnabled: Boolean,
    onGrantMic: () -> Unit,
    onOpenIme: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsOverviewCard(
            icon = Icons.Default.Mic,
            title = "Device access",
            status = "Microphone ${if (hasMicPermission) "ready" else "needs permission"} • Keyboard ${if (isImeEnabled) "enabled" else "not enabled"}",
            guidance = if (!hasMicPermission) {
                "Grant microphone access so Whisper++ can start recording."
            } else if (!isImeEnabled) {
                "Enable Whisper++ in Android input settings before trying it in other apps."
            } else {
                "System prerequisites look good. Continue into provider and behavior setup."
            },
            onClick = if (!hasMicPermission) onGrantMic else onOpenIme,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Mic,
                title = "Microphone",
                status = if (hasMicPermission) "Granted" else "Tap to grant",
                onClick = onGrantMic,
                highlighted = !hasMicPermission,
            )
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Keyboard,
                title = "Keyboard",
                status = if (isImeEnabled) "Enabled" else "Tap to enable",
                onClick = onOpenIme,
                highlighted = !isImeEnabled,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    onClick: () -> Unit,
    highlighted: Boolean,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsOverviewCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    guidance: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    enabled: Boolean = true,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)

    Surface(
        modifier = if (enabled) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showChevron) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun resolveSelectionLabel(
    providers: List<com.github.shekohex.whisperpp.data.ServiceProvider>,
    providerId: String,
    modelId: String,
): String {
    val provider = providers.firstOrNull { it.id == providerId }
    val model = provider?.models?.firstOrNull { it.id == modelId }
    return when {
        provider == null || model == null -> "Needs setup"
        else -> "${provider.name} / ${model.name}"
    }
}

private fun countBlockedApps(rawJson: String?): Int {
    val json = rawJson?.trim().orEmpty()
    if (json.isBlank()) {
        return 0
    }
    return try {
        val root = JSONObject(json)
        root.keys().asSequence().count { key -> root.optBoolean(key, false) }
    } catch (_: Exception) {
        0
    }
}

private fun pluralize(count: Int, singular: String, plural: String): String {
    return if (count == 1) singular else plural
}

private fun onOff(enabled: Boolean): String {
    return if (enabled) "on" else "off"
}

private fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}
