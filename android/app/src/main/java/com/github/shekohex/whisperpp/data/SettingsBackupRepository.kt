package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit

private val backupProvidersJsonKey = stringPreferencesKey("providers_json")
private val backupProfilesJsonKey = stringPreferencesKey("profiles_json")
private val backupGlobalBasePromptKey = stringPreferencesKey("global_base_prompt")
private val backupPromptProfilesJsonKey = stringPreferencesKey("prompt_profiles_json")
private val backupAppPromptMappingsJsonKey = stringPreferencesKey("app_prompt_mappings_json")
private val backupActiveSttProviderIdKey = stringPreferencesKey("active-stt-provider-id")
private val backupActiveSttModelIdKey = stringPreferencesKey("active-stt-model-id")
private val backupActiveTextProviderIdKey = stringPreferencesKey("active-text-provider-id")
private val backupActiveTextModelIdKey = stringPreferencesKey("active-text-model-id")
private val backupCommandTextProviderIdKey = stringPreferencesKey("command-text-provider-id")
private val backupCommandTextModelIdKey = stringPreferencesKey("command-text-model-id")
private val backupEnhancementPresetIdKey = stringPreferencesKey("enhancement-preset-id")
private val backupCommandPresetIdKey = stringPreferencesKey("command-preset-id")
private val backupAutoRecordingStartKey = booleanPreferencesKey("is-auto-recording-start")
private val backupAutoSwitchBackKey = booleanPreferencesKey("auto-switch-back")
private val backupAutoTranscribeOnPauseKey = booleanPreferencesKey("auto-transcribe-on-pause")
private val backupCancelConfirmationKey = booleanPreferencesKey("cancel-confirmation")
private val backupAddTrailingSpaceKey = booleanPreferencesKey("add-trailing-space")
private val backupHapticFeedbackEnabledKey = booleanPreferencesKey("haptic-feedback-enabled")
private val backupSoundEffectsEnabledKey = booleanPreferencesKey("sound-effects-enabled")
private val backupSmartFixEnabledKey = booleanPreferencesKey("smart-fix-enabled")
private val backupUseContextKey = booleanPreferencesKey("use-context")
private val backupPerAppSendPolicyJsonKey = stringPreferencesKey("per-app-send-policy-json")
private val backupSecureFieldExplanationDontShowAgainKey = booleanPreferencesKey("secure-field-explanation-dont-show-again")
private val backupVerboseNetworkLogsEnabledKey = booleanPreferencesKey("verbose-network-logs-enabled")
private val backupDisclosureShownDictationAudioKey = booleanPreferencesKey("disclosure-shown-dictation-audio")
private val backupDisclosureShownEnhancementTextKey = booleanPreferencesKey("disclosure-shown-enhancement-text")
private val backupDisclosureShownCommandTextKey = booleanPreferencesKey("disclosure-shown-command-text")
private val backupUpdateChannelKey = stringPreferencesKey("update-channel")

interface ProviderCredentialSource {
    fun getProviderApiKey(providerId: String): String?
}

class SecretsStoreProviderCredentialSource(
    private val secretsStore: SecretsStore,
) : ProviderCredentialSource {
    override fun getProviderApiKey(providerId: String): String? {
        return secretsStore.getProviderApiKey(providerId)
    }
}

