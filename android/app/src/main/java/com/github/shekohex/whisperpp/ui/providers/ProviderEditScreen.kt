package com.github.shekohex.whisperpp.ui.providers

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.domain.providers.DiscoveryUnsupported
import com.github.shekohex.whisperpp.domain.providers.toProviderUserMessage
import com.github.shekohex.whisperpp.ui.components.PageScaffold
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpAction
import com.github.shekohex.whisperpp.ui.settings.common.SettingsHelpTopic
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import kotlinx.coroutines.flow.first

@Composable
fun ProviderEditScreen(
    providerId: String?,
    cloneFromId: String?,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = containerViewModel { container, handle ->
        ProviderEditViewModel(container, handle, appContext, providerId, cloneFromId)
    }
    val providerKey = viewModel.effectiveProviderId
    val discoveryViewModel = containerViewModel(key = "model-discovery-$providerKey") { container, _ ->
        val repository = container.settingsRepository
        ModelDiscoveryViewModel(
            loadCache = { repository.discoveredModels(providerKey).first() },
            saveCache = { repository.saveDiscovered(providerKey, it.models, it.fetchedAtMillis, it.identity) },
            errorMessage = { discoveryErrorMessage(appContext, it) },
        )
    }

    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val apiKeyInput by viewModel.apiKeyInput.collectAsStateWithLifecycle()
    val keyStatus by viewModel.keyStatus.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val discoveryCache by viewModel.discoveryCache.collectAsStateWithLifecycle()
    val testOptions by viewModel.testOptions.collectAsStateWithLifecycle()
    val isTestRunning by viewModel.isTestRunning.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val lastAddedModelCount by viewModel.lastAddedModelCount.collectAsStateWithLifecycle()

    var showDiscoverySheet by rememberSaveable { mutableStateOf(false) }
    var pendingSttTestAfterPick by rememberSaveable { mutableStateOf(false) }
    var blockedModel by remember { mutableStateOf<Pair<ModelConfig, List<Int>>?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (pendingSttTestAfterPick) {
            pendingSttTestAfterPick = false
            viewModel.testStt(uri)
        } else {
            viewModel.updateTestOptions { it.copy(sttAudioUri = uri) }
        }
    }

    val duplicateProvider = remember(providers, draft.type, draft.baseUrl) {
        val normalized = ProviderDraft.normalizedBaseUrl(draft.baseUrl)
        normalized?.let {
            providers.firstOrNull { provider ->
                provider.id != viewModel.existingProviderId && provider.type == draft.type &&
                    ProviderDraft.normalizedBaseUrl(provider.endpoint) == it
            }
        }
    }
    val discoveryAvailable = remember(draft.type, draft.baseUrl) {
        viewModel.isDiscoveryAvailable(draft.type, draft.baseUrl)
    }

    val title = when {
        viewModel.isEditingExisting -> stringResource(R.string.provider_edit_title_edit)
        viewModel.isCloning -> stringResource(R.string.provider_edit_title_duplicate)
        else -> stringResource(R.string.provider_edit_title_new)
    }
    PageScaffold(
        title = title,
        onBack = onBack,
        actions = {
            SettingsHelpAction(SettingsHelpTopic.PROVIDERS)
            viewModel.existingProviderId?.let { id ->
                TextButton(onClick = { onNavigate(Destination.ProviderEdit(id = null, cloneFromId = id)) }) {
                    Text(stringResource(R.string.provider_duplicate))
                }
            }
            TextButton(
                enabled = viewModel.canSave(draft, apiKeyInput, keyStatus),
                onClick = { viewModel.save(onSaved = onBack) },
            ) {
                Text(stringResource(R.string.provider_save))
            }
        },
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
                text = stringResource(R.string.provider_edit_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProviderBasicsSection(
                draft = draft,
                typeEditable = !viewModel.isEditingExisting,
                duplicateName = duplicateProvider?.name,
                onNameChange = viewModel::setName,
                onTypeChange = viewModel::setType,
                onLanguageChange = viewModel::setLanguage,
                onBaseUrlChange = viewModel::setBaseUrl,
            )
            ProviderAuthSection(
                authMode = draft.authMode,
                apiKeyInput = apiKeyInput,
                keyStatus = keyStatus,
                isCloning = viewModel.isCloning,
                onAuthModeChange = viewModel::setAuthMode,
                onApiKeyChange = viewModel::setApiKeyInput,
                onClearStoredKey = viewModel::clearStoredKey,
            )
            if (viewModel.isEditingExisting) {
                HorizontalDivider()
                ProviderTestSection(
                    models = draft.models,
                    options = testOptions,
                    isRunning = isTestRunning,
                    onOptionsChange = viewModel::updateTestOptions,
                    onTestText = viewModel::testText,
                    onTestStt = {
                        val audio = testOptions.sttAudioUri
                        if (audio == null) {
                            pendingSttTestAfterPick = true
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        } else {
                            viewModel.testStt(audio)
                        }
                    },
                    onTestConnection = viewModel::testConnection,
                    onPickAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                )
            }
            ProviderTuningSection(
                draft = draft,
                onTimeoutChange = viewModel::setTimeout,
                onTemperatureChange = viewModel::setTemperature,
                onPromptChange = viewModel::setPrompt,
                onThinkingEnabledChange = viewModel::setThinkingEnabled,
                onThinkingTypeChange = viewModel::setThinkingType,
                onThinkingLevelChange = viewModel::setThinkingLevel,
                onThinkingBudgetChange = viewModel::setThinkingBudget,
            )
            HorizontalDivider()
            ProviderModelsSection(
                models = draft.models,
                isModelMissing = { model ->
                    discoveryCache?.let { cache -> model.id.isNotBlank() && !cache.contains(model.id) } == true
                },
                discoveryEnabled = discoveryAvailable && draft.isBaseUrlValid,
                discoveryAvailable = discoveryAvailable,
                lastAddedModelCount = lastAddedModelCount,
                onUpdateModel = viewModel::updateModel,
                onDeleteModel = { index, model ->
                    viewModel.removeModel(index) { refs -> blockedModel = model to refs }
                },
                onAddModel = viewModel::addModel,
                onOpenDiscovery = {
                    discoveryViewModel.open(viewModel.discovery())
                    showDiscoverySheet = true
                },
            )
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showDiscoverySheet) {
        ModelDiscoverySheet(
            viewModel = discoveryViewModel,
            existingIds = draft.models.mapTo(HashSet()) { it.id.trim() },
            onAdd = {
                viewModel.addDiscoveredModels(it)
                showDiscoverySheet = false
            },
            onDismiss = { showDiscoverySheet = false },
        )
    }

    testResult?.let { result ->
        TestResultDialog(result = result, onDismiss = viewModel::dismissTestResult)
    }

    blockedModel?.let { (model, refs) ->
        BlockedModelDeleteDialog(
            modelName = model.name,
            refs = refs,
            onDismiss = { blockedModel = null },
            onGoToSelections = {
                blockedModel = null
                onNavigate(Destination.ProviderSelections)
            },
        )
    }
}

private fun discoveryErrorMessage(context: Context, error: Throwable): String = when (error) {
    is DiscoveryUnsupported -> context.getString(R.string.model_discovery_unavailable)
    else -> error.toProviderUserMessage(context)
}
