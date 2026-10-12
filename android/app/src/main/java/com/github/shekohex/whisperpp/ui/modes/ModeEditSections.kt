package com.github.shekohex.whisperpp.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.OutputMode
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.components.SettingsRow
import com.github.shekohex.whisperpp.ui.components.SettingsSwitchRow
import com.github.shekohex.whisperpp.ui.modes.ModeEditViewModel.ValidationError
import com.github.shekohex.whisperpp.ui.settings.common.COMMON_LANGUAGES
import com.github.shekohex.whisperpp.ui.settings.common.ProviderModelDropdowns
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown
import com.github.shekohex.whisperpp.ui.settings.common.languageLabel
import com.github.shekohex.whisperpp.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModeIdentitySection(
    draft: Mode,
    errors: Set<ValidationError>,
    onUpdate: ((Mode) -> Mode) -> Unit,
) {
    val nameError = when {
        ValidationError.NAME_BLANK in errors -> stringResource(R.string.modes_error_name_blank)
        ValidationError.NAME_TOO_LONG in errors -> stringResource(R.string.modes_error_name_long, ModeEditViewModel.MAX_NAME_LENGTH)
        ValidationError.NAME_DUPLICATE in errors -> stringResource(R.string.modes_error_name_duplicate)
        else -> null
    }
    SectionCard(title = stringResource(R.string.modes_edit_identity)) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { name -> onUpdate { it.copy(name = name) } },
                label = { Text(stringResource(R.string.modes_edit_name)) },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.modes_edit_icon), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Mode.Icon.entries.forEach { icon ->
                    FilledTonalIconToggleButton(
                        checked = draft.icon == icon,
                        onCheckedChange = { onUpdate { it.copy(icon = icon) } },
                    ) {
                        Icon(icon.imageVector, contentDescription = stringResource(icon.labelRes))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModeTranscriptionSection(
    draft: Mode,
    providers: List<ServiceProvider>,
    errors: Set<ValidationError>,
    onUpdate: ((Mode) -> Mode) -> Unit,
) {
    SectionCard(title = stringResource(R.string.modes_edit_transcription)) {
        ModeSelectionDropdowns(
            selection = draft.sttSelection,
            providers = providers,
            isCompatible = { it.capabilities.stt },
            error = if (ValidationError.STT_MODEL_MISSING in errors) stringResource(R.string.modes_error_model_missing) else null,
            onChange = { selection -> onUpdate { it.copy(sttSelection = selection) } },
        )
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            SettingsDropdown(
                label = stringResource(R.string.modes_edit_language),
                value = draft.language?.let(::languageLabel) ?: stringResource(R.string.modes_edit_language_inherit),
                options = COMMON_LANGUAGES,
                optionLabel = { it.second },
                onSelect = { (code, _) -> onUpdate { it.copy(language = code) } },
                clearLabel = stringResource(R.string.modes_edit_language_inherit),
                onClear = { onUpdate { it.copy(language = null) } },
            )
        }
    }
}

@Composable
internal fun ModeEnhancementSection(
    draft: Mode,
    providers: List<ServiceProvider>,
    profiles: List<PromptProfile>,
    errors: Set<ValidationError>,
    onUpdate: ((Mode) -> Mode) -> Unit,
) {
    SectionCard(title = stringResource(R.string.modes_edit_enhancement)) {
        TriStateSelector(
            title = stringResource(R.string.modes_edit_enhancement_enabled),
            value = draft.enhancementEnabled,
            onValueChange = { value -> onUpdate { it.copy(enhancementEnabled = value) } },
        )
        if (draft.enhancementEnabled != false) {
            ModeSelectionDropdowns(
                selection = draft.enhancementSelection,
                providers = providers,
                isCompatible = { it.capabilities.text },
                error = if (ValidationError.ENHANCEMENT_MODEL_MISSING in errors) {
                    stringResource(R.string.modes_error_model_missing)
                } else {
                    null
                },
                onChange = { selection -> onUpdate { it.copy(enhancementSelection = selection) } },
            )
            val missingPrompt = stringResource(R.string.modes_edit_prompt_missing)
            Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                SettingsDropdown(
                    label = stringResource(R.string.modes_edit_prompt),
                    value = draft.promptId?.let { id -> profiles.firstOrNull { it.id == id }?.name ?: missingPrompt }
                        ?: stringResource(R.string.modes_edit_prompt_none),
                    options = profiles,
                    optionLabel = { it.name },
                    onSelect = { profile -> onUpdate { it.copy(promptId = profile.id) } },
                    clearLabel = stringResource(R.string.modes_edit_prompt_none),
                    onClear = { onUpdate { it.copy(promptId = null) } },
                )
            }
        }
    }
}

