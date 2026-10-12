package com.github.shekohex.whisperpp.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.ui.settings.common.ProviderModelDropdowns
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup

/** Provider + model override where blank means "inherit"; used by app and language rules. */
@Composable
internal fun ProviderModelSelectionEditor(
    title: String,
    providers: List<ServiceProvider>,
    isCompatible: (ModelConfig) -> Boolean,
    providerId: String,
    modelId: String,
    onUpdate: (providerId: String, modelId: String) -> Unit,
) {
    val selectedProvider = providers.firstOrNull { it.id == providerId }
    val selectedModel = selectedProvider?.models?.firstOrNull { it.id == modelId }
    SettingsGroup(title = title) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProviderModelDropdowns(
                providers = providers,
                selectedProvider = selectedProvider,
                providerValue = selectedProvider?.name ?: providerId.takeIf { it.isNotBlank() } ?: "Inherit",
                modelValue = when {
                    selectedProvider == null -> modelId.takeIf { it.isNotBlank() } ?: "Inherit"
                    selectedModel != null -> selectedModel.name
                    modelId.isNotBlank() -> modelId
                    else -> "Inherit"
                },
                isCompatible = isCompatible,
                providerClearLabel = "Inherit",
                onClearProvider = { onUpdate("", "") },
                onSelectProvider = { onUpdate(it.id, "") },
                onSelectModel = { onUpdate(providerId, it.id) },
                modelClearLabel = "Inherit",
                onClearModel = { onUpdate(providerId, "") },
                emptyProvidersLabel = "No providers configured",
            )
            if (providerId.isNotBlank() || modelId.isNotBlank()) {
                OutlinedButton(onClick = { onUpdate("", "") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear")
                }
            }
        }
    }
}
