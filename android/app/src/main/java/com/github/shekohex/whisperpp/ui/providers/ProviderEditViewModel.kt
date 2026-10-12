package com.github.shekohex.whisperpp.ui.providers

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.ThinkingType
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.DiscoveredCache
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.domain.providers.ModelDiscovery
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Provider editor state. The form ([draft]) survives process death via [SavedStateHandle]; the
 * typed API key, test results and test options are in-memory only.
 */
class ProviderEditViewModel(
    private val container: AppContainer,
    private val handle: SavedStateHandle,
    appContext: Context,
    providerId: String?,
    private val cloneFromId: String?,
) : ViewModel() {

    data class KeyStatus(
        val hasStoredKey: Boolean = false,
        val storedLast4: String? = null,
        val hasCloneKey: Boolean = false,
        val cloneLast4: String? = null,
    )

    data class TestOptions(
        val useCustomTextModel: Boolean = false,
        val customTextModelId: String = "",
        val useCustomSttModel: Boolean = false,
        val customSttModelId: String = "",
        val sttAudioUri: Uri? = null,
    )

    private val repository = container.settingsRepository
    private val secretsStore = container.secretsStore
    private val gson = Gson()
    private val tests = ProviderTestRunner(appContext, container.providerFactory, repository)

    val existingProviderId: String? = providerId?.trim()?.takeIf { it.isNotEmpty() }
    val isEditingExisting: Boolean = existingProviderId != null
    val isCloning: Boolean = !isEditingExisting && !cloneFromId.isNullOrBlank()

    /** Stable id for the provider being created; kept across process death. */
    val effectiveProviderId: String = existingProviderId
        ?: handle.get<String>(KEY_NEW_ID)
        ?: UUID.randomUUID().toString().also { handle[KEY_NEW_ID] = it }

    private val _draft = MutableStateFlow(
        handle.get<String>(KEY_DRAFT)?.let { runCatching { gson.fromJson(it, ProviderDraft::class.java) }.getOrNull() }
            ?: ProviderDraft.newOf(ProviderType.OPENAI)
    )
    val draft: StateFlow<ProviderDraft> = _draft.asStateFlow()

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _keyStatus = MutableStateFlow(KeyStatus())
    val keyStatus: StateFlow<KeyStatus> = _keyStatus.asStateFlow()

    private val _testOptions = MutableStateFlow(TestOptions())
    val testOptions: StateFlow<TestOptions> = _testOptions.asStateFlow()

    private val _isTestRunning = MutableStateFlow(false)
    val isTestRunning: StateFlow<Boolean> = _isTestRunning.asStateFlow()

    private val _testResult = MutableStateFlow<ProviderTestResult?>(null)
    val testResult: StateFlow<ProviderTestResult?> = _testResult.asStateFlow()

    private val _lastAddedModelCount = MutableStateFlow<Int?>(null)
    val lastAddedModelCount: StateFlow<Int?> = _lastAddedModelCount.asStateFlow()

    val providers: StateFlow<List<ServiceProvider>> = repository.providers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val discoveryCache: StateFlow<DiscoveredCache?> = repository.discoveredModels(effectiveProviderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch { loadInitialState() }
    }

    private suspend fun loadInitialState() {
        val sourceId = existingProviderId ?: cloneFromId?.trim()?.takeIf { it.isNotEmpty() }
        if (sourceId != null && handle.get<Boolean>(KEY_LOADED) != true) {
            repository.providers.first().find { it.id == sourceId }?.let { source ->
                updateDraft { ProviderDraft.from(source) }
                handle[KEY_LOADED] = true
            }
        }
        _keyStatus.value = when {
            existingProviderId != null -> secretsStore.getProviderApiKeyLast4(existingProviderId)
                .let { KeyStatus(hasStoredKey = it != null, storedLast4 = it) }
            isCloning && sourceId != null -> secretsStore.getProviderApiKeyLast4(sourceId)
                .let { KeyStatus(hasCloneKey = it != null, cloneLast4 = it) }
            else -> KeyStatus()
        }
    }

    private fun updateDraft(transform: (ProviderDraft) -> ProviderDraft) {
        val next = _draft.updateAndGet(transform)
        handle[KEY_DRAFT] = gson.toJson(next)
    }

    // Form fields.

    fun setName(value: String) = updateDraft { it.copy(name = value, nameTouched = true) }

    fun setType(type: ProviderType) {
        if (isEditingExisting) return
        updateDraft { draft ->
            val baseUrl = if (draft.baseUrlTouched) draft.baseUrl else ProviderDraft.defaultBaseUrlFor(type)
            draft.copy(
                type = type,
                baseUrl = baseUrl,
                authMode = if (draft.authModeTouched) draft.authMode else ProviderDraft.defaultAuthModeFor(type),
                name = if (draft.nameTouched) draft.name else ProviderDraft.defaultNameFor(type, baseUrl),
            )
        }
    }

    fun setBaseUrl(value: String) = updateDraft { draft ->
        draft.copy(
            baseUrl = value,
            baseUrlTouched = true,
            name = if (draft.nameTouched || isEditingExisting) draft.name else ProviderDraft.defaultNameFor(draft.type, value),
        )
    }

    fun setAuthMode(mode: ProviderAuthMode) {
        updateDraft { it.copy(authMode = mode, authModeTouched = true) }
        if (mode == ProviderAuthMode.NO_AUTH) _apiKeyInput.value = ""
    }

    fun setApiKeyInput(value: String) {
        _apiKeyInput.value = value
    }

    fun clearStoredKey() {
        val id = existingProviderId ?: return
        secretsStore.clearProviderApiKey(id)
        _apiKeyInput.value = ""
        _keyStatus.update { it.copy(hasStoredKey = false, storedLast4 = null) }
    }

    fun setLanguage(code: String) = updateDraft { it.copy(languageCode = code) }
    fun setTimeout(text: String) = updateDraft { it.copy(timeout = text.toIntOrNull() ?: ProviderDraft.DEFAULT_TIMEOUT_MS) }
    fun setTemperature(value: Float) = updateDraft { it.copy(temperature = value) }
    fun setPrompt(value: String) = updateDraft { it.copy(prompt = value) }
    fun setThinkingEnabled(value: Boolean) = updateDraft { it.copy(thinkingEnabled = value) }
    fun setThinkingType(value: ThinkingType) = updateDraft { it.copy(thinkingType = value) }
    fun setThinkingBudget(value: Int) = updateDraft { it.copy(thinkingBudget = value) }
    fun setThinkingLevel(value: String) = updateDraft { it.copy(thinkingLevel = value) }

    // Models.

    fun addModel() = updateDraft { it.copy(models = it.models + ModelConfig(UUID.randomUUID().toString(), "New Model")) }

    fun updateModel(index: Int, model: ModelConfig) = updateDraft { draft ->
        draft.copy(models = draft.models.toMutableList().also { if (index in it.indices) it[index] = model })
    }

    /** Removes the model unless an active selection uses it; then reports the blocking selections. */
    fun removeModel(index: Int, onBlocked: (List<String>) -> Unit) {
        val model = _draft.value.models.getOrNull(index) ?: return
        viewModelScope.launch {
            val prefs = container.settingsDataStore.data.first()
            val refs = selectionReferencesModel(effectiveProviderId, model.id, prefs)
            if (refs.isEmpty()) {
                updateDraft { draft -> draft.copy(models = draft.models.filterIndexed { i, _ -> i != index }) }
            } else {
                onBlocked(refs)
            }
        }
    }

    fun addDiscoveredModels(discovered: List<DiscoveredModel>) {
        val current = _draft.value.models
        val merged = ModelDiscoveryViewModel.merge(current, discovered)
        updateDraft { it.copy(models = merged) }
        if (isEditingExisting) {
            viewModelScope.launch { repository.upsertProvider(_draft.value.toProvider(effectiveProviderId, merged)) }
        }
        _lastAddedModelCount.value = merged.size - current.size
    }

    /** The discovery client for the current form, or `null` when the provider type has none. */
    fun discovery(): ModelDiscovery? = container.providerFactory.discovery(
        _draft.value.toProvider(effectiveProviderId),
        Credentials(resolveApiKeyForTests()),
    )

    fun isDiscoveryAvailable(type: ProviderType, baseUrl: String): Boolean = container.providerFactory.discovery(
        ServiceProvider(name = "", type = type, endpoint = baseUrl, models = emptyList()),
        Credentials.NONE,
    ) != null

    // Tests.

    fun updateTestOptions(transform: (TestOptions) -> TestOptions) = _testOptions.update(transform)

    fun dismissTestResult() {
        _testResult.value = null
    }

    fun testText() {
        val modelId = chosenTestModelId(ModelCapabilities::text, TestOptions::useCustomTextModel, TestOptions::customTextModelId)
        if (modelId.isBlank()) return reportMissingModel(ProviderTestRunner.TEXT_TEST_TITLE)
        runTest {
            val draft = _draft.value
            tests.text(
                provider = draft.toProvider(effectiveProviderId),
                model = ProviderTestRunner.testModel(draft.models, modelId, ModelCapabilities.TEXT),
                credentials = Credentials(resolveApiKeyForTests()),
            )
        }
    }

    fun testStt(audioUri: Uri) {
        _testOptions.update { it.copy(sttAudioUri = audioUri) }
        val modelId = chosenTestModelId(ModelCapabilities::stt, TestOptions::useCustomSttModel, TestOptions::customSttModelId)
        if (modelId.isBlank()) return reportMissingModel(ProviderTestRunner.STT_TEST_TITLE)
        runTest {
            val draft = _draft.value
            tests.stt(
                provider = draft.toProvider(effectiveProviderId),
                model = ProviderTestRunner.testModel(draft.models, modelId, ModelCapabilities.stt(draft.type)),
                credentials = Credentials(resolveApiKeyForTests()),
                audioUri = audioUri,
            )
        }
    }

    fun testConnection() = runTest {
        tests.connection(
            providerId = effectiveProviderId,
            provider = _draft.value.toProvider(effectiveProviderId),
            credentials = Credentials(resolveApiKeyForTests()),
        )
    }

    private fun chosenTestModelId(
        capability: (ModelCapabilities) -> Boolean,
        useCustom: (TestOptions) -> Boolean,
        customId: (TestOptions) -> String,
    ): String {
        val defaultId = _draft.value.models.firstOrNull { capability(it.capabilities) }?.id.orEmpty()
        val options = _testOptions.value
        return if (useCustom(options) || defaultId.isBlank()) customId(options).trim() else defaultId
    }

    private fun reportMissingModel(title: String) {
        _testResult.value = ProviderTestResult(title, null, "", "Model ID is required")
    }

    private fun runTest(block: suspend () -> ProviderTestResult) {
        if (_isTestRunning.value) return
        _isTestRunning.value = true
        viewModelScope.launch {
            try {
                _testResult.value = block()
            } finally {
                _isTestRunning.value = false
            }
        }
    }

    private fun resolveApiKeyForTests(): String {
        if (_draft.value.authMode != ProviderAuthMode.API_KEY) return ""
        val typed = _apiKeyInput.value.trim()
        if (typed.isNotEmpty()) return typed
        val id = existingProviderId ?: return ""
        return secretsStore.getProviderApiKey(id).orEmpty()
    }

    // Save.

    fun canSave(draft: ProviderDraft, apiKeyInput: String, keyStatus: KeyStatus): Boolean {
        val hasAnyApiKey = keyStatus.hasStoredKey || apiKeyInput.isNotBlank() || (isCloning && keyStatus.hasCloneKey)
        val isAuthValid = draft.authMode == ProviderAuthMode.NO_AUTH || hasAnyApiKey
        return draft.isNameValid && draft.isBaseUrlValid && isAuthValid
    }

    fun save(onSaved: () -> Unit) {
        val provider = _draft.value.toProvider(effectiveProviderId)
        val typedKey = _apiKeyInput.value.trim()
        viewModelScope.launch {
            repository.upsertProvider(provider)
            if (provider.authMode == ProviderAuthMode.API_KEY) {
                if (typedKey.isNotEmpty()) {
                    secretsStore.setProviderApiKey(provider.id, typedKey)
                } else if (isCloning && !cloneFromId.isNullOrBlank()) {
                    val sourceKey = secretsStore.getProviderApiKey(cloneFromId.trim()).orEmpty()
                    if (sourceKey.isNotBlank()) secretsStore.setProviderApiKey(provider.id, sourceKey)
                }
            }
            onSaved()
        }
    }

    private companion object {
        const val KEY_DRAFT = "provider_draft"
        const val KEY_LOADED = "provider_draft_loaded"
        const val KEY_NEW_ID = "provider_new_id"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
