package com.github.shekohex.whisperpp.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.providers.ProviderSelectionsViewModel.Slot
import com.github.shekohex.whisperpp.ui.settings.common.ProviderModelDropdowns
import com.github.shekohex.whisperpp.ui.settings.common.SettingsGroup
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
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

    PageScaffold(
        title = stringResource(R.string.more_provider_selections_title),
        onBack = onBack,
        actions = { SettingsHelpAction(SettingsHelpTopic.PROVIDER_SELECTIONS) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.provider_selections_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val (sttProviderId, sttModelId) = ids(Slot.STT)
            SettingsGroup(title = stringResource(R.string.provider_selection_ref_dictation)) {
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
            SettingsGroup(title = stringResource(R.string.provider_selection_ref_enhancement)) {
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
            SettingsGroup(title = stringResource(R.string.provider_selections_advanced)) {
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
    val none = stringResource(R.string.provider_none)
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProviderModelDropdowns(
            providers = providers,
            selectedProvider = provider,
            providerValue = provider?.name ?: none,
            modelValue = when {
                provider == null -> stringResource(R.string.provider_select_provider_first)
                selectedModel != null -> selectedModel.name
                modelId.isNotBlank() -> modelId
                else -> stringResource(R.string.provider_select_model)
            },
            isCompatible = isCompatible,
            providerClearLabel = none,
            onClearProvider = { viewModel.clear(slot) },
            onSelectProvider = { viewModel.selectProvider(slot, it) },
            onSelectModel = { viewModel.selectModel(slot, it.id) },
            providerLabel = stringResource(R.string.provider_label),
            modelLabel = stringResource(R.string.provider_model_label),
            emptyProvidersLabel = stringResource(R.string.provider_no_providers),
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
    val inherit = stringResource(R.string.provider_inherit_enhancement)
    val provider = providers.find { it.id == providerId }
    val selectedModel = provider?.models?.firstOrNull { it.id == modelId }
    val isInheriting = providerId.isBlank() && modelId.isBlank()
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(
                if (isInheriting) R.string.provider_command_inherit_info else R.string.provider_command_override_info,
            ),
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
                provider == null -> stringResource(R.string.provider_inherit)
                selectedModel != null -> selectedModel.name
                modelId.isNotBlank() -> modelId
                else -> stringResource(R.string.provider_select_model)
            },
            isCompatible = { it.capabilities.text },
            providerClearLabel = inherit,
            onClearProvider = { viewModel.clear(Slot.COMMAND) },
            onSelectProvider = { viewModel.selectProvider(Slot.COMMAND, it) },
            onSelectModel = { viewModel.selectModel(Slot.COMMAND, it.id) },
            providerLabel = stringResource(R.string.provider_command_provider),
            modelLabel = stringResource(R.string.provider_command_model),
            emptyProvidersLabel = stringResource(R.string.provider_no_providers),
        )
    }
}
