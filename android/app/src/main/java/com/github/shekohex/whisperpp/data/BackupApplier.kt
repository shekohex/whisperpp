package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.dictionary.writeDictionary
import com.github.shekohex.whisperpp.data.history.HistoryBackupSource
import com.github.shekohex.whisperpp.data.modes.ModeRules
import com.github.shekohex.whisperpp.data.modes.storedModes
import com.github.shekohex.whisperpp.data.modes.writeModes
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

/** Writes the resolved payload categories back to DataStore, the credential sink and history. */
internal class BackupApplier(
    private val dataStore: DataStore<Preferences>,
    private val credentialSink: ProviderCredentialSink?,
    private val gson: Gson,
    private val historySource: HistoryBackupSource?,
    private val codec: BackupCodec,
    private val repairPlanner: RestoreRepairPlanner,
) {
    suspend fun apply(
        analysis: ImportAnalysis,
        includedCategoryIds: Set<String> = emptySet(),
    ): RestoreSummary {
        val categoriesToApply = resolveCategoriesToApply(analysis, includedCategoryIds)
        if (categoriesToApply.isNotEmpty()) {
            val resolvedPayload = analysis.resolvedPayload
            dataStore.edit { prefs ->
                if (SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS in categoriesToApply) {
                    applyProvidersModels(prefs, resolvedPayload.providersModels)
                }
                if (SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS in categoriesToApply) {
                    applyActiveSelections(prefs, resolvedPayload.activeSelections)
                }
                if (SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS in categoriesToApply) {
                    applyLanguageDefaults(prefs, resolvedPayload.languageDefaults)
                }
                if (SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES in categoriesToApply) {
                    applyPromptsProfiles(prefs, resolvedPayload.promptsProfiles)
                }
                if (SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS in categoriesToApply) {
                    applyAppMappings(prefs, resolvedPayload.appMappings)
                }
                if (SETTINGS_BACKUP_CATEGORY_TRANSFORM_PRESETS in categoriesToApply) {
                    applyTransformPresets(prefs, resolvedPayload.transformPresets)
                }
                if (SETTINGS_BACKUP_CATEGORY_KEYBOARD_BEHAVIOR in categoriesToApply) {
                    applyKeyboardBehavior(prefs, resolvedPayload.keyboardBehavior)
                }
                if (SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY in categoriesToApply) {
                    applyPrivacySafety(prefs, resolvedPayload.privacySafety)
                }
                if (SETTINGS_BACKUP_CATEGORY_ADVANCED_PREFERENCES in categoriesToApply) {
                    applyAdvancedPreferences(prefs, resolvedPayload.advancedPreferences)
                }
                if (SETTINGS_BACKUP_CATEGORY_DICTIONARY in categoriesToApply) {
                    writeDictionary(prefs, resolvedPayload.dictionary, gson)
                }
                if (SETTINGS_BACKUP_CATEGORY_MODES in categoriesToApply) {
                    applyModes(prefs, resolvedPayload.modes)
                }
            }

            if (SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS in categoriesToApply) {
                applyProviderCredentials(resolvedPayload.providerCredentials)
            }
            if (SETTINGS_BACKUP_CATEGORY_HISTORY in categoriesToApply) {
                resolvedPayload.history?.let { history ->
                    historySource?.importBackup(history, replace = analysis.restoreMode == RestoreMode.OVERWRITE)
                }
            }
        }


        val repairOutcome = repairPlanner.repairSelections()

        return RestoreSummary(
            restoreMode = analysis.restoreMode,
            appliedCategories = SETTINGS_BACKUP_CATEGORY_MANIFEST.map { it.id }.filter { it in categoriesToApply },
            skippedItems = analysis.skippedItems,
            warnings = analysis.warnings,
            clearedSelections = repairOutcome.clearedSelections.distinctBy { listOf(it.selectionType, it.providerId, it.modelId, it.reason) },
            repairChecklist = repairOutcome.repairEntries.distinctBy { listOf(it.area, it.providerId, it.selectionType, it.message) },
        )
    }

    private suspend fun applyProviderCredentials(category: ProviderCredentialsBackup) {
        val sink = credentialSink ?: return
        val prefs = dataStore.data.first()
        val currentProviderIds = codec.parseProviders(prefs[PreferenceKeys.PROVIDERS_JSON]).map { it.id }
        val importedProviderIds = category.credentials.map { it.providerId }
        (currentProviderIds + importedProviderIds).distinct().forEach { providerId ->
            sink.clearProviderApiKey(providerId)
        }
        category.credentials.forEach { credential ->
            sink.setProviderApiKey(credential.providerId, credential.apiKey)
        }
    }

    private fun resolveCategoriesToApply(
        analysis: ImportAnalysis,
        includedCategoryIds: Set<String>,
    ): Set<String> {
        val available = analysis.categoryPreviews.filter { it.isAvailable }.map { it.categoryId }.toSet()
        if (analysis.restoreMode == RestoreMode.OVERWRITE) {
            // Opt-in categories (history) are only replaced when explicitly selected.
            val defaults = analysis.categoryPreviews.filter { it.isAvailable && it.includedByDefault }.map { it.categoryId }
            return defaults.toSet() + includedCategoryIds.intersect(available)
        }

        val requested = if (includedCategoryIds.isEmpty()) {
            analysis.categoryPreviews.filter { it.selectable && it.includedByDefault }.map { it.categoryId }.toSet()
        } else {
            includedCategoryIds
        }

        require(requested.all { it in available }) {
            val invalidIds = requested.filterNot { it in available }.sorted().joinToString(", ")
            "Unknown or unavailable backup categories selected: $invalidIds"
        }
        return requested
    }

    /** Restored modes replace the legacy migration: it is marked done so it never runs on top of them. */
    private fun applyModes(prefs: MutablePreferences, category: ModesBackup) {
        val modes = ModeRules.normalize(category.modes)
        // Nothing imported (the backup had no modes): leave the store and the migration state alone.
        if (category.modes.isEmpty() || modes == storedModes(prefs, gson)) return
        writeModes(prefs, modes, gson)
        prefs[PreferenceKeys.MODES_MIGRATED] = true
        val selected = prefs[PreferenceKeys.SELECTED_MODE_ID]
        if (selected != null && modes.none { it.id == selected }) prefs.remove(PreferenceKeys.SELECTED_MODE_ID)
    }

    private fun applyProvidersModels(prefs: MutablePreferences, category: ProvidersModelsBackup) {
        prefs[PreferenceKeys.PROVIDERS_JSON] = gson.toJson(category.providers)
    }

    private fun applyActiveSelections(prefs: MutablePreferences, category: ActiveSelectionsBackup) {
        prefs.putStringOrRemove(PreferenceKeys.ACTIVE_STT_PROVIDER_ID, category.activeSttProviderId)
        prefs.putStringOrRemove(PreferenceKeys.ACTIVE_STT_MODEL_ID, category.activeSttModelId)
        prefs.putStringOrRemove(PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID, category.activeTextProviderId)
        prefs.putStringOrRemove(PreferenceKeys.ACTIVE_TEXT_MODEL_ID, category.activeTextModelId)
        prefs.putStringOrRemove(PreferenceKeys.COMMAND_TEXT_PROVIDER_ID, category.commandTextProviderId)
        prefs.putStringOrRemove(PreferenceKeys.COMMAND_TEXT_MODEL_ID, category.commandTextModelId)
    }

    private fun applyLanguageDefaults(prefs: MutablePreferences, category: LanguageDefaultsBackup) {
        prefs[PreferenceKeys.PROFILES_JSON] = gson.toJson(category.profiles)
    }

    private fun applyPromptsProfiles(prefs: MutablePreferences, category: PromptsProfilesBackup) {
        prefs[PreferenceKeys.GLOBAL_BASE_PROMPT] = sanitizeBasePrompt(category.globalBasePrompt)
        prefs[PreferenceKeys.PROMPT_PROFILES_JSON] = gson.toJson(category.promptProfiles)
    }

    private fun applyAppMappings(prefs: MutablePreferences, category: AppMappingsBackup) {
        prefs[PreferenceKeys.APP_PROMPT_MAPPINGS_JSON] = gson.toJson(category.mappings)
    }

    private fun applyTransformPresets(prefs: MutablePreferences, category: TransformPresetsBackup) {
        prefs.putStringOrRemove(PreferenceKeys.ENHANCEMENT_PRESET_ID, category.enhancementPresetId)
        prefs.putStringOrRemove(PreferenceKeys.COMMAND_PRESET_ID, category.commandPresetId)
    }

    private fun applyKeyboardBehavior(prefs: MutablePreferences, category: KeyboardBehaviorBackup) {
        prefs[PreferenceKeys.AUTO_RECORDING_START] = category.autoRecordingStart
        prefs[PreferenceKeys.AUTO_SWITCH_BACK] = category.autoSwitchBack
        prefs[PreferenceKeys.AUTO_TRANSCRIBE_ON_PAUSE] = category.autoTranscribeOnPause
        prefs[PreferenceKeys.CANCEL_CONFIRMATION] = category.cancelConfirmation
        prefs[PreferenceKeys.ADD_TRAILING_SPACE] = category.addTrailingSpace
        prefs[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] = category.hapticFeedbackEnabled
        prefs[PreferenceKeys.SOUND_EFFECTS_ENABLED] = category.soundEffectsEnabled
        prefs[PreferenceKeys.FILTER_FILLERS_ENABLED] = category.filterFillersEnabled
        prefs[PreferenceKeys.FORMAT_PARAGRAPHS_ENABLED] = category.formatParagraphsEnabled
        prefs[PreferenceKeys.AUTO_SEND_AFTER_INSERT] = category.autoSendAfterInsert
    }

    private fun applyPrivacySafety(prefs: MutablePreferences, category: PrivacySafetyBackup) {
        prefs[PreferenceKeys.ENHANCEMENT_ENABLED] = category.enhancementEnabled
        prefs[PreferenceKeys.USE_CONTEXT] = category.useContext
        prefs[PreferenceKeys.PER_APP_SEND_POLICY_JSON] = gson.toJson(category.perAppSendPolicies)
        prefs[PreferenceKeys.SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN] = category.secureFieldExplanationDontShowAgain
        prefs[PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED] = category.verboseNetworkLogsEnabled
        prefs[PreferenceKeys.DISCLOSURE_SHOWN_DICTATION_AUDIO] = category.disclosureShownDictationAudio
        prefs[PreferenceKeys.DISCLOSURE_SHOWN_ENHANCEMENT_TEXT] = category.disclosureShownEnhancementText
        prefs[PreferenceKeys.DISCLOSURE_SHOWN_COMMAND_TEXT] = category.disclosureShownCommandText
    }

    private fun applyAdvancedPreferences(prefs: MutablePreferences, category: AdvancedPreferencesBackup) {
        prefs.putStringOrRemove(PreferenceKeys.UPDATE_CHANNEL, category.updateChannel)
    }
}
