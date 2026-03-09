package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupRepositoryExportTest {

    private val gson = Gson()

    @Test
    fun exportEncryptedBackup_includesEveryRequiredCategoryInManifestAndPayload() = runBlocking {
        val repository = SettingsBackupRepository(
            dataStore = FakePreferencesDataStore(seedPreferences()),
            credentialSource = FakeProviderCredentialSource(mapOf("provider-openai" to "live-secret-key")),
            gson = gson,
            timestampProvider = { "2026-03-09T17:05:00Z" },
        )

        val envelope = repository.exportEncryptedBackup(
            password = "backup-password",
            appVersionName = "0.1.3",
        )
        val payload = decodePayload(envelope)

        assertEquals(
            SETTINGS_BACKUP_CATEGORY_MANIFEST.map { it.id },
            envelope.categoryManifest.map { it.id },
        )
        assertEquals("0.1.3", envelope.appVersionName)
        assertEquals("2026-03-09T17:05:00Z", envelope.exportedAtUtc)
        assertEquals(1, payload.providersModels.providers.size)
        assertEquals(1, payload.providerCredentials.credentials.size)
        assertEquals("provider-openai", payload.activeSelections.activeSttProviderId)
        assertEquals(1, payload.languageDefaults.profiles.size)
        assertEquals(1, payload.promptsProfiles.promptProfiles.size)
        assertEquals(1, payload.appMappings.mappings.size)
        assertEquals("cleanup", payload.transformPresets.enhancementPresetId)
        assertTrue(payload.keyboardBehavior.autoRecordingStart)
        assertTrue(payload.privacySafety.perAppSendPolicies["com.example.blocked"] == true)
        assertEquals("nightly", payload.advancedPreferences.updateChannel)
    }

    @Test
    fun exportEncryptedBackup_keepsCredentialsOnlyInCredentialsCategory() = runBlocking {
        val repository = SettingsBackupRepository(
            dataStore = FakePreferencesDataStore(seedPreferences()),
            credentialSource = FakeProviderCredentialSource(mapOf("provider-openai" to "live-secret-key")),
            gson = gson,
            timestampProvider = { "2026-03-09T17:05:00Z" },
        )

        val envelope = repository.exportEncryptedBackup(
            password = "backup-password",
            appVersionName = "0.1.3",
        )
        val payloadJson = SettingsBackupCrypto.decryptUtf8(envelope, "backup-password")
        val payloadObject = JsonParser.parseString(payloadJson).asJsonObject
        val providersJson = payloadObject.getAsJsonObject("providersModels").getAsJsonArray("providers").toString()
        val credentials = decodePayload(envelope).providerCredentials.credentials

        assertFalse(providersJson.contains("apiKey"))
        assertFalse(providersJson.contains("live-secret-key"))
        assertEquals(1, credentials.size)
        assertEquals("provider-openai", credentials.first().providerId)
        assertEquals("live-secret-key", credentials.first().apiKey)
    }

    @Test
    fun exportEncryptedBackup_capturesCurrentSettingsSurfacesWithoutLegacyPreferenceMap() = runBlocking {
        val repository = SettingsBackupRepository(
            dataStore = FakePreferencesDataStore(seedPreferences()),
            credentialSource = FakeProviderCredentialSource(mapOf("provider-openai" to "live-secret-key")),
            gson = gson,
            timestampProvider = { "2026-03-09T17:05:00Z" },
        )

        val payload = decodePayload(
            repository.exportEncryptedBackup(
                password = "backup-password",
                appVersionName = "0.1.3",
            )
        )

        assertEquals("Base prompt", payload.promptsProfiles.globalBasePrompt)
        assertEquals("profile-1", payload.promptsProfiles.promptProfiles.single().id)
        assertEquals("com.example.app", payload.appMappings.mappings.single().packageName)
        assertEquals("provider-openai", payload.activeSelections.activeTextProviderId)
        assertEquals("model-gpt", payload.activeSelections.commandTextModelId)
        assertEquals("en-US", payload.languageDefaults.profiles.single().languageCode)
        assertTrue(payload.keyboardBehavior.hapticFeedbackEnabled)
        assertTrue(payload.privacySafety.smartFixEnabled)
        assertTrue(payload.privacySafety.useContext)
        assertTrue(payload.privacySafety.disclosureShownCommandText)
        assertEquals("nightly", payload.advancedPreferences.updateChannel)
    }

    private fun decodePayload(envelope: SettingsBackupEnvelope): SettingsBackupPayload {
        return gson.fromJson(
            SettingsBackupCrypto.decryptUtf8(envelope, "backup-password"),
            SettingsBackupPayload::class.java,
        )
    }

    private fun seedPreferences(): Preferences {
        return mutablePreferencesOf(
            stringPreferencesKey("providers_json") to gson.toJson(
                listOf(
                    ServiceProvider(
                        id = "provider-openai",
                        name = "OpenAI",
                        type = ProviderType.OPENAI,
                        endpoint = "https://api.openai.com/v1",
                        models = listOf(
                            ModelConfig(
                                id = "model-whisper",
                                name = "Whisper",
                                kind = ModelKind.STT,
                                streamingPartialsSupported = true,
                            ),
                            ModelConfig(
                                id = "model-gpt",
                                name = "GPT",
                                kind = ModelKind.TEXT,
                            ),
                        ),
                    )
                )
            ),
            stringPreferencesKey("profiles_json") to gson.toJson(
                listOf(
                    LanguageProfile(
                        languageCode = "en-US",
                        transcriptionProviderId = "provider-openai",
                        transcriptionModelId = "model-whisper",
                        smartFixProviderId = "provider-openai",
                        smartFixModelId = "model-gpt",
                    )
                )
            ),
            stringPreferencesKey("global_base_prompt") to "Base prompt",
            stringPreferencesKey("prompt_profiles_json") to gson.toJson(
                listOf(
                    PromptProfile(
                        id = "profile-1",
                        name = "Support",
                        promptAppend = "Use a calm tone",
                    )
                )
            ),
            stringPreferencesKey("app_prompt_mappings_json") to gson.toJson(
                listOf(
                    AppPromptMapping(
                        packageName = "com.example.app",
                        profileId = "profile-1",
                        appPromptAppend = "Ticket context",
                        sttOverride = ProviderModelOverride(
                            providerId = "provider-openai",
                            modelId = "model-whisper",
                        ),
                        textOverride = ProviderModelOverride(
                            providerId = "provider-openai",
                            modelId = "model-gpt",
                        ),
                    )
                )
            ),
            stringPreferencesKey("active-stt-provider-id") to "provider-openai",
            stringPreferencesKey("active-stt-model-id") to "model-whisper",
            stringPreferencesKey("active-text-provider-id") to "provider-openai",
            stringPreferencesKey("active-text-model-id") to "model-gpt",
            stringPreferencesKey("command-text-provider-id") to "provider-openai",
            stringPreferencesKey("command-text-model-id") to "model-gpt",
            stringPreferencesKey("enhancement-preset-id") to "cleanup",
            stringPreferencesKey("command-preset-id") to "tone_rewrite",
            booleanPreferencesKey("is-auto-recording-start") to true,
            booleanPreferencesKey("auto-switch-back") to true,
            booleanPreferencesKey("auto-transcribe-on-pause") to false,
            booleanPreferencesKey("cancel-confirmation") to true,
            booleanPreferencesKey("add-trailing-space") to true,
            booleanPreferencesKey("haptic-feedback-enabled") to true,
            booleanPreferencesKey("sound-effects-enabled") to false,
            booleanPreferencesKey("smart-fix-enabled") to true,
            booleanPreferencesKey("use-context") to true,
            stringPreferencesKey("per-app-send-policy-json") to gson.toJson(mapOf("com.example.blocked" to true)),
            booleanPreferencesKey("secure-field-explanation-dont-show-again") to true,
            booleanPreferencesKey("verbose-network-logs-enabled") to true,
            booleanPreferencesKey("disclosure-shown-dictation-audio") to true,
            booleanPreferencesKey("disclosure-shown-enhancement-text") to false,
            booleanPreferencesKey("disclosure-shown-command-text") to true,
            stringPreferencesKey("update-channel") to "nightly",
        )
    }

    private class FakePreferencesDataStore(
        initialPreferences: Preferences,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(initialPreferences)

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw UnsupportedOperationException("Not used in export tests")
        }
    }

    private class FakeProviderCredentialSource(
        private val credentials: Map<String, String>,
    ) : ProviderCredentialSource {
        override fun getProviderApiKey(providerId: String): String? = credentials[providerId]
    }
}
