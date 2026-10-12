package com.github.shekohex.whisperpp.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.ui.components.SectionCard
import com.github.shekohex.whisperpp.ui.settings.common.ProviderModelDropdowns

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
    val inherit = stringResource(R.string.provider_inherit)
    SectionCard(title = title) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProviderModelDropdowns(
                providers = providers,
                selectedProvider = selectedProvider,
                providerValue = selectedProvider?.name ?: providerId.takeIf { it.isNotBlank() } ?: inherit,
                modelValue = when {
                    selectedProvider == null -> modelId.takeIf { it.isNotBlank() } ?: inherit
                    selectedModel != null -> selectedModel.name
                    modelId.isNotBlank() -> modelId
                    else -> inherit
                },
                isCompatible = isCompatible,
                providerClearLabel = inherit,
                onClearProvider = { onUpdate("", "") },
                onSelectProvider = { onUpdate(it.id, "") },
                onSelectModel = { onUpdate(providerId, it.id) },
                modelClearLabel = inherit,
                onClearModel = { onUpdate(providerId, "") },
                emptyProvidersLabel = stringResource(R.string.provider_no_providers),
                providerLabel = stringResource(R.string.provider_label),
                modelLabel = stringResource(R.string.provider_model_label),
            )
            if (providerId.isNotBlank() || modelId.isNotBlank()) {
                OutlinedButton(onClick = { onUpdate("", "") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.apps_clear))
                }
            }
        }
    }
}
