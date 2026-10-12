package com.github.shekohex.whisperpp.ui.providers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ThinkingType
import com.github.shekohex.whisperpp.ui.components.SecureTextField
import com.github.shekohex.whisperpp.ui.providers.ProviderEditViewModel.KeyStatus
import com.github.shekohex.whisperpp.ui.providers.ProviderEditViewModel.TestOptions
import com.github.shekohex.whisperpp.ui.settings.common.commonLanguages
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown
import com.github.shekohex.whisperpp.ui.settings.common.SettingsToggle
import com.github.shekohex.whisperpp.ui.settings.common.languageLabel

private val THINKING_LEVELS = listOf("minimal", "low", "medium", "high", "xhigh")

@Composable
internal fun ProviderBasicsSection(
    draft: ProviderDraft,
    typeEditable: Boolean,
    duplicateName: String?,
    onNameChange: (String) -> Unit,
    onTypeChange: (ProviderType) -> Unit,
    onLanguageChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
) {
    val typeLabels = ProviderType.entries.associateWith { stringResource(it.labelRes()) }
    OutlinedTextField(
        value = draft.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.provider_name_label)) },
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    SettingsDropdown(
        label = stringResource(R.string.provider_type_label),
        value = stringResource(draft.type.labelRes()),
        options = if (typeEditable) ProviderType.entries else emptyList(),
        optionLabel = { typeLabels.getValue(it) },
        onSelect = onTypeChange,
        enabled = typeEditable,
    )
    SettingsDropdown(
        label = stringResource(R.string.provider_language_label),
        value = languageLabel(draft.languageCode),
        options = commonLanguages(),
        optionLabel = { it.second },
        onSelect = { onLanguageChange(it.first) },
    )
    OutlinedTextField(
        value = draft.baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text(stringResource(R.string.provider_base_url_label)) },
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        singleLine = true,
        isError = draft.baseUrl.isNotBlank() && !draft.isBaseUrlValid,
        modifier = Modifier.fillMaxWidth()
    )
    when {
        draft.baseUrl.isBlank() -> FieldHint(stringResource(R.string.provider_base_url_required))
        draft.isRemoteHttpBaseUrl -> FieldHint(stringResource(R.string.provider_url_insecure_http_local_only), isError = true)
        !draft.isBaseUrlValid -> FieldHint(stringResource(R.string.provider_base_url_invalid), isError = true)
        draft.isLocalHttpBaseUrl -> FieldHint(stringResource(R.string.provider_url_insecure_http_local_only))
    }
    duplicateName?.let { FieldHint(stringResource(R.string.provider_duplicate_warning, it)) }
}

