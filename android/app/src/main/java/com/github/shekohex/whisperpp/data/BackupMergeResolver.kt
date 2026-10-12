package com.github.shekohex.whisperpp.data

import com.github.shekohex.whisperpp.data.dictionary.DictionaryBackup
import com.github.shekohex.whisperpp.data.dictionary.mergeDictionary
import com.github.shekohex.whisperpp.data.history.HistoryBackup
import com.github.shekohex.whisperpp.data.modes.ModeRules

/** Combines the current payload with an imported one per category, honouring [RestoreMode]. */
internal object BackupMergeResolver {
    fun resolve(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): SettingsBackupPayload {
        return SettingsBackupPayload(
            schemaVersion = imported.schemaVersion,
            providersModels = resolveProvidersModels(current, imported, availableCategoryIds, restoreMode),
            providerCredentials = resolveProviderCredentials(current, imported, availableCategoryIds, restoreMode),
            activeSelections = resolveActiveSelections(current, imported, availableCategoryIds),
            languageDefaults = resolveLanguageDefaults(current, imported, availableCategoryIds, restoreMode),
            promptsProfiles = resolvePromptsProfiles(current, imported, availableCategoryIds, restoreMode),
            appMappings = resolveAppMappings(current, imported, availableCategoryIds, restoreMode),
            transformPresets = resolveTransformPresets(current, imported, availableCategoryIds),
            keyboardBehavior = resolveKeyboardBehavior(current, imported, availableCategoryIds),
            privacySafety = resolvePrivacySafety(current, imported, availableCategoryIds, restoreMode),
            advancedPreferences = resolveAdvancedPreferences(current, imported, availableCategoryIds),
            dictionary = resolveDictionary(current, imported, availableCategoryIds, restoreMode),
            modes = resolveModes(current, imported, availableCategoryIds, restoreMode),
            history = resolveHistory(current, imported, availableCategoryIds, restoreMode),
        )
    }

    private fun resolveHistory(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): HistoryBackup? {
        val incoming = imported.history
        if (SETTINGS_BACKUP_CATEGORY_HISTORY !in availableCategoryIds || incoming == null) {
            return current.history
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return incoming
        }
        val existing = current.history?.entries.orEmpty()
        val merged = (existing + incoming.entries).distinctBy { it.createdAt to it.rawText }.sortedByDescending { it.createdAt }
        return incoming.copy(entries = merged)
    }

    /** Merge by id like providers/prompts; imported modes win the default flag and contested packages. */
    private fun resolveModes(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): ModesBackup {
        val incoming = imported.modes.modes
        if (SETTINGS_BACKUP_CATEGORY_MODES !in availableCategoryIds || incoming.isEmpty()) {
            return current.modes
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return ModesBackup(ModeRules.normalize(incoming))
        }
        val merged = mergeByKey(current.modes.modes, incoming) { it.id }
        return ModesBackup(ModeRules.normalize(merged, claimFirst = incoming.map { it.id }.toSet()))
    }

    private fun resolveProvidersModels(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): ProvidersModelsBackup {
        if (SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS !in availableCategoryIds) {
            return current.providersModels
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return imported.providersModels
        }
        return ProvidersModelsBackup(
            providers = mergeByKey(current.providersModels.providers, imported.providersModels.providers) { it.id },
        )
    }

    private fun resolveProviderCredentials(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): ProviderCredentialsBackup {
        if (SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS !in availableCategoryIds) {
            return current.providerCredentials
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return imported.providerCredentials
        }
        return ProviderCredentialsBackup(
            credentials = mergeByKey(current.providerCredentials.credentials, imported.providerCredentials.credentials) { it.providerId },
        )
    }

    private fun resolveActiveSelections(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
    ): ActiveSelectionsBackup {
        if (SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS !in availableCategoryIds) {
            return current.activeSelections
        }
        return imported.activeSelections
    }

    private fun resolveLanguageDefaults(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): LanguageDefaultsBackup {
        if (SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS !in availableCategoryIds) {
            return current.languageDefaults
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return imported.languageDefaults
        }
        return LanguageDefaultsBackup(
            profiles = mergeByKey(current.languageDefaults.profiles, imported.languageDefaults.profiles) { it.languageCode },
        )
    }

    private fun resolvePromptsProfiles(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): PromptsProfilesBackup {
        if (SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES !in availableCategoryIds) {
            return current.promptsProfiles
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return imported.promptsProfiles
        }
        return PromptsProfilesBackup(
            globalBasePrompt = imported.promptsProfiles.globalBasePrompt,
            promptProfiles = mergeByKey(current.promptsProfiles.promptProfiles, imported.promptsProfiles.promptProfiles) { it.id },
        )
    }

    private fun resolveAppMappings(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): AppMappingsBackup {
        if (SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS !in availableCategoryIds) {
            return current.appMappings
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return imported.appMappings
        }
        return AppMappingsBackup(
            mappings = mergeByKey(current.appMappings.mappings, imported.appMappings.mappings) { it.packageName },
        )
    }

    private fun resolveTransformPresets(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
    ): TransformPresetsBackup {
        if (SETTINGS_BACKUP_CATEGORY_TRANSFORM_PRESETS !in availableCategoryIds) {
            return current.transformPresets
        }
        return imported.transformPresets
    }

    private fun resolveKeyboardBehavior(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
    ): KeyboardBehaviorBackup {
        if (SETTINGS_BACKUP_CATEGORY_KEYBOARD_BEHAVIOR !in availableCategoryIds) {
            return current.keyboardBehavior
        }
        return imported.keyboardBehavior
    }

    private fun resolvePrivacySafety(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): PrivacySafetyBackup {
        if (SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY !in availableCategoryIds) {
            return current.privacySafety
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return imported.privacySafety
        }
        return PrivacySafetyBackup(
            enhancementEnabled = imported.privacySafety.enhancementEnabled,
            useContext = imported.privacySafety.useContext,
            perAppSendPolicies = current.privacySafety.perAppSendPolicies + imported.privacySafety.perAppSendPolicies,
            secureFieldExplanationDontShowAgain = imported.privacySafety.secureFieldExplanationDontShowAgain,
            verboseNetworkLogsEnabled = imported.privacySafety.verboseNetworkLogsEnabled,
            disclosureShownDictationAudio = imported.privacySafety.disclosureShownDictationAudio,
            disclosureShownEnhancementText = imported.privacySafety.disclosureShownEnhancementText,
            disclosureShownCommandText = imported.privacySafety.disclosureShownCommandText,
        )
    }

    private fun resolveAdvancedPreferences(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
    ): AdvancedPreferencesBackup {
        if (SETTINGS_BACKUP_CATEGORY_ADVANCED_PREFERENCES !in availableCategoryIds) {
            return current.advancedPreferences
        }
        return imported.advancedPreferences
    }

    private fun resolveDictionary(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): DictionaryBackup {
        if (SETTINGS_BACKUP_CATEGORY_DICTIONARY !in availableCategoryIds) {
            return current.dictionary
        }
        if (restoreMode == RestoreMode.OVERWRITE) {
            return imported.dictionary
        }
        return mergeDictionary(current.dictionary, imported.dictionary)
    }

    private fun <T, K> mergeByKey(
        current: List<T>,
        imported: List<T>,
        keySelector: (T) -> K,
    ): List<T> {
        val merged = LinkedHashMap<K, T>()
        current.forEach { merged[keySelector(it)] = it }
        imported.forEach { merged[keySelector(it)] = it }
        return merged.values.toList()
    }
}
