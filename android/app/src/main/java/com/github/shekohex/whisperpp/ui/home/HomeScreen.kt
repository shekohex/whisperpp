package com.github.shekohex.whisperpp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.analytics.AnalyticsSnapshot
import com.github.shekohex.whisperpp.data.BackupStatusStore
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_RECORDING_START
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_SWITCH_BACK
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.HAPTIC_FEEDBACK_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.PER_APP_SEND_POLICY_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.SOUND_EFFECTS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.domain.setup.SetupStatus
import com.github.shekohex.whisperpp.ui.analytics.AnalyticsDashboardCard
import com.github.shekohex.whisperpp.ui.components.LoadingState
import com.github.shekohex.whisperpp.ui.components.RefreshOnResume
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    viewModel: HomeViewModel = containerViewModel { container, _ -> HomeViewModel(container) },
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val setupStatus by viewModel.setupStatus.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refreshSetupStatus)

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                actions = { SettingsHelpAction(SettingsHelpTopic.HOME) },
            )
        },
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        val current = summary
        if (current == null) {
            LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            HomeContent(
                summary = current,
                analytics = analytics ?: AnalyticsSnapshot(),
                backupStatus = backupStatus,
                setupStatus = setupStatus,
                onNavigate = onNavigate,
                onMicRequested = viewModel::setMicRequested,
                onMicResult = viewModel::refreshSetupStatus,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun HomeContent(
    summary: HomeViewModel.Summary,
    analytics: AnalyticsSnapshot,
    backupStatus: BackupStatusStore.Status,
    setupStatus: SetupStatus?,
    onNavigate: (Destination) -> Unit,
    onMicRequested: () -> Unit,
    onMicResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setupIssues = remember(summary, backupStatus.repairCount) {
        buildSetupIssues(summary, backupStatus.repairCount, onNavigate)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (setupIssues.isNotEmpty()) {
            item {
                HomeSetupBanner(
                    issues = setupIssues,
                    onPrimaryAction = { setupIssues.firstOrNull()?.onFix?.invoke() },
                )
            }
        }
        item {
            AnalyticsDashboardCard(snapshot = analytics, onClick = { onNavigate(Destination.Insights) })
        }
        if (setupStatus != null && !setupStatus.isComplete) {
            item(key = SETUP_CHECKLIST_TAG) {
                SetupChecklistCard(
                    status = setupStatus,
                    hasProviders = summary.providers.isNotEmpty(),
                    onNavigate = onNavigate,
                    onMicRequested = onMicRequested,
                    onMicResult = onMicResult,
                )
            }
        }
        item {
            SetupEssentialsGroup(summary, onNavigate)
        }
        personalizationGroup(summary, onNavigate)
        behaviorGroup(summary, onNavigate)
        item {
            SettingsGroup(title = "Backup & restore") {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    val repairCount = backupStatus.repairCount
                    HomeOverviewCard(
                        icon = Icons.Default.Backup,
                        title = "Backup & restore",
                        status = backupStatus.summary.ifBlank {
                            if (repairCount > 0) "Repair attention needed" else "No backup yet"
                        },
                        guidance = if (repairCount > 0) {
                            "Review the last restore summary, skipped items, and repair checklist before relying on those settings."
                        } else {
                            "Create password-encrypted backups, preview imports, and restore safely without leaving settings."
                        },
                        modifier = Modifier.testTag("backup_restore_home_card"),
                        onClick = { onNavigate(Destination.BackupRestore) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupEssentialsGroup(
    summary: HomeViewModel.Summary,
    onNavigate: (Destination) -> Unit,
) {
    val providers = summary.providers
    val validation = summary.validation
    val totalModels = providers.sumOf { it.models.size }
    val sttLabel = resolveSelectionLabel(providers, validation.effective.stt.providerId, validation.effective.stt.modelId)
    val textLabel = resolveSelectionLabel(providers, validation.effective.text.providerId, validation.effective.text.modelId)

    SettingsGroup(title = "Setup essentials") {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeOverviewCard(
                icon = Icons.Default.SettingsSuggest,
                title = "Provider selections",
                status = "Dictation: $sttLabel • Enhancement: $textLabel",
                guidance = if (validation.isSttValid && validation.isTextValid) {
                    "Review defaults, command override, and language-specific fallbacks."
                } else {
                    "Choose valid STT and text models before relying on dictation or enhancement."
                },
                onClick = { onNavigate(Destination.ProviderSelections) },
            )
            HomeOverviewCard(
                icon = Icons.Default.Dns,
                title = "Providers & models",
                status = if (providers.isEmpty()) {
                    "No providers configured"
                } else {
                    "${providers.size} ${pluralize(providers.size, "provider", "providers")} • $totalModels ${pluralize(totalModels, "model", "models")}"
                },
                guidance = if (providers.isEmpty()) {
                    "Add your first provider, endpoint, and model list here."
                } else {
                    "Manage provider endpoints, credentials, and available model lists."
                },
                onClick = { onNavigate(Destination.ProviderList) },
            )
        }
    }
}

private fun LazyListScope.personalizationGroup(
    summary: HomeViewModel.Summary,
    onNavigate: (Destination) -> Unit,
) = item {
    val resources = LocalResources.current
    val enhancementPresetTitle = presetById(summary.enhancementPresetId)?.let { resources.getString(it.titleRes) }
        ?: stringResource(R.string.transform_preset_cleanup_title)
    val commandPresetTitle = presetById(summary.commandPresetId)?.let { resources.getString(it.titleRes) }
        ?: stringResource(R.string.transform_preset_tone_rewrite_title)

    SettingsGroup(title = "Personalization") {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeOverviewCard(
                icon = Icons.Default.TextFields,
                title = "Prompts, profiles & mappings",
                status = buildString {
                    append(if (summary.hasBasePrompt) "Base prompt saved" else "Base prompt off")
                    append(" • ${summary.promptProfileCount} ${pluralize(summary.promptProfileCount, "profile", "profiles")}")
                    append(" • ${summary.appMappingCount} ${pluralize(summary.appMappingCount, "mapping", "mappings")}")
                },
                guidance = if (summary.promptProfileCount == 0) {
                    "Create reusable prompt profiles, then map them to your most-used apps."
                } else {
                    "Adjust the base prompt or drill in to review per-app prompt behavior."
                },
                onClick = { onNavigate(Destination.Prompts) },
            )
            HomeOverviewCard(
                icon = Icons.Default.Translate,
                title = "Per-language defaults",
                status = if (summary.languageDefaultCount == 0) {
                    "No language-specific overrides"
                } else {
                    "${summary.languageDefaultCount} ${pluralize(summary.languageDefaultCount, "language", "languages")} configured"
                },
                guidance = "Use language rules when one global provider setup is not enough.",
                onClick = { onNavigate(Destination.LanguageDefaults) },
            )
            HomeOverviewCard(
                icon = Icons.Default.AutoFixHigh,
                title = "Transform presets",
                status = "Enhancement: $enhancementPresetTitle • Command: $commandPresetTitle",
                guidance = "Choose the defaults Whisper++ should use before dictation cleanup or command mode transforms.",
                onClick = { onNavigate(Destination.Presets) },
            )
        }
    }
}

private fun LazyListScope.behaviorGroup(
    summary: HomeViewModel.Summary,
    onNavigate: (Destination) -> Unit,
) = item {
    val prefs = summary.prefs
    val blockedAppCount = remember(prefs[PER_APP_SEND_POLICY_JSON]) { countBlockedApps(prefs[PER_APP_SEND_POLICY_JSON]) }

    SettingsGroup(title = "Behavior & safety") {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeOverviewCard(
                icon = Icons.Default.Keyboard,
                title = "Keyboard behavior",
                status = "Auto start ${onOff(prefs[AUTO_RECORDING_START] ?: true)} • Haptics ${onOff(prefs[HAPTIC_FEEDBACK_ENABLED] ?: true)} • Switch back ${onOff(prefs[AUTO_SWITCH_BACK] ?: false)}",
                guidance = "Tune recording automation, typing behavior, and IME feedback from one place.",
                onClick = { onNavigate(Destination.DictationSettings) },
            )
            HomeOverviewCard(
                icon = Icons.Default.Security,
                title = "Privacy & safety",
                status = "$blockedAppCount ${pluralize(blockedAppCount, "blocked app", "blocked apps")} • Verbose logs ${onOff(prefs[VERBOSE_NETWORK_LOGS_ENABLED] ?: false)} • Sound ${onOff(prefs[SOUND_EFFECTS_ENABLED] ?: true)}",
                guidance = "Review disclosures, blocked apps, and diagnostics before sending text or audio externally.",
                onClick = { onNavigate(Destination.PrivacySafety) },
            )
        }
    }
}

private fun buildSetupIssues(
    summary: HomeViewModel.Summary,
    repairCount: Int,
    onNavigate: (Destination) -> Unit,
): List<HomeSetupIssue> = buildList {
    // Missing provider / dictation model is covered by the setup checklist.
    val validation = summary.validation
    if (summary.providers.isNotEmpty() && !validation.isTextValid) {
        add(HomeSetupIssue("Choose a valid enhancement provider and model.", "Fix enhancement") { onNavigate(Destination.ProviderSelections) })
    }
    val commandOverridePresent = summary.prefs[COMMAND_TEXT_PROVIDER_ID].orEmpty().isNotBlank() ||
        summary.prefs[COMMAND_TEXT_MODEL_ID].orEmpty().isNotBlank()
    if (commandOverridePresent && !validation.isCommandOverrideValid) {
        add(HomeSetupIssue("Command override is stale. Clear it or choose a valid model.", "Review override") { onNavigate(Destination.ProviderSelections) })
    }
    if (repairCount > 0) {
        add(
            HomeSetupIssue(
                "Last restore still needs $repairCount ${pluralize(repairCount, "repair step", "repair steps")}.",
                "Review restore",
            ) { onNavigate(Destination.BackupRestore) },
        )
    }
}

private fun resolveSelectionLabel(providers: List<ServiceProvider>, providerId: String, modelId: String): String {
    val provider = providers.firstOrNull { it.id == providerId }
    val model = provider?.models?.firstOrNull { it.id == modelId }
    return if (provider == null || model == null) "Needs setup" else "${provider.name} / ${model.name}"
}

private fun countBlockedApps(rawJson: String?): Int {
    val json = rawJson?.trim().orEmpty()
    if (json.isBlank()) return 0
    return try {
        val root = JSONObject(json)
        root.keys().asSequence().count { key -> root.optBoolean(key, false) }
    } catch (_: Exception) {
        0
    }
}

private fun pluralize(count: Int, singular: String, plural: String): String = if (count == 1) singular else plural

private fun onOff(enabled: Boolean): String = if (enabled) "on" else "off"
