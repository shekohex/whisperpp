package com.github.shekohex.whisperpp.ui.dictionary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnSettings
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewSchedule
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.SectionHeader
import com.github.shekohex.whisperpp.ui.components.SettingsSwitchRow
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.settings.common.ProviderModelDropdowns
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.Spacing

private const val EXPORT_FILE_NAME = "whisperpp-dictionary.json"
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@Composable
fun defaultDictionarySettingsViewModel(): DictionarySettingsViewModel {
    val resolver = LocalContext.current.applicationContext.contentResolver
    return containerViewModel { container, _ ->
        DictionarySettingsViewModel(
            settingsRepository = container.settingsRepository,
            dictionaryRepository = container.dictionaryRepository,
            coordinator = container.autoLearnCoordinator,
            contentResolver = resolver,
        )
    }
}

/** Gear sheet of the Dictionary screen (§4.11). "Review now" opens the review sheet, then calls [onReviewNow]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsSheet(
    onDismiss: () -> Unit,
    onReviewNow: () -> Unit,
    viewModel: DictionarySettingsViewModel = defaultDictionarySettingsViewModel(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        DictionarySettingsPane(onReviewNow = onReviewNow, viewModel = viewModel)
    }
}

/** Settings body shared by the sheet and the full page opened from Privacy & Safety. */
@Composable
fun DictionarySettingsPane(
    onReviewNow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DictionarySettingsViewModel = defaultDictionarySettingsViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
    val proposals by viewModel.proposalCount.collectAsStateWithLifecycle()
    val reviewing by viewModel.reviewing.collectAsStateWithLifecycle()
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            status = when (message) {
                DictionarySettingsViewModel.Message.Exported -> resources.getString(R.string.dictionary_export_done)
                DictionarySettingsViewModel.Message.ExportFailed -> resources.getString(R.string.dictionary_export_failed)
                DictionarySettingsViewModel.Message.ImportFailed -> resources.getString(R.string.dictionary_import_failed)
                is DictionarySettingsViewModel.Message.Imported ->
                    resources.getString(R.string.dictionary_import_done, message.rules, message.terms)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    DictionarySettingsContent(
        settings = settings ?: AutoLearnSettings(),
        providers = providers,
        pendingCount = pending,
        proposalCount = proposals,
        reviewing = reviewing,
        status = status,
        actions = DictionarySettingsActions(
            onEnabledChange = { viewModel.setEnabled(it) },
            onModelChange = { provider, model -> viewModel.setModel(provider, model) },
            onScheduleChange = { viewModel.setSchedule(it) },
            onReviewNow = {
                if (pending > 0) viewModel.reviewNow()
                AutoLearnReviewUiState.openSheet()
                onReviewNow()
            },
            onExport = { exportLauncher.launch(EXPORT_FILE_NAME) },
            onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
        ),
        modifier = modifier,
    )
}

/** Callbacks of [DictionarySettingsContent]. */
data class DictionarySettingsActions(
    val onEnabledChange: (Boolean) -> Unit,
    val onModelChange: (providerId: String?, modelId: String?) -> Unit,
    val onScheduleChange: (AutoLearnReviewSchedule) -> Unit,
    val onReviewNow: () -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
)

@Composable
fun DictionarySettingsContent(
    settings: AutoLearnSettings,
    providers: List<ServiceProvider>,
    pendingCount: Int,
    proposalCount: Int,
    reviewing: Boolean,
    status: String?,
    actions: DictionarySettingsActions,
    modifier: Modifier = Modifier,
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.xl),
    ) {
        SectionHeader(stringResource(R.string.autolearn_section_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsSwitchRow(
                title = stringResource(R.string.autolearn_toggle_title),
                supporting = stringResource(R.string.autolearn_toggle_summary),
                leadingIcon = Icons.Outlined.AutoFixHigh,
                checked = settings.enabled,
                onCheckedChange = actions.onEnabledChange,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.autolearn_info_title))
            }
        }
        if (settings.enabled) {
            AutoLearnOptions(settings, providers, pendingCount, proposalCount, reviewing, actions)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
        SectionHeader(stringResource(R.string.dictionary_backup_section_title))
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(onClick = actions.onExport) { Text(stringResource(R.string.dictionary_export)) }
            OutlinedButton(onClick = actions.onImport) { Text(stringResource(R.string.dictionary_import)) }
        }
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
    }
    if (showInfo) AutoLearnInfoDialog(onDismiss = { showInfo = false })
}

