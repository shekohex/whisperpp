package com.github.shekohex.whisperpp.ui.providers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ModelKind
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.providers.ModelClassifier
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown

@Composable
internal fun ProviderModelsSection(
    providerType: ProviderType,
    models: List<ModelConfig>,
    isModelMissing: (ModelConfig) -> Boolean,
    discoveryEnabled: Boolean,
    discoveryAvailable: Boolean,
    lastAddedModelCount: Int?,
    onUpdateModel: (Int, ModelConfig) -> Unit,
    onDeleteModel: (Int, ModelConfig) -> Unit,
    onAddModel: () -> Unit,
    onOpenDiscovery: () -> Unit,
) {
    Text(
        stringResource(R.string.provider_models_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    models.forEachIndexed { index, model ->
        ModelEditCard(
            model = model,
            providerType = providerType,
            notFound = isModelMissing(model),
            onUpdate = { onUpdateModel(index, it) },
            onDelete = { onDeleteModel(index, model) },
        )
    }
    Button(onClick = onAddModel, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.provider_model_add))
    }
    OutlinedButton(enabled = discoveryEnabled, onClick = onOpenDiscovery, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.model_discovery_open))
    }
    if (!discoveryAvailable) {
        Text(
            stringResource(R.string.model_discovery_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    lastAddedModelCount?.let { count ->
        Text(
            pluralStringResource(R.plurals.model_discovery_added, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelEditCard(
    model: ModelConfig,
    providerType: ProviderType,
    onUpdate: (ModelConfig) -> Unit,
    onDelete: () -> Unit,
    notFound: Boolean,
) {
    val kindLabels = ModelKind.entries.associateWith { stringResource(it.labelRes()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = model.name,
                    onValueChange = { onUpdate(model.copy(name = it)) },
                    label = { Text(stringResource(R.string.provider_model_name)) },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        stringResource(R.string.provider_model_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (notFound) {
                Text(
                    stringResource(R.string.model_discovery_not_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model.id,
                onValueChange = { onUpdate(model.copy(id = it)) },
                label = { Text(stringResource(R.string.provider_model_id)) },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            var timeoutText by remember(model.id) { mutableStateOf(model.timeoutMs?.toString().orEmpty()) }
            val parsedTimeout = timeoutText.trim().toIntOrNull()
            val timeoutInvalid = timeoutText.isNotBlank() && (parsedTimeout == null || parsedTimeout <= 0)
            OutlinedTextField(
                value = timeoutText,
                onValueChange = { raw ->
                    timeoutText = raw
                    onUpdate(model.copy(timeoutMs = raw.trim().toIntOrNull()?.takeIf { it > 0 }))
                },
                label = { Text(stringResource(R.string.provider_model_timeout)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (timeoutInvalid) R.string.provider_model_timeout_invalid
                            else R.string.provider_model_timeout_hint
                        )
                    )
                },
                isError = timeoutInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            SettingsDropdown(
                label = stringResource(R.string.provider_model_kind),
                value = kindLabels.getValue(model.capabilities.legacyKind),
                options = ModelKind.entries,
                optionLabel = { kindLabels.getValue(it) },
                onSelect = { onUpdate(model.copy(capabilities = model.capabilities.withKind(it))) },
            )
            if (model.capabilities.stt && ModelClassifier.acceptsRealtimeFlag(providerType)) {
                val hint = if (providerType == ProviderType.OPENAI) {
                    R.string.provider_model_realtime_hint_openai
                } else {
                    R.string.provider_model_realtime_hint
                }
                CheckboxRow(
                    text = stringResource(R.string.provider_model_realtime),
                    supportingText = stringResource(hint),
                    checked = model.capabilities.realtime,
                    onCheckedChange = { onUpdate(model.copy(capabilities = model.capabilities.copy(realtime = it))) },
                )
            }
            CheckboxRow(
                text = stringResource(R.string.provider_model_thinking),
                checked = model.capabilities.thinking,
                onCheckedChange = { onUpdate(model.copy(capabilities = model.capabilities.copy(thinking = it))) },
            )
        }
    }
}

@Composable
private fun CheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
        Column {
            Text(text)
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
