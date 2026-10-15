package com.github.shekohex.whisperpp.data

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.dictionary.readDictionary
import com.github.shekohex.whisperpp.data.history.HistoryBackupSource
import com.github.shekohex.whisperpp.data.modes.storedModes
import com.google.gson.Gson

/** Snapshots the current preferences, credentials and (opt-in) history into a [SettingsBackupPayload]. */
internal class BackupPayloadBuilder(
    private val codec: BackupCodec,
    private val credentialSource: ProviderCredentialSource,
    private val gson: Gson,
    private val historySource: HistoryBackupSource?,
) {
    /** [includeHistory]: add the opt-in `history` category (transcript text, never audio). */
    suspend fun build(prefs: Preferences, includeHistory: Boolean): SettingsBackupPayload {
        val providers = codec.parseProviders(prefs[PreferenceKeys.PROVIDERS_JSON])

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
                activeSttProviderId = prefs.stringValue(PreferenceKeys.ACTIVE_STT_PROVIDER_ID),
                activeSttModelId = prefs.stringValue(PreferenceKeys.ACTIVE_STT_MODEL_ID),
                activeTextProviderId = prefs.stringValue(PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID),
                activeTextModelId = prefs.stringValue(PreferenceKeys.ACTIVE_TEXT_MODEL_ID),
                commandTextProviderId = prefs.stringValue(PreferenceKeys.COMMAND_TEXT_PROVIDER_ID),
                commandTextModelId = prefs.stringValue(PreferenceKeys.COMMAND_TEXT_MODEL_ID),
            ),
            languageDefaults = LanguageDefaultsBackup(
                profiles = codec.parseLanguageProfiles(prefs[PreferenceKeys.PROFILES_JSON]),
            ),
            promptsProfiles = PromptsProfilesBackup(
                globalBasePrompt = prefs.stringValue(PreferenceKeys.GLOBAL_BASE_PROMPT),
                promptProfiles = codec.parsePromptProfiles(prefs[PreferenceKeys.PROMPT_PROFILES_JSON]),
            ),
            appMappings = AppMappingsBackup(
                mappings = codec.parseAppPromptMappings(prefs[PreferenceKeys.APP_PROMPT_MAPPINGS_JSON]),
            ),
            transformPresets = TransformPresetsBackup(
                enhancementPresetId = prefs.stringValue(PreferenceKeys.ENHANCEMENT_PRESET_ID).ifBlank { TRANSFORM_PRESET_ID_CLEANUP },
                commandPresetId = prefs.stringValue(PreferenceKeys.COMMAND_PRESET_ID).ifBlank { TRANSFORM_PRESET_ID_TONE_REWRITE },
            ),
            keyboardBehavior = KeyboardBehaviorBackup(
                autoRecordingStart = prefs[PreferenceKeys.AUTO_RECORDING_START] ?: true,
                autoSwitchBack = prefs[PreferenceKeys.AUTO_SWITCH_BACK] ?: false,
                autoTranscribeOnPause = prefs[PreferenceKeys.AUTO_TRANSCRIBE_ON_PAUSE] ?: true,
                cancelConfirmation = prefs[PreferenceKeys.CANCEL_CONFIRMATION] ?: true,
                addTrailingSpace = prefs[PreferenceKeys.ADD_TRAILING_SPACE] ?: false,
                hapticFeedbackEnabled = prefs[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] ?: true,
                soundEffectsEnabled = prefs[PreferenceKeys.SOUND_EFFECTS_ENABLED] ?: true,
                filterFillersEnabled = prefs[PreferenceKeys.FILTER_FILLERS_ENABLED] ?: false,
                formatParagraphsEnabled = prefs[PreferenceKeys.FORMAT_PARAGRAPHS_ENABLED] ?: false,
                autoSendAfterInsert = prefs[PreferenceKeys.AUTO_SEND_AFTER_INSERT] ?: false,
                showLiveTranscript = prefs[PreferenceKeys.KEYBOARD_SHOW_LIVE_TRANSCRIPT] ?: true,
            ),
            privacySafety = PrivacySafetyBackup(
                enhancementEnabled = prefs[PreferenceKeys.ENHANCEMENT_ENABLED] ?: false,
                useContext = prefs[PreferenceKeys.USE_CONTEXT] ?: false,
                perAppSendPolicies = codec.parseSendPolicies(prefs[PreferenceKeys.PER_APP_SEND_POLICY_JSON]),
                secureFieldExplanationDontShowAgain = prefs[PreferenceKeys.SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN] ?: false,
                verboseNetworkLogsEnabled = prefs[PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED] ?: false,
                disclosureShownDictationAudio = prefs[PreferenceKeys.DISCLOSURE_SHOWN_DICTATION_AUDIO] ?: false,
                disclosureShownEnhancementText = prefs[PreferenceKeys.DISCLOSURE_SHOWN_ENHANCEMENT_TEXT] ?: false,
                disclosureShownCommandText = prefs[PreferenceKeys.DISCLOSURE_SHOWN_COMMAND_TEXT] ?: false,
            ),
            advancedPreferences = AdvancedPreferencesBackup(
                updateChannel = prefs.stringValue(PreferenceKeys.UPDATE_CHANNEL).ifBlank { "stable" },
            ),
            dictionary = readDictionary(prefs, gson),
            modes = ModesBackup(modes = storedModes(prefs, gson).orEmpty()),
            history = if (includeHistory) historySource?.exportBackup() else null,
        )
    }
}
