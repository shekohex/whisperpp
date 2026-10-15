package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
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
        assertTrue(payload.keyboardBehavior.showLiveTranscript)
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
        assertTrue(payload.privacySafety.enhancementEnabled)
        assertTrue(payload.privacySafety.useContext)
        assertTrue(payload.privacySafety.disclosureShownCommandText)
        assertEquals("nightly", payload.advancedPreferences.updateChannel)
    }

    @Test
    fun exportEncryptedBackup_roundTripsShowLiveTranscriptDisabled() = runBlocking {
        val repository = SettingsBackupRepository(
            dataStore = FakePreferencesDataStore(
                mutablePreferencesOf(PreferenceKeys.KEYBOARD_SHOW_LIVE_TRANSCRIPT to false)
            ),
            credentialSource = FakeProviderCredentialSource(emptyMap()),
            gson = gson,
            timestampProvider = { "2026-03-09T17:05:00Z" },
        )

        val payload = decodePayload(
            repository.exportEncryptedBackup(
                password = "backup-password",
                appVersionName = "0.1.3",
            )
        )

        assertFalse(payload.keyboardBehavior.showLiveTranscript)
    }

    private fun decodePayload(envelope: SettingsBackupEnvelope): SettingsBackupPayload {
        return gson.fromJson(
            SettingsBackupCrypto.decryptUtf8(envelope, "backup-password"),
            SettingsBackupPayload::class.java,
        )
    }

    private fun seedPreferences(): Preferences {
        return mutablePreferencesOf(
            PreferenceKeys.PROVIDERS_JSON to gson.toJson(
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
                                capabilities = ModelCapabilities.stt(ProviderType.OPENAI, realtime = true),
                            ),
                            ModelConfig(
                                id = "model-gpt",
                                name = "GPT",
                                capabilities = ModelCapabilities.TEXT,
                            ),
                        ),
                    )
                )
            ),
            PreferenceKeys.PROFILES_JSON to gson.toJson(
                listOf(
                    LanguageProfile(
                        languageCode = "en-US",
                        transcriptionProviderId = "provider-openai",
                        transcriptionModelId = "model-whisper",
                        enhancementProviderId = "provider-openai",
                        enhancementModelId = "model-gpt",
                    )
                )
            ),
            PreferenceKeys.GLOBAL_BASE_PROMPT to "Base prompt",
            PreferenceKeys.PROMPT_PROFILES_JSON to gson.toJson(
                listOf(
                    PromptProfile(
                        id = "profile-1",
                        name = "Support",
                        promptAppend = "Use a calm tone",
                    )
                )
            ),
            PreferenceKeys.APP_PROMPT_MAPPINGS_JSON to gson.toJson(
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
            PreferenceKeys.ACTIVE_STT_PROVIDER_ID to "provider-openai",
            PreferenceKeys.ACTIVE_STT_MODEL_ID to "model-whisper",
            PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID to "provider-openai",
            PreferenceKeys.ACTIVE_TEXT_MODEL_ID to "model-gpt",
            PreferenceKeys.COMMAND_TEXT_PROVIDER_ID to "provider-openai",
            PreferenceKeys.COMMAND_TEXT_MODEL_ID to "model-gpt",
            PreferenceKeys.ENHANCEMENT_PRESET_ID to "cleanup",
            PreferenceKeys.COMMAND_PRESET_ID to "tone_rewrite",
            PreferenceKeys.AUTO_RECORDING_START to true,
            PreferenceKeys.AUTO_SWITCH_BACK to true,
            PreferenceKeys.AUTO_TRANSCRIBE_ON_PAUSE to false,
            PreferenceKeys.CANCEL_CONFIRMATION to true,
            PreferenceKeys.ADD_TRAILING_SPACE to true,
            PreferenceKeys.HAPTIC_FEEDBACK_ENABLED to true,
            PreferenceKeys.SOUND_EFFECTS_ENABLED to false,
            PreferenceKeys.ENHANCEMENT_ENABLED to true,
            PreferenceKeys.USE_CONTEXT to true,
            PreferenceKeys.PER_APP_SEND_POLICY_JSON to gson.toJson(mapOf("com.example.blocked" to true)),
            PreferenceKeys.SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN to true,
            PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED to true,
            PreferenceKeys.DISCLOSURE_SHOWN_DICTATION_AUDIO to true,
            PreferenceKeys.DISCLOSURE_SHOWN_ENHANCEMENT_TEXT to false,
            PreferenceKeys.DISCLOSURE_SHOWN_COMMAND_TEXT to true,
            PreferenceKeys.UPDATE_CHANNEL to "nightly",
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
