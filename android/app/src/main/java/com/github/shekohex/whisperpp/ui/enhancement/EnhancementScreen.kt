package com.github.shekohex.whisperpp.ui.enhancement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_BACKEND
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_MODEL
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.components.SettingsNavRow
import com.github.shekohex.whisperpp.ui.components.SettingsSwitchRow
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown
import com.github.shekohex.whisperpp.ui.settings.common.SettingsEditDialog
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel

/** Post-dictation cleanup (old "Post processing" screen) plus links to prompts and presets. */
@Composable
fun EnhancementScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    viewModel: EnhancementViewModel = containerViewModel { container, _ -> EnhancementViewModel(container) },
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()

    val selectedProviderId = prefs?.get(ENHANCEMENT_BACKEND) ?: ""
    val selectedModelId = prefs?.get(ENHANCEMENT_MODEL) ?: ""
    val selectedProvider = providers.find { it.id == selectedProviderId } ?: providers.firstOrNull()
    var showPromptDialog by rememberSaveable { mutableStateOf(false) }

    PageScaffold(
        title = stringResource(R.string.enhancement_title),
        onBack = onBack,
        actions = { SettingsHelpAction(SettingsHelpTopic.ENHANCEMENT) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.enhancement_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard(title = stringResource(R.string.enhancement_group_behavior)) {
                SettingsSwitchRow(
                    leadingIcon = Icons.Default.AutoFixHigh,
                    title = stringResource(R.string.settings_enhancement_enabled),
                    checked = prefs?.get(ENHANCEMENT_ENABLED) ?: false,
                    onCheckedChange = { viewModel.set(ENHANCEMENT_ENABLED, it) }
                )
                SettingsSwitchRow(
                    leadingIcon = Icons.Default.History,
                    title = stringResource(R.string.enhancement_use_context),
                    checked = prefs?.get(USE_CONTEXT) ?: false,
                    onCheckedChange = { viewModel.set(USE_CONTEXT, it) }
                )
                SettingsNavRow(
                    leadingIcon = Icons.Default.EditNote,
                    title = stringResource(R.string.enhancement_custom_prompt),
                    supporting = promptPreview(prefs?.get(ENHANCEMENT_PROMPT))
                        ?: stringResource(R.string.enhancement_prompt_default),
                    onClick = { showPromptDialog = true }
                )
            }

            SectionCard(title = stringResource(R.string.enhancement_group_model)) {
                val itemModifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                SettingsDropdown(
                    label = stringResource(R.string.provider_label),
                    value = selectedProvider?.name ?: stringResource(R.string.provider_select_provider),
                    options = providers,
                    optionLabel = { it.name },
                    onSelect = viewModel::selectProvider,
                    modifier = itemModifier,
                    emptyLabel = stringResource(R.string.provider_no_providers),
                )
                val currentModelName = selectedProvider?.models?.find { it.id == selectedModelId }?.name ?: selectedModelId
                SettingsDropdown(
                    label = stringResource(R.string.provider_model_label),
                    value = currentModelName.ifEmpty { stringResource(R.string.provider_select_model) },
                    options = selectedProvider?.models.orEmpty(),
                    optionLabel = { it.name },
                    onSelect = { viewModel.set(ENHANCEMENT_MODEL, it.id) },
                    modifier = itemModifier,
                    enabled = selectedProvider != null,
                    emptyLabel = stringResource(R.string.enhancement_no_models),
                )
            }

            SectionCard(title = stringResource(R.string.enhancement_group_prompts)) {
                SettingsNavRow(
                    leadingIcon = Icons.Default.TextFields,
                    title = stringResource(R.string.enhancement_prompts_link_title),
                    supporting = stringResource(R.string.enhancement_prompts_link_summary),
                    onClick = { onNavigate(Destination.Prompts) },
                )
                SettingsNavRow(
                    leadingIcon = Icons.Default.AutoFixHigh,
                    title = stringResource(R.string.settings_transform_presets_title),
                    supporting = stringResource(R.string.enhancement_presets_link_summary),
                    onClick = { onNavigate(Destination.Presets) },
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showPromptDialog) {
        SettingsEditDialog(
            title = stringResource(R.string.enhancement_custom_prompt_dialog),
            initialValue = prefs?.get(ENHANCEMENT_PROMPT) ?: "",
            onDismiss = { showPromptDialog = false },
            onConfirm = { newValue ->
                viewModel.set(ENHANCEMENT_PROMPT, newValue)
                showPromptDialog = false
            }
        )
    }
}

private const val PROMPT_PREVIEW_LENGTH = 40

/** First characters of a custom prompt for the row subtitle; `null` when no prompt is set. */
private fun promptPreview(prompt: String?): String? {
    val trimmed = prompt?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return if (trimmed.length > PROMPT_PREVIEW_LENGTH) trimmed.take(PROMPT_PREVIEW_LENGTH) + "…" else trimmed
}