@Composable
private fun FieldHint(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ProviderAuthSection(
    authMode: ProviderAuthMode,
    apiKeyInput: String,
    keyStatus: KeyStatus,
    isCloning: Boolean,
    onAuthModeChange: (ProviderAuthMode) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onClearStoredKey: () -> Unit,
) {
    SubsectionTitle(stringResource(R.string.provider_auth_title))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = authMode == ProviderAuthMode.API_KEY,
            onClick = { onAuthModeChange(ProviderAuthMode.API_KEY) },
            label = { Text(stringResource(R.string.provider_auth_api_key)) }
        )
        FilterChip(
            selected = authMode == ProviderAuthMode.NO_AUTH,
            onClick = { onAuthModeChange(ProviderAuthMode.NO_AUTH) },
            label = { Text(stringResource(R.string.provider_auth_none)) }
        )
    }
    if (authMode != ProviderAuthMode.API_KEY) return

    val reusesCloneKey = isCloning && keyStatus.hasCloneKey
    val keyStatusText = when {
        keyStatus.hasStoredKey -> keyStatus.storedLast4
            ?.let { stringResource(R.string.provider_key_status_set_last4, it) }
            ?: stringResource(R.string.provider_key_status_set)
        reusesCloneKey -> keyStatus.cloneLast4
            ?.let { stringResource(R.string.provider_key_status_reuse_last4, it) }
            ?: stringResource(R.string.provider_key_status_reuse)
        else -> stringResource(R.string.provider_key_status_not_set)
    }
    Text(keyStatusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SecureTextField(
        value = apiKeyInput,
        onValueChange = onApiKeyChange,
        label = stringResource(
            if (keyStatus.hasStoredKey || reusesCloneKey) R.string.provider_key_replace else R.string.provider_key_set,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    if (keyStatus.hasStoredKey) {
        OutlinedButton(onClick = onClearStoredKey, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.provider_key_clear))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderTestSection(
    models: List<ModelConfig>,
    options: TestOptions,
    isRunning: Boolean,
    onOptionsChange: ((TestOptions) -> TestOptions) -> Unit,
    onTestText: () -> Unit,
    onTestStt: () -> Unit,
    onTestConnection: () -> Unit,
    onPickAudio: () -> Unit,
) {
    SubsectionTitle(stringResource(R.string.provider_test_title))
    val mustUseCustomTextModel = models.firstOrNull { it.capabilities.text }?.id.isNullOrBlank()
    val mustUseCustomSttModel = models.firstOrNull { it.capabilities.stt }?.id.isNullOrBlank()
    val useCustomText = options.useCustomTextModel || mustUseCustomTextModel
    val useCustomStt = options.useCustomSttModel || mustUseCustomSttModel

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(enabled = !isRunning, onClick = onTestText, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.provider_test_text))
            }
            OutlinedButton(enabled = !isRunning, onClick = onTestStt, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.provider_test_stt))
            }
        }
        OutlinedButton(enabled = !isRunning, onClick = onTestConnection, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.provider_test_connection))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilterChip(
                selected = useCustomText,
                enabled = !mustUseCustomTextModel,
                onClick = { onOptionsChange { it.copy(useCustomTextModel = !it.useCustomTextModel) } },
                label = { Text(stringResource(R.string.provider_test_custom_text)) }
            )
            FilterChip(
                selected = useCustomStt,
                enabled = !mustUseCustomSttModel,
                onClick = { onOptionsChange { it.copy(useCustomSttModel = !it.useCustomSttModel) } },
                label = { Text(stringResource(R.string.provider_test_custom_stt)) }
            )
            OutlinedButton(enabled = !isRunning, onClick = onPickAudio) {
                Text(
                    stringResource(
                        if (options.sttAudioUri == null) R.string.provider_test_pick_audio else R.string.provider_test_change_audio,
                    ),
                )
            }
        }
        if (useCustomText) {
            OutlinedTextField(
                value = options.customTextModelId,
                onValueChange = { value -> onOptionsChange { it.copy(customTextModelId = value) } },
                label = { Text(stringResource(R.string.provider_test_text_model_id)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (useCustomStt) {
            OutlinedTextField(
                value = options.customSttModelId,
                onValueChange = { value -> onOptionsChange { it.copy(customSttModelId = value) } },
                label = { Text(stringResource(R.string.provider_test_stt_model_id)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (isRunning) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                FieldHint(stringResource(R.string.provider_test_running))
            }
        }
    }
}

@Composable
internal fun ProviderTuningSection(
    draft: ProviderDraft,
    onTimeoutChange: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onPromptChange: (String) -> Unit,
    onThinkingEnabledChange: (Boolean) -> Unit,
    onThinkingTypeChange: (ThinkingType) -> Unit,
    onThinkingLevelChange: (String) -> Unit,
    onThinkingBudgetChange: (Int) -> Unit,
) {
    val levelLabels = THINKING_LEVELS.associateWith { thinkingLevelLabel(it) }
    OutlinedTextField(
        value = draft.timeout.toString(),
        onValueChange = onTimeoutChange,
        label = { Text(stringResource(R.string.provider_timeout_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.provider_temperature, "%.2f".format(draft.temperature)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = draft.temperature,
            onValueChange = onTemperatureChange,
            valueRange = 0f..1f,
            steps = 20,
            modifier = Modifier.fillMaxWidth()
        )
    }
    OutlinedTextField(
        value = draft.prompt,
        onValueChange = onPromptChange,
        label = { Text(stringResource(R.string.provider_prompt_label)) },
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    HorizontalDivider()
    SubsectionTitle(stringResource(R.string.provider_thinking_title))
    SettingsToggle(
        icon = Icons.Default.Psychology,
        title = stringResource(R.string.provider_thinking_enable),
        checked = draft.thinkingEnabled,
        onCheckedChange = onThinkingEnabledChange,
    )
    AnimatedVisibility(visible = draft.thinkingEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.provider_thinking_mode), style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.thinkingType == ThinkingType.LEVEL,
                    onClick = { onThinkingTypeChange(ThinkingType.LEVEL) },
                    label = { Text(stringResource(R.string.provider_thinking_type_level)) }
                )
                FilterChip(
                    selected = draft.thinkingType == ThinkingType.BUDGET,
                    onClick = { onThinkingTypeChange(ThinkingType.BUDGET) },
                    label = { Text(stringResource(R.string.provider_thinking_type_budget)) }
                )
            }
            if (draft.thinkingType == ThinkingType.LEVEL) {
                SettingsDropdown(
                    label = stringResource(R.string.provider_thinking_level_label),
                    value = levelLabels[draft.thinkingLevel] ?: draft.thinkingLevel,
                    options = THINKING_LEVELS,
                    optionLabel = { levelLabels[it] ?: it },
                    onSelect = onThinkingLevelChange,
                )
            } else {
                Column {
                    Text(
                        pluralStringResource(
                            R.plurals.provider_thinking_budget_tokens,
                            draft.thinkingBudget,
                            draft.thinkingBudget,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = draft.thinkingBudget.toFloat(),
                        onValueChange = { onThinkingBudgetChange(it.toInt()) },
                        valueRange = 1024f..32768f,
                        steps = 31,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SubsectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}