class SettingsBackupRepository(
    private val dataStore: DataStore<Preferences>,
    private val credentialSource: ProviderCredentialSource,
    private val gson: Gson = Gson(),
    private val timestampProvider: () -> String = { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() },
) {
    suspend fun buildExportPayload(): SettingsBackupPayload {
        val prefs = dataStore.data.first()
        val providers = parseProviders(prefs[backupProvidersJsonKey])

        return SettingsBackupPayload(
            providersModels = ProvidersModelsBackup(
                providers = providers,
            ),
            providerCredentials = ProviderCredentialsBackup(
                credentials = providers.mapNotNull { provider ->
                    val apiKey = credentialSource.getProviderApiKey(provider.id)?.trim().orEmpty()
                    if (provider.id.isBlank() || apiKey.isBlank()) {
                        null
                    } else {
                        ProviderCredentialBackupEntry(
                            providerId = provider.id,
                            providerName = provider.name,
                            apiKey = apiKey,
                        )
                    }
                },
            ),
            activeSelections = ActiveSelectionsBackup(
                activeSttProviderId = prefs.stringValue(backupActiveSttProviderIdKey),
                activeSttModelId = prefs.stringValue(backupActiveSttModelIdKey),
                activeTextProviderId = prefs.stringValue(backupActiveTextProviderIdKey),
                activeTextModelId = prefs.stringValue(backupActiveTextModelIdKey),
                commandTextProviderId = prefs.stringValue(backupCommandTextProviderIdKey),
                commandTextModelId = prefs.stringValue(backupCommandTextModelIdKey),
            ),
            languageDefaults = LanguageDefaultsBackup(
                profiles = parseLanguageProfiles(prefs[backupProfilesJsonKey]),
            ),
            promptsProfiles = PromptsProfilesBackup(
                globalBasePrompt = prefs.stringValue(backupGlobalBasePromptKey),
                promptProfiles = parsePromptProfiles(prefs[backupPromptProfilesJsonKey]),
            ),
            appMappings = AppMappingsBackup(
                mappings = parseAppPromptMappings(prefs[backupAppPromptMappingsJsonKey]),
            ),
            transformPresets = TransformPresetsBackup(
                enhancementPresetId = prefs.stringValue(backupEnhancementPresetIdKey).ifBlank { "cleanup" },
                commandPresetId = prefs.stringValue(backupCommandPresetIdKey).ifBlank { "tone_rewrite" },
            ),
            keyboardBehavior = KeyboardBehaviorBackup(
                autoRecordingStart = prefs[backupAutoRecordingStartKey] ?: true,
                autoSwitchBack = prefs[backupAutoSwitchBackKey] ?: false,
                autoTranscribeOnPause = prefs[backupAutoTranscribeOnPauseKey] ?: true,
                cancelConfirmation = prefs[backupCancelConfirmationKey] ?: true,
                addTrailingSpace = prefs[backupAddTrailingSpaceKey] ?: false,
                hapticFeedbackEnabled = prefs[backupHapticFeedbackEnabledKey] ?: true,
                soundEffectsEnabled = prefs[backupSoundEffectsEnabledKey] ?: true,
            ),
            privacySafety = PrivacySafetyBackup(
                smartFixEnabled = prefs[backupSmartFixEnabledKey] ?: false,
                useContext = prefs[backupUseContextKey] ?: false,
                perAppSendPolicies = parseSendPolicies(prefs[backupPerAppSendPolicyJsonKey]),
                secureFieldExplanationDontShowAgain = prefs[backupSecureFieldExplanationDontShowAgainKey] ?: false,
                verboseNetworkLogsEnabled = prefs[backupVerboseNetworkLogsEnabledKey] ?: false,
                disclosureShownDictationAudio = prefs[backupDisclosureShownDictationAudioKey] ?: false,
                disclosureShownEnhancementText = prefs[backupDisclosureShownEnhancementTextKey] ?: false,
                disclosureShownCommandText = prefs[backupDisclosureShownCommandTextKey] ?: false,
            ),
            advancedPreferences = AdvancedPreferencesBackup(
                updateChannel = prefs.stringValue(backupUpdateChannelKey).ifBlank { "stable" },
            ),
        )
    }

    suspend fun exportEncryptedBackup(
        password: String,
        appVersionName: String,
        exportedAtUtc: String = timestampProvider(),
    ): SettingsBackupEnvelope {
        val payloadJson = gson.toJson(buildExportPayload())
        return SettingsBackupCrypto.encryptUtf8(
            plaintext = payloadJson,
            password = password,
            appVersionName = appVersionName,
            exportedAtUtc = exportedAtUtc,
        )
    }

    private fun parseProviders(json: String?): List<ServiceProvider> {
        return parseList(json, object : TypeToken<List<ServiceProvider>>() {}.type)
    }

    private fun parseLanguageProfiles(json: String?): List<LanguageProfile> {
        return parseList(json, object : TypeToken<List<LanguageProfile>>() {}.type)
    }

    private fun parsePromptProfiles(json: String?): List<PromptProfile> {
        return parseList(json, object : TypeToken<List<PromptProfile>>() {}.type)
    }

    private fun parseAppPromptMappings(json: String?): List<AppPromptMapping> {
        return parseList(json, object : TypeToken<List<AppPromptMapping>>() {}.type)
    }

    private fun parseSendPolicies(json: String?): Map<String, Boolean> {
        val parsed = parseMap(json)
        return parsed
            .mapKeys { (key, _) -> key.trim() }
            .filterKeys { it.isNotEmpty() }
    }

    private fun <T> parseList(json: String?, type: java.lang.reflect.Type): List<T> {
        val raw = json?.trim().orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }

        return try {
            gson.fromJson<List<T>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseMap(json: String?): Map<String, Boolean> {
        val raw = json?.trim().orEmpty()
        if (raw.isBlank()) {
            return emptyMap()
        }

        return try {
            gson.fromJson<Map<String, Boolean>>(raw, object : TypeToken<Map<String, Boolean>>() {}.type)
                ?.mapValues { it.value == true }
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

private fun Preferences.stringValue(key: Preferences.Key<String>): String {
    return this[key]?.trim().orEmpty()
}
