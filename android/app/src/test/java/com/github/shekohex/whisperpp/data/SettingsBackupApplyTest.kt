package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupApplyTest {

    private val gson = Gson()

    @Test
    fun applyImportAnalysis_mergesSelectedCategoriesAndOverwriteReplacesCategory() = runBlocking {
        val mergeDataStore = MutablePreferencesDataStore(
            seedPreferences(
                providers = listOf(
                    provider(id = "provider-shared", name = "Local Shared", modelId = "model-local"),
                    provider(id = "provider-local", name = "Local Only", modelId = "model-local-only"),
                ),
                promptProfiles = listOf(
                    PromptProfile(id = "profile-local", name = "Local Profile", promptAppend = "Local"),
                ),
            )
        )
        val mergeCredentialStore = FakeProviderCredentialStore()
        val mergeRepository = SettingsBackupRepository(
            dataStore = mergeDataStore,
            credentialSource = mergeCredentialStore,
            credentialSink = mergeCredentialStore,
            gson = gson,
        )
        val importEnvelope = envelopeForPayload(
            SettingsBackupPayload(
                providersModels = ProvidersModelsBackup(
                    providers = listOf(
                        provider(id = "provider-shared", name = "Imported Shared", modelId = "model-imported"),
                        provider(id = "provider-remote", name = "Imported Only", modelId = "model-remote"),
                    ),
                ),
                promptsProfiles = PromptsProfilesBackup(
                    globalBasePrompt = "Imported Base",
                    promptProfiles = listOf(
                        PromptProfile(id = "profile-imported", name = "Imported Profile", promptAppend = "Imported"),
                    ),
                ),
            )
        )

        val mergeAnalysis = mergeRepository.analyzeEncryptedBackup(importEnvelope, PASSWORD, RestoreMode.MERGE)
        val mergeSummary = mergeRepository.applyImportAnalysis(
            analysis = mergeAnalysis,
            includedCategoryIds = setOf(SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS),
        )

        val mergedProviders = decodeProviders(mergeDataStore.data.first()[stringPreferencesKey("providers_json")].orEmpty())
        val mergedPromptProfiles = decodePromptProfiles(mergeDataStore.data.first()[stringPreferencesKey("prompt_profiles_json")].orEmpty())
        assertEquals(3, mergedProviders.size)
        assertEquals("Imported Shared", mergedProviders.first { it.id == "provider-shared" }.name)
        assertEquals(listOf("profile-local"), mergedPromptProfiles.map { it.id })
        assertEquals(listOf(SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS), mergeSummary.appliedCategories)

        val overwriteDataStore = MutablePreferencesDataStore(
            seedPreferences(
                providers = listOf(
                    provider(id = "provider-shared", name = "Local Shared", modelId = "model-local"),
                    provider(id = "provider-local", name = "Local Only", modelId = "model-local-only"),
                ),
            )
        )
        val overwriteCredentialStore = FakeProviderCredentialStore()
        val overwriteRepository = SettingsBackupRepository(
            dataStore = overwriteDataStore,
            credentialSource = overwriteCredentialStore,
            credentialSink = overwriteCredentialStore,
            gson = gson,
        )

        val overwriteAnalysis = overwriteRepository.analyzeEncryptedBackup(importEnvelope, PASSWORD, RestoreMode.OVERWRITE)
        overwriteRepository.applyImportAnalysis(overwriteAnalysis)

        val overwrittenProviders = decodeProviders(overwriteDataStore.data.first()[stringPreferencesKey("providers_json")].orEmpty())
        assertEquals(listOf("provider-remote", "provider-shared"), overwrittenProviders.map { it.id }.sorted())
        assertFalse(overwrittenProviders.any { it.id == "provider-local" })
    }

    @Test
    fun applyImportAnalysis_restoresCredentialsOnlyViaCredentialStore() = runBlocking {
        val credentialStore = FakeProviderCredentialStore()
        val dataStore = MutablePreferencesDataStore(seedPreferences())
        val repository = SettingsBackupRepository(
            dataStore = dataStore,
            credentialSource = credentialStore,
            credentialSink = credentialStore,
            gson = gson,
        )

        val analysis = repository.analyzeEncryptedBackup(
            envelopeForPayload(
                SettingsBackupPayload(
                    providersModels = ProvidersModelsBackup(
                        providers = listOf(provider(id = "provider-openai", name = "OpenAI", modelId = "model-gpt")),
                    ),
                    providerCredentials = ProviderCredentialsBackup(
                        credentials = listOf(
                            ProviderCredentialBackupEntry(
                                providerId = "provider-openai",
                                providerName = "OpenAI",
                                apiKey = "live-secret-key",
                            )
                        ),
                    ),
                )
            ),
            PASSWORD,
            RestoreMode.OVERWRITE,
        )

        repository.applyImportAnalysis(analysis)

        val providersJson = dataStore.data.first()[stringPreferencesKey("providers_json")].orEmpty()
        assertEquals("live-secret-key", credentialStore.getProviderApiKey("provider-openai"))
        assertFalse(providersJson.contains("live-secret-key"))
        assertFalse(providersJson.contains("apiKey"))
    }

    @Test
    fun applyImportAnalysis_clearsInvalidImportedSelectionsAfterApply() = runBlocking {
        val credentialStore = FakeProviderCredentialStore()
        val dataStore = MutablePreferencesDataStore(seedPreferences())
        val repository = SettingsBackupRepository(
            dataStore = dataStore,
            credentialSource = credentialStore,
            credentialSink = credentialStore,
            gson = gson,
        )

        val analysis = repository.analyzeEncryptedBackup(
            envelopeForPayload(
                SettingsBackupPayload(
                    providersModels = ProvidersModelsBackup(
                        providers = listOf(provider(id = "provider-valid", name = "Valid Provider", modelId = "model-valid")),
                    ),
                    activeSelections = ActiveSelectionsBackup(
                        activeSttProviderId = "missing-provider",
                        activeSttModelId = "missing-model",
                    ),
                )
            ),
            PASSWORD,
            RestoreMode.OVERWRITE,
        )

        val summary = repository.applyImportAnalysis(analysis)
        val prefs = dataStore.data.first()

        assertNull(prefs[stringPreferencesKey("active-stt-provider-id")])
        assertNull(prefs[stringPreferencesKey("active-stt-model-id")])
        assertTrue(summary.clearedSelections.any { it.selectionType == RestoreSelectionType.ACTIVE_STT })
        assertTrue(summary.repairChecklist.any { it.area == RestoreRepairArea.ACTIVE_STT })
    }

    @Test
    fun applyImportAnalysis_missingCredentialsProduceRepairChecklistAndClearSelection() = runBlocking {
        val credentialStore = FakeProviderCredentialStore()
        val dataStore = MutablePreferencesDataStore(seedPreferences())
        val repository = SettingsBackupRepository(
            dataStore = dataStore,
            credentialSource = credentialStore,
            credentialSink = credentialStore,
            gson = gson,
        )

        val analysis = repository.analyzeEncryptedBackup(
            envelopeForPayload(
                SettingsBackupPayload(
                    providersModels = ProvidersModelsBackup(
                        providers = listOf(
                            provider(
                                id = "provider-openai",
                                name = "OpenAI",
                                modelId = "model-gpt",
                                authMode = ProviderAuthMode.API_KEY,
                            )
                        ),
                    ),
                    activeSelections = ActiveSelectionsBackup(
                        activeTextProviderId = "provider-openai",
                        activeTextModelId = "model-gpt",
                    ),
                )
            ),
            PASSWORD,
            RestoreMode.OVERWRITE,
        )

        val summary = repository.applyImportAnalysis(analysis)
        val prefs = dataStore.data.first()

        assertNull(prefs[stringPreferencesKey("active-text-provider-id")])
        assertNull(prefs[stringPreferencesKey("active-text-model-id")])
        assertTrue(summary.clearedSelections.any { it.selectionType == RestoreSelectionType.ACTIVE_TEXT })
        assertTrue(summary.repairChecklist.any { it.area == RestoreRepairArea.PROVIDER_CREDENTIALS && it.providerId == "provider-openai" })
        assertTrue(summary.repairChecklist.any { it.area == RestoreRepairArea.ACTIVE_TEXT && it.providerId == "provider-openai" })
    }

    private fun envelopeForPayload(payload: SettingsBackupPayload): SettingsBackupEnvelope {
        return SettingsBackupCrypto.encryptUtf8(
            plaintext = gson.toJson(payload),
            password = PASSWORD,
            appVersionName = "1.0.0",
            exportedAtUtc = "2026-03-09T18:00:00Z",
        )
    }

    private fun seedPreferences(
        providers: List<ServiceProvider> = emptyList(),
        promptProfiles: List<PromptProfile> = emptyList(),
    ): Preferences {
        return mutablePreferencesOf(
            stringPreferencesKey("providers_json") to gson.toJson(providers),
            stringPreferencesKey("prompt_profiles_json") to gson.toJson(promptProfiles),
        )
    }

    private fun decodeProviders(json: String): List<ServiceProvider> {
        val type = object : TypeToken<List<ServiceProvider>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun decodePromptProfiles(json: String): List<PromptProfile> {
        val type = object : TypeToken<List<PromptProfile>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun provider(
        id: String,
        name: String,
        modelId: String,
        authMode: ProviderAuthMode = ProviderAuthMode.NO_AUTH,
    ): ServiceProvider {
        return ServiceProvider(
            id = id,
            name = name,
            type = ProviderType.OPENAI,
            endpoint = "https://api.openai.com/v1",
            authMode = authMode,
            models = listOf(
                ModelConfig(
                    id = modelId,
                    name = modelId,
                    kind = ModelKind.TEXT,
                )
            ),
        )
    }

    private class MutablePreferencesDataStore(
        initialPreferences: Preferences,
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private class FakeProviderCredentialStore : ProviderCredentialSource, ProviderCredentialSink {
        private val credentials = linkedMapOf<String, String>()

        override fun getProviderApiKey(providerId: String): String? = credentials[providerId]

        override fun setProviderApiKey(providerId: String, apiKey: String) {
            credentials[providerId] = apiKey
        }

        override fun clearProviderApiKey(providerId: String) {
            credentials.remove(providerId)
        }
    }

    private companion object {
        const val PASSWORD = "backup-password"
    }
}
