package com.github.shekohex.whisperpp.ui.providers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ThinkingType
import com.github.shekohex.whisperpp.ui.providers.ProviderEditViewModel.KeyStatus
import com.github.shekohex.whisperpp.ui.providers.ProviderEditViewModel.TestOptions
import com.github.shekohex.whisperpp.ui.settings.common.COMMON_LANGUAGES
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
    OutlinedTextField(
        value = draft.name,
        onValueChange = onNameChange,
        label = { Text("Provider Name") },
        modifier = Modifier.fillMaxWidth()
    )
    SettingsDropdown(
        label = "Type",
        value = draft.type.toString(),
        options = if (typeEditable) ProviderType.entries else emptyList(),
        optionLabel = { it.toString() },
        onSelect = onTypeChange,
        enabled = typeEditable,
    )
    SettingsDropdown(
        label = "Language (for Transcription)",
        value = languageLabel(draft.languageCode),
        options = COMMON_LANGUAGES,
        optionLabel = { it.second },
        onSelect = { onLanguageChange(it.first) },
    )
    OutlinedTextField(
        value = draft.baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text("Base URL") },
        isError = draft.baseUrl.isNotBlank() && !draft.isBaseUrlValid,
        modifier = Modifier.fillMaxWidth()
    )
    val hintStyle = MaterialTheme.typography.bodySmall
    when {
        draft.baseUrl.isBlank() -> Text("Base URL is required", style = hintStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        draft.isRemoteHttpBaseUrl -> Text(stringResource(R.string.provider_url_insecure_http_local_only), style = hintStyle, color = MaterialTheme.colorScheme.error)
        !draft.isBaseUrlValid -> Text("Use a valid https:// URL", style = hintStyle, color = MaterialTheme.colorScheme.error)
        draft.isLocalHttpBaseUrl -> Text(stringResource(R.string.provider_url_insecure_http_local_only), style = hintStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    duplicateName?.let {
        Text("Duplicate: $it already uses this type + base URL", style = hintStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
    Text("Authentication", style = MaterialTheme.typography.titleMedium)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = authMode == ProviderAuthMode.API_KEY,
            onClick = { onAuthModeChange(ProviderAuthMode.API_KEY) },
            label = { Text("API Key") }
        )
        FilterChip(
            selected = authMode == ProviderAuthMode.NO_AUTH,
            onClick = { onAuthModeChange(ProviderAuthMode.NO_AUTH) },
            label = { Text("No Auth") }
        )
    }
    if (authMode != ProviderAuthMode.API_KEY) return

    val reusesCloneKey = isCloning && keyStatus.hasCloneKey
    val keyStatusText = when {
        keyStatus.hasStoredKey -> keyStatus.storedLast4?.let { "API Key: Key set (ends with $it)" } ?: "API Key: Key set"
        reusesCloneKey -> keyStatus.cloneLast4?.let { "API Key: Will reuse key (ends with $it)" } ?: "API Key: Will reuse key"
        else -> "API Key: Not set"
    }
    Text(keyStatusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // TODO(WP-3.7): adopt shared components (SecureTextField).
    OutlinedTextField(
        value = apiKeyInput,
        onValueChange = onApiKeyChange,
        label = { Text(if (keyStatus.hasStoredKey || reusesCloneKey) "Replace API Key" else "Set API Key") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    if (keyStatus.hasStoredKey) {
        OutlinedButton(onClick = onClearStoredKey, modifier = Modifier.fillMaxWidth()) {
            Text("Clear Stored Key")
        }
    }
}

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
    Text("Test", style = MaterialTheme.typography.titleMedium)
    val mustUseCustomTextModel = models.firstOrNull { it.capabilities.text }?.id.isNullOrBlank()
    val mustUseCustomSttModel = models.firstOrNull { it.capabilities.stt }?.id.isNullOrBlank()
    val useCustomText = options.useCustomTextModel || mustUseCustomTextModel
    val useCustomStt = options.useCustomSttModel || mustUseCustomSttModel

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(enabled = !isRunning, onClick = onTestText, modifier = Modifier.weight(1f)) {
                Text("Test Text")
            }
            OutlinedButton(enabled = !isRunning, onClick = onTestStt, modifier = Modifier.weight(1f)) {
                Text("Test STT")
            }
        }
        OutlinedButton(enabled = !isRunning, onClick = onTestConnection, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.provider_test_connection))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = useCustomText,
                enabled = !mustUseCustomTextModel,
                onClick = { onOptionsChange { it.copy(useCustomTextModel = !it.useCustomTextModel) } },
                label = { Text("Test anyway") }
            )
            FilterChip(
                selected = useCustomStt,
                enabled = !mustUseCustomSttModel,
                onClick = { onOptionsChange { it.copy(useCustomSttModel = !it.useCustomSttModel) } },
                label = { Text("Force model") }
            )
            OutlinedButton(enabled = !isRunning, onClick = onPickAudio) {
                Text(if (options.sttAudioUri == null) "Pick audio" else "Change audio")
            }
        }
        if (useCustomText) {
            OutlinedTextField(
                value = options.customTextModelId,
                onValueChange = { value -> onOptionsChange { it.copy(customTextModelId = value) } },
                label = { Text("Text model id") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (useCustomStt) {
            OutlinedTextField(
                value = options.customSttModelId,
                onValueChange = { value -> onOptionsChange { it.copy(customSttModelId = value) } },
                label = { Text("STT model id") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (isRunning) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Running...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    OutlinedTextField(
        value = draft.timeout.toString(),
        onValueChange = onTimeoutChange,
        label = { Text("Timeout (ms)") },
        modifier = Modifier.fillMaxWidth()
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Temperature: ${"%.2f".format(draft.temperature)}", style = MaterialTheme.typography.bodyMedium)
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
        label = { Text("System Prompt / Context") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    HorizontalDivider()
    Text("Thinking Configuration", style = MaterialTheme.typography.titleMedium)
    SettingsToggle(
        icon = Icons.Default.Psychology,
        title = "Enable Thinking (Global Toggle)",
        checked = draft.thinkingEnabled,
        onCheckedChange = onThinkingEnabledChange,
    )
    AnimatedVisibility(visible = draft.thinkingEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Thinking Mode", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.thinkingType == ThinkingType.LEVEL,
                    onClick = { onThinkingTypeChange(ThinkingType.LEVEL) },
                    label = { Text("Level") }
                )
                FilterChip(
                    selected = draft.thinkingType == ThinkingType.BUDGET,
                    onClick = { onThinkingTypeChange(ThinkingType.BUDGET) },
                    label = { Text("Budget (Tokens)") }
                )
            }
            if (draft.thinkingType == ThinkingType.LEVEL) {
                SettingsDropdown(
                    label = "Thinking Level",
                    value = draft.thinkingLevel,
                    options = THINKING_LEVELS,
                    optionLabel = { it },
                    onSelect = onThinkingLevelChange,
                )
            } else {
                Column {
                    Text("Budget: ${draft.thinkingBudget} tokens", style = MaterialTheme.typography.bodyMedium)
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
