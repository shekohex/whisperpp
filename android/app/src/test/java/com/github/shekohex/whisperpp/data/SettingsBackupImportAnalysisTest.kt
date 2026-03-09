package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupImportAnalysisTest {

    private val gson = Gson()

    @Test
    fun analyzeEncryptedBackup_buildsOverwriteAndMergePreviewWithoutMutatingState() = runBlocking {
        val dataStore = MutablePreferencesDataStore(
            seedPreferences(
                providers = listOf(
                    provider(id = "provider-shared", name = "Local Shared", modelId = "model-local"),
                    provider(id = "provider-local", name = "Local Only", modelId = "model-local-only"),
                ),
            )
        )
        val repository = SettingsBackupRepository(
            dataStore = dataStore,
            credentialSource = FakeProviderCredentialSource(),
            gson = gson,
            currentAppVersionNameProvider = { "1.0.0" },
        )

        val importEnvelope = envelopeForPayload(
            SettingsBackupPayload(
                providersModels = ProvidersModelsBackup(
                    providers = listOf(
                        provider(id = "provider-shared", name = "Imported Shared", modelId = "model-imported"),
                        provider(id = "provider-remote", name = "Imported Only", modelId = "model-remote"),
                    ),
                ),
            )
        )

        val mergeAnalysis = repository.analyzeEncryptedBackup(importEnvelope, PASSWORD, RestoreMode.MERGE)
        val overwriteAnalysis = repository.analyzeEncryptedBackup(importEnvelope, PASSWORD, RestoreMode.OVERWRITE)

        assertEquals(3, mergeAnalysis.resolvedPayload.providersModels.providers.size)
        assertEquals(
            "Imported Shared",
            mergeAnalysis.resolvedPayload.providersModels.providers.first { it.id == "provider-shared" }.name,
        )
        assertEquals(2, overwriteAnalysis.resolvedPayload.providersModels.providers.size)
        assertEquals(
            listOf("provider-remote", "provider-shared"),
            overwriteAnalysis.resolvedPayload.providersModels.providers.map { it.id }.sorted(),
        )

        val mergePreview = mergeAnalysis.categoryPreviews.first { it.categoryId == SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS }
        val overwritePreview = overwriteAnalysis.categoryPreviews.first { it.categoryId == SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS }
        assertTrue(mergePreview.selectable)
        assertTrue(mergePreview.includedByDefault)
        assertFalse(overwritePreview.selectable)
        assertTrue(mergePreview.conflictKeys.contains("provider-shared"))

        val storedProvidersJson = dataStore.data.first()[stringPreferencesKey("providers_json")].orEmpty()
        assertTrue(storedProvidersJson.contains("Local Shared"))
        assertTrue(storedProvidersJson.contains("Local Only"))
        assertFalse(storedProvidersJson.contains("Imported Shared"))
    }

    @Test
    fun analyzeEncryptedBackup_emitsCrossVersionWarningsWithoutFailingKnownCategories() = runBlocking {
        val repository = SettingsBackupRepository(
            dataStore = MutablePreferencesDataStore(seedPreferences()),
            credentialSource = FakeProviderCredentialSource(),
            gson = gson,
            currentAppVersionNameProvider = { "1.0.0" },
        )

        val envelope = envelopeForPayload(
            SettingsBackupPayload(
                providersModels = ProvidersModelsBackup(
                    providers = listOf(provider(id = "provider-import", name = "Imported", modelId = "model-import")),
                ),
            ),
            appVersionName = "2.0.0",
            schemaVersion = SETTINGS_BACKUP_SCHEMA_VERSION + 1,
        )

        val analysis = repository.analyzeEncryptedBackup(envelope, PASSWORD, RestoreMode.OVERWRITE)

        assertTrue(analysis.warnings.any { it.kind == RestoreWarningKind.NEWER_SCHEMA_VERSION })
        assertTrue(analysis.warnings.any { it.kind == RestoreWarningKind.NEWER_APP_VERSION })
        assertEquals(1, analysis.resolvedPayload.providersModels.providers.size)
    }

    @Test
    fun analyzeEncryptedBackup_skipsInvalidItemsAndCategoriesButKeepsValidData() = runBlocking {
        val repository = SettingsBackupRepository(
            dataStore = MutablePreferencesDataStore(seedPreferences()),
            credentialSource = FakeProviderCredentialSource(),
            gson = gson,
        )

        val payloadJson = """
            {
              "schemaVersion": 1,
              "providersModels": {
                "providers": [
                  {
                    "id": "provider-valid",
                    "name": "Valid Provider",
                    "type": "OPENAI",
                    "endpoint": "https://api.openai.com/v1",
                    "authMode": "API_KEY",
                    "models": [
                      { "id": "model-valid", "name": "Valid Model", "kind": "TEXT" }
                    ]
                  },
                  {
                    "id": "",
                    "name": "Broken Provider",
                    "type": "OPENAI",
                    "endpoint": "https://api.openai.com/v1",
                    "models": []
                  },
                  5
                ]
              },
              "providerCredentials": {
                "credentials": [
                  { "providerId": "provider-valid", "providerName": "Valid Provider", "apiKey": "secret-1" },
                  { "providerId": "provider-bad", "providerName": "Broken Provider", "apiKey": "" }
                ]
              },
              "appMappings": "oops"
            }
        """.trimIndent()

        val analysis = repository.analyzeEncryptedBackup(
            envelopeForJson(payloadJson),
            PASSWORD,
            RestoreMode.MERGE,
        )

        assertEquals(1, analysis.resolvedPayload.providersModels.providers.size)
        assertEquals(1, analysis.resolvedPayload.providerCredentials.credentials.size)
        assertEquals("provider-valid", analysis.resolvedPayload.providersModels.providers.single().id)
        assertTrue(analysis.skippedItems.any { it.categoryId == SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS && it.itemKey == "provider[1]" })
        assertTrue(analysis.skippedItems.any { it.categoryId == SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS && it.itemKey == "provider[2]" })
        assertTrue(analysis.skippedItems.any { it.categoryId == SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS && it.itemKey == "credential[1]" })
        assertTrue(analysis.skippedItems.any { it.categoryId == SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS })
        assertFalse(analysis.categoryPreviews.first { it.categoryId == SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS }.isAvailable)
    }

    private fun envelopeForPayload(
        payload: SettingsBackupPayload,
        appVersionName: String = "1.0.0",
        schemaVersion: Int = SETTINGS_BACKUP_SCHEMA_VERSION,
    ): SettingsBackupEnvelope {
        return SettingsBackupCrypto.encryptUtf8(
            plaintext = gson.toJson(payload),
            password = PASSWORD,
            appVersionName = appVersionName,
            exportedAtUtc = "2026-03-09T18:00:00Z",
        ).copy(schemaVersion = schemaVersion)
    }

    private fun envelopeForJson(payloadJson: String): SettingsBackupEnvelope {
        return SettingsBackupCrypto.encryptUtf8(
            plaintext = payloadJson,
            password = PASSWORD,
            appVersionName = "1.0.0",
            exportedAtUtc = "2026-03-09T18:00:00Z",
        )
    }

    private fun seedPreferences(
        providers: List<ServiceProvider> = emptyList(),
    ): Preferences {
        return mutablePreferencesOf(
            stringPreferencesKey("providers_json") to gson.toJson(providers),
        )
    }

    private fun provider(
        id: String,
        name: String,
        modelId: String,
    ): ServiceProvider {
        return ServiceProvider(
            id = id,
            name = name,
            type = ProviderType.OPENAI,
            endpoint = "https://api.openai.com/v1",
            authMode = ProviderAuthMode.API_KEY,
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

    private class FakeProviderCredentialSource : ProviderCredentialSource {
        private val credentials = linkedMapOf<String, String>()

        override fun getProviderApiKey(providerId: String): String? = credentials[providerId]
    }

    private companion object {
        const val PASSWORD = "backup-password"
    }
}
