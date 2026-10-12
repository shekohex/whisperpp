package com.github.shekohex.whisperpp.ui.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.providers.ProviderSelectionsViewModel.Slot
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar
import com.github.shekohex.whisperpp.ui.settings.common.ProviderModelDropdowns
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel

@Composable
fun ProviderSelectionsScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    viewModel: ProviderSelectionsViewModel = containerViewModel { container, _ -> ProviderSelectionsViewModel(container) },
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()

    fun ids(slot: Slot) = prefs?.get(slot.providerKey).orEmpty() to prefs?.get(slot.modelKey).orEmpty()

    Scaffold(
        topBar = {
            DetailTopBar(title = "Provider selections", onBack = onBack, helpTopic = SettingsHelpTopic.PROVIDER_SELECTIONS)
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Selections save immediately. Choose a provider first, then choose one of its compatible models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsGroup(title = "Defaults") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ListItem(
                        headlineContent = { Text("Per-language defaults") },
                        supportingContent = { Text("Choose STT and text defaults per language") },
                        leadingContent = { Icon(Icons.Default.Translate, null) },
                        modifier = Modifier.clickable { onNavigate(Destination.LanguageDefaults) },
                    )
                }
            }

            val (sttProviderId, sttModelId) = ids(Slot.STT)
            SettingsGroup(title = "Dictation (STT)") {
                SelectionPickers(
                    providers = providers,
                    providerId = sttProviderId,
                    modelId = sttModelId,
                    isCompatible = { it.capabilities.stt },
                    slot = Slot.STT,
                    viewModel = viewModel,
                )
            }

            val (textProviderId, textModelId) = ids(Slot.TEXT)
            SettingsGroup(title = "Enhancement (Text)") {
                SelectionPickers(
                    providers = providers,
                    providerId = textProviderId,
                    modelId = textModelId,
                    isCompatible = { it.capabilities.text },
                    slot = Slot.TEXT,
                    viewModel = viewModel,
                )
            }

            val (commandProviderId, commandModelId) = ids(Slot.COMMAND)
            SettingsGroup(title = "Advanced") {
                CommandOverridePickers(
                    providers = providers,
                    providerId = commandProviderId,
                    modelId = commandModelId,
                    viewModel = viewModel,
                )
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SelectionPickers(
    providers: List<ServiceProvider>,
    providerId: String,
    modelId: String,
    isCompatible: (ModelConfig) -> Boolean,
    slot: Slot,
    viewModel: ProviderSelectionsViewModel,
) {
    val provider = providers.find { it.id == providerId }
    val selectedModel = provider?.models?.firstOrNull { it.id == modelId }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProviderModelDropdowns(
            providers = providers,
            selectedProvider = provider,
            providerValue = provider?.name ?: "None",
            modelValue = when {
                provider == null -> "Select provider first"
                selectedModel != null -> selectedModel.name
                modelId.isNotBlank() -> modelId
                else -> "Select Model"
            },
            isCompatible = isCompatible,
            providerClearLabel = "None",
            onClearProvider = { viewModel.clear(slot) },
            onSelectProvider = { viewModel.selectProvider(slot, it) },
            onSelectModel = { viewModel.selectModel(slot, it.id) },
        )
    }
}

@Composable
private fun CommandOverridePickers(
    providers: List<ServiceProvider>,
    providerId: String,
    modelId: String,
    viewModel: ProviderSelectionsViewModel,
) {
    val inherit = "Inherit (Enhancement selection)"
    val provider = providers.find { it.id == providerId }
    val selectedModel = provider?.models?.firstOrNull { it.id == modelId }
    val isInheriting = providerId.isBlank() && modelId.isBlank()
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isInheriting) {
                "Command mode uses the enhancement selection by default."
            } else {
                "Command override is set. Clear it to inherit enhancement selection changes."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProviderModelDropdowns(
            providers = providers,
            selectedProvider = provider,
            providerValue = when {
                isInheriting -> inherit
                provider != null -> provider.name
                providerId.isNotBlank() -> providerId
                else -> inherit
            },
            modelValue = when {
                provider == null -> "Inherit"
                selectedModel != null -> selectedModel.name
                modelId.isNotBlank() -> modelId
                else -> "Select Model"
            },
            isCompatible = { it.capabilities.text },
            providerClearLabel = inherit,
            onClearProvider = { viewModel.clear(Slot.COMMAND) },
            onSelectProvider = { viewModel.selectProvider(Slot.COMMAND, it) },
            onSelectModel = { viewModel.selectModel(Slot.COMMAND, it.id) },
            providerLabel = "Command Provider",
            modelLabel = "Command Model",
        )
    }
}