@Composable
private fun AutoLearnOptions(
    settings: AutoLearnSettings,
    providers: List<ServiceProvider>,
    pendingCount: Int,
    proposalCount: Int,
    reviewing: Boolean,
    actions: DictionarySettingsActions,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ReviewModelPickers(settings, providers, actions.onModelChange)
        Text(stringResource(R.string.autolearn_schedule_title), style = MaterialTheme.typography.titleSmall)
        ScheduleSelector(settings.reviewSchedule, actions.onScheduleChange)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.autolearn_to_review, pendingCount + proposalCount, pendingCount + proposalCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onReviewNow, enabled = !reviewing && pendingCount + proposalCount > 0) {
                Text(stringResource(if (reviewing) R.string.autolearn_reviewing else R.string.autolearn_review_now))
            }
        }
    }
}

@Composable
private fun ReviewModelPickers(
    settings: AutoLearnSettings,
    providers: List<ServiceProvider>,
    onModelChange: (providerId: String?, modelId: String?) -> Unit,
) {
    val inherit = stringResource(R.string.autolearn_model_inherit)
    // A provider picked without a model is not saved yet; the stored pair always has both.
    var draftProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    val providerId = draftProviderId ?: settings.providerId
    val provider = providers.find { it.id == providerId }
    val model = provider?.models?.firstOrNull { it.id == settings.modelId && settings.providerId == provider.id }
    ProviderModelDropdowns(
        providers = providers,
        selectedProvider = provider,
        providerValue = provider?.name ?: inherit,
        modelValue = model?.name ?: if (provider == null) inherit else stringResource(R.string.autolearn_model_select),
        isCompatible = { it.capabilities.text },
        providerClearLabel = inherit,
        onClearProvider = {
            draftProviderId = null
            onModelChange(null, null)
        },
        onSelectProvider = { draftProviderId = it.id },
        onSelectModel = {
            onModelChange(provider?.id, it.id)
            draftProviderId = null
        },
        providerLabel = stringResource(R.string.autolearn_provider_label),
        modelLabel = stringResource(R.string.autolearn_model_label),
        emptyProvidersLabel = stringResource(R.string.autolearn_no_providers),
    )
}

@Composable
private fun ScheduleSelector(selected: AutoLearnReviewSchedule, onSelect: (AutoLearnReviewSchedule) -> Unit) {
    val options = AutoLearnReviewSchedule.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, schedule ->
            SegmentedButton(
                selected = schedule == selected,
                onClick = { onSelect(schedule) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(schedule.labelRes()))
            }
        }
    }
}

private fun AutoLearnReviewSchedule.labelRes(): Int = when (this) {
    AutoLearnReviewSchedule.IMMEDIATELY -> R.string.autolearn_schedule_immediately
    AutoLearnReviewSchedule.DAILY -> R.string.autolearn_schedule_daily
    AutoLearnReviewSchedule.MANUALLY -> R.string.autolearn_schedule_manually
}

/** What Auto-Learn sends and when (§4.11 step 6). */
@Composable
fun AutoLearnInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autolearn_info_title)) },
        text = { Text(stringResource(R.string.autolearn_info_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dictionary_ok)) } },
    )
}

@ThemePreviews
@Composable
private fun DictionarySettingsContentPreview() {
    PreviewSurface {
        DictionarySettingsContent(
            settings = AutoLearnSettings(enabled = true, reviewSchedule = AutoLearnReviewSchedule.DAILY),
            providers = emptyList(),
            pendingCount = 2,
            proposalCount = 1,
            reviewing = false,
            status = null,
            actions = DictionarySettingsActions({}, { _, _ -> }, {}, {}, {}, {}),
        )
    }
}
