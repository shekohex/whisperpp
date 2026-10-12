package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup payload is a file format: renaming Kotlin identifiers (SmartFix → Enhancement, G-10)
 * must not change its JSON keys. Schema v3 only adds `models[].capabilities.*`.
 */
class SettingsBackupShapeTest {
    private val gson = Gson()

    @Test
    fun exportedPayload_keepsEveryPreviousKey_andOnlyAddsCapabilities() = runBlocking {
        val paths = exportedKeyPaths()

        val added = paths - PRE_V3_KEY_PATHS
        val removed = PRE_V3_KEY_PATHS - paths
        assertEquals(emptySet<String>(), removed)
        assertEquals(
            setOf("stt", "text", "realtime", "thinking", "vocabulary")
                .map { "providersModels.providers[].models[].capabilities.$it" }
                .toSet(),
            added,
        )
    }

    @Test
    fun capabilities_roundTripThroughExportAndImport() = runBlocking {
        val repository = repository()
        val payloadJson = SettingsBackupCrypto.decryptUtf8(repository.exportEncryptedBackup("pw", "1.0"), "pw")
        val restored = gson.fromJson(payloadJson, SettingsBackupPayload::class.java)

        val models = restored.providersModels.providers.single().models
        assertEquals(ModelCapabilities(stt = true, realtime = true, vocabulary = true), models[0].capabilities)
        assertEquals(ModelCapabilities(text = true, thinking = true), models[1].capabilities)
        assertTrue(restored.privacySafety.enhancementEnabled)
        assertEquals("model-gpt", restored.languageDefaults.profiles.single().enhancementModelId)
    }

    private suspend fun exportedKeyPaths(): Set<String> {
        val envelope = repository().exportEncryptedBackup("pw", "1.0")
        val paths = sortedSetOf<String>()
        fun walk(element: JsonElement, path: String) {
            when {
                element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                    walk(value, if (path.isEmpty()) key else "$path.$key")
                }
                element.isJsonArray -> element.asJsonArray.forEach { walk(it, "$path[]") }
                else -> paths += path
            }
        }
        walk(JsonParser.parseString(SettingsBackupCrypto.decryptUtf8(envelope, "pw")), "")
        return paths
    }

    private fun repository() = SettingsBackupRepository(
        dataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(seed())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw UnsupportedOperationException()
        },
        credentialSource = object : ProviderCredentialSource {
            override fun getProviderApiKey(providerId: String): String? = "secret"
        },
        gson = gson,
        timestampProvider = { "2026-10-12T00:00:00Z" },
    )

    private fun seed(): Preferences = mutablePreferencesOf(
        PreferenceKeys.PROVIDERS_JSON to gson.toJson(
            listOf(
                ServiceProvider(
                    id = "provider-openai",
                    name = "OpenAI",
                    type = ProviderType.OPENAI,
                    endpoint = "https://api.openai.com/v1",
                    models = listOf(
                        ModelConfig("model-whisper", "Whisper", ModelCapabilities.stt(ProviderType.OPENAI, realtime = true)),
                        ModelConfig("model-gpt", "GPT", ModelCapabilities(text = true, thinking = true)),
                    ),
                )
            )
        ),
        PreferenceKeys.PROFILES_JSON to gson.toJson(
            listOf(LanguageProfile("en-US", "provider-openai", "model-whisper", "provider-openai", "model-gpt"))
        ),
        PreferenceKeys.GLOBAL_BASE_PROMPT to "Base prompt",
        PreferenceKeys.PROMPT_PROFILES_JSON to gson.toJson(listOf(PromptProfile(id = "profile-1", name = "Support", promptAppend = "Calm"))),
        PreferenceKeys.APP_PROMPT_MAPPINGS_JSON to gson.toJson(
            listOf(
                AppPromptMapping(
                    packageName = "com.example.app",
                    profileId = "profile-1",
                    appPromptAppend = "Ticket context",
                    sttOverride = ProviderModelOverride(providerId = "provider-openai", modelId = "model-whisper"),
                    textOverride = ProviderModelOverride(providerId = "provider-openai", modelId = "model-gpt"),
                )
            )
        ),
        PreferenceKeys.ENHANCEMENT_ENABLED to true,
        PreferenceKeys.PER_APP_SEND_POLICY_JSON to gson.toJson(mapOf("com.example.blocked" to true)),
    )

    private companion object {
        /** Key paths of the payload exported before WP-2.1 (same seed). */
        val PRE_V3_KEY_PATHS = setOf(
            "activeSelections.activeSttModelId",
            "activeSelections.activeSttProviderId",
            "activeSelections.activeTextModelId",
            "activeSelections.activeTextProviderId",
            "activeSelections.commandTextModelId",
            "activeSelections.commandTextProviderId",
            "advancedPreferences.updateChannel",
            "appMappings.mappings[].appPromptAppend",
            "appMappings.mappings[].appendMode",
            "appMappings.mappings[].packageName",
            "appMappings.mappings[].profileId",
            "appMappings.mappings[].sttOverride.modelId",
            "appMappings.mappings[].sttOverride.providerId",
            "appMappings.mappings[].textOverride.modelId",
            "appMappings.mappings[].textOverride.providerId",
            "keyboardBehavior.addTrailingSpace",
            "keyboardBehavior.autoRecordingStart",
            "keyboardBehavior.autoSwitchBack",
            "keyboardBehavior.autoTranscribeOnPause",
            "keyboardBehavior.cancelConfirmation",
            "keyboardBehavior.hapticFeedbackEnabled",
            "keyboardBehavior.soundEffectsEnabled",
            "languageDefaults.profiles[].languageCode",
            "languageDefaults.profiles[].smartFixModelId",
            "languageDefaults.profiles[].smartFixProviderId",
            "languageDefaults.profiles[].transcriptionModelId",
            "languageDefaults.profiles[].transcriptionProviderId",
            "privacySafety.disclosureShownCommandText",
            "privacySafety.disclosureShownDictationAudio",
            "privacySafety.disclosureShownEnhancementText",
            "privacySafety.perAppSendPolicies.com.example.blocked",
            "privacySafety.secureFieldExplanationDontShowAgain",
            "privacySafety.smartFixEnabled",
            "privacySafety.useContext",
            "privacySafety.verboseNetworkLogsEnabled",
            "promptsProfiles.globalBasePrompt",
            "promptsProfiles.promptProfiles[].id",
            "promptsProfiles.promptProfiles[].name",
            "promptsProfiles.promptProfiles[].promptAppend",
            "providerCredentials.credentials[].apiKey",
            "providerCredentials.credentials[].providerId",
            "providerCredentials.credentials[].providerName",
            "providersModels.providers[].authMode",
            "providersModels.providers[].endpoint",
            "providersModels.providers[].id",
            "providersModels.providers[].languageCode",
            "providersModels.providers[].models[].id",
            "providersModels.providers[].models[].isThinking",
            "providersModels.providers[].models[].kind",
            "providersModels.providers[].models[].name",
            "providersModels.providers[].models[].streamingPartialsSupported",
            "providersModels.providers[].name",
            "providersModels.providers[].prompt",
            "providersModels.providers[].temperature",
            "providersModels.providers[].thinkingBudget",
            "providersModels.providers[].thinkingEnabled",
            "providersModels.providers[].thinkingLevel",
            "providersModels.providers[].thinkingType",
            "providersModels.providers[].timeout",
            "providersModels.providers[].type",
            "schemaVersion",
            "transformPresets.commandPresetId",
            "transformPresets.enhancementPresetId",
        )
    }
}