/** Provider + model where no provider means "inherit the global selection". */
@Composable
private fun ModeSelectionDropdowns(
    selection: Mode.Selection?,
    providers: List<ServiceProvider>,
    isCompatible: (ModelConfig) -> Boolean,
    error: String?,
    onChange: (Mode.Selection?) -> Unit,
) {
    val providerId = selection?.providerId.orEmpty()
    val modelId = selection?.modelId.orEmpty()
    val provider = providers.firstOrNull { it.id == providerId }
    val model = provider?.models?.firstOrNull { it.id == modelId }
    val inherit = stringResource(R.string.modes_inherit_global)
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ProviderModelDropdowns(
            providers = providers,
            selectedProvider = provider,
            providerValue = provider?.name ?: providerId.ifBlank { inherit },
            modelValue = model?.name ?: modelId.ifBlank { if (provider == null) inherit else "" },
            isCompatible = isCompatible,
            providerClearLabel = inherit,
            onClearProvider = { onChange(null) },
            onSelectProvider = { onChange(Mode.Selection(providerId = it.id, modelId = "")) },
            onSelectModel = { onChange(Mode.Selection(providerId = providerId, modelId = it.id)) },
            providerLabel = stringResource(R.string.modes_edit_provider),
            modelLabel = stringResource(R.string.modes_edit_model),
            emptyProvidersLabel = stringResource(R.string.modes_edit_no_providers),
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun ModeBehaviourSection(draft: Mode, onUpdate: ((Mode) -> Mode) -> Unit) {
    SectionCard(title = stringResource(R.string.modes_edit_formatting)) {
        TriStateSelector(
            title = stringResource(R.string.modes_edit_filler_removal),
            value = draft.formatting.fillerRemoval,
            onValueChange = { value -> onUpdate { it.copy(formatting = it.formatting.copy(fillerRemoval = value)) } },
        )
        TriStateSelector(
            title = stringResource(R.string.modes_edit_paragraphs),
            value = draft.formatting.paragraphs,
            onValueChange = { value -> onUpdate { it.copy(formatting = it.formatting.copy(paragraphs = value)) } },
        )
        TriStateSelector(
            title = stringResource(R.string.modes_edit_auto_send),
            supporting = stringResource(R.string.modes_edit_auto_send_helper),
            value = draft.autoSend,
            onValueChange = { value -> onUpdate { it.copy(autoSend = value) } },
        )
    }
    SectionCard(title = stringResource(R.string.modes_edit_context)) {
        SettingsSwitchRow(
            title = stringResource(R.string.modes_edit_context_selected),
            supporting = stringResource(R.string.modes_edit_context_helper),
            checked = draft.contextSources.selectedText,
            onCheckedChange = { value -> onUpdate { it.copy(contextSources = it.contextSources.copy(selectedText = value)) } },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.modes_edit_context_clipboard),
            checked = draft.contextSources.clipboard,
            onCheckedChange = { value -> onUpdate { it.copy(contextSources = it.contextSources.copy(clipboard = value)) } },
        )
    }
    ModeOutputSection(draft = draft, onUpdate = onUpdate)
}

@Composable
private fun ModeOutputSection(draft: Mode, onUpdate: ((Mode) -> Mode) -> Unit) {
    SectionCard(title = stringResource(R.string.modes_edit_output)) {
        Column(modifier = Modifier.selectableGroup()) {
            OutputMode.entries.forEach { output ->
                val (title, helper) = when (output) {
                    OutputMode.INSERT -> R.string.modes_output_insert to R.string.modes_output_insert_helper
                    OutputMode.REPLACE_SELECTION -> R.string.modes_output_replace to R.string.modes_output_replace_helper
                    OutputMode.CLIPBOARD -> R.string.modes_output_clipboard to R.string.modes_output_clipboard_helper
                }
                SettingsRow(
                    title = stringResource(title),
                    supporting = stringResource(helper),
                    modifier = Modifier.selectable(
                        selected = draft.outputMode == output,
                        role = Role.RadioButton,
                        onClick = { onUpdate { it.copy(outputMode = output) } },
                    ),
                    trailing = { RadioButton(selected = draft.outputMode == output, onClick = null) },
                )
            }
        }
    }
}
