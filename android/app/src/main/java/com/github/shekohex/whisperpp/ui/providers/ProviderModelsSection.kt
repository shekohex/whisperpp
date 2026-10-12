package com.github.shekohex.whisperpp.ui.providers

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ModelKind
import com.github.shekohex.whisperpp.ui.settings.common.SettingsDropdown

@Composable
internal fun ProviderModelsSection(
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
    Text("Models", style = MaterialTheme.typography.titleMedium)
    models.forEachIndexed { index, model ->
        ModelEditCard(
            model = model,
            notFound = isModelMissing(model),
            onUpdate = { onUpdateModel(index, it) },
            onDelete = { onDeleteModel(index, model) },
        )
    }
    Button(onClick = onAddModel, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.width(8.dp))
        Text("Add Model")
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
    onUpdate: (ModelConfig) -> Unit,
    onDelete: () -> Unit,
    notFound: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = model.name,
                    onValueChange = { onUpdate(model.copy(name = it)) },
                    label = { Text("Model Name") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
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
                label = { Text("Model ID (API)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            SettingsDropdown(
                label = "Kind",
                value = model.capabilities.legacyKind.name,
                options = ModelKind.entries,
                optionLabel = { it.name },
                onSelect = { onUpdate(model.copy(capabilities = model.capabilities.withKind(it))) },
            )
            if (model.capabilities.stt) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = model.capabilities.realtime,
                        onCheckedChange = { onUpdate(model.copy(capabilities = model.capabilities.copy(realtime = it))) }
                    )
                    Text("Streaming partials supported")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.capabilities.thinking,
                    onCheckedChange = { onUpdate(model.copy(capabilities = model.capabilities.copy(thinking = it))) }
                )
                Text("Thinking Model")
            }
        }
    }
}
