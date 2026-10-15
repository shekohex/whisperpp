package com.github.shekohex.whisperpp.data

import com.github.shekohex.whisperpp.data.dictionary.DictionaryBackup
import com.github.shekohex.whisperpp.data.dictionary.sanitizeDictionary
import com.github.shekohex.whisperpp.data.history.HistoryBackup
import com.github.shekohex.whisperpp.data.history.sanitizeHistoryBackup
import com.github.shekohex.whisperpp.data.modes.ModeRules
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

internal data class ParsedImportPayload(
    val payload: SettingsBackupPayload,
    val availableCategoryIds: Set<String>,
    val skippedItems: List<SkippedImportItem>,
    val payloadSchemaVersion: Int,
)

/** Decodes backup payload JSON (import) and the DataStore JSON blobs that feed export. */
internal class BackupCodec(private val gson: Gson) {
    fun parseImportPayload(payloadJson: String): ParsedImportPayload {
        val rootElement = try {
            JsonParser.parseString(payloadJson)
        } catch (exception: Exception) {
            throw IllegalArgumentException("Invalid backup payload: ${exception.message}", exception)
        }
        require(rootElement.isJsonObject) { "Invalid backup payload: expected JSON object" }

        val root = rootElement.asJsonObject
        val skippedItems = mutableListOf<SkippedImportItem>()
        val availableCategoryIds = linkedSetOf<String>()
        val payloadSchemaVersion = root.numberValue("schemaVersion")?.toInt() ?: SETTINGS_BACKUP_SCHEMA_VERSION

        return ParsedImportPayload(
            payload = SettingsBackupPayload(
                schemaVersion = payloadSchemaVersion,
                providersModels = BackupProviderParser.parseProvidersModelsCategory(root, availableCategoryIds, skippedItems),
                providerCredentials = parseProviderCredentialsCategory(root, availableCategoryIds, skippedItems),
                activeSelections = parseActiveSelectionsCategory(root, availableCategoryIds, skippedItems),
                languageDefaults = parseLanguageDefaultsCategory(root, availableCategoryIds, skippedItems),
                promptsProfiles = parsePromptsProfilesCategory(root, availableCategoryIds, skippedItems),
                appMappings = parseAppMappingsCategory(root, availableCategoryIds, skippedItems),
                transformPresets = parseTransformPresetsCategory(root, availableCategoryIds, skippedItems),
                keyboardBehavior = parseKeyboardBehaviorCategory(root, availableCategoryIds, skippedItems),
                privacySafety = parsePrivacySafetyCategory(root, availableCategoryIds, skippedItems),
                advancedPreferences = parseAdvancedPreferencesCategory(root, availableCategoryIds, skippedItems),
                dictionary = parseDictionaryCategory(root, availableCategoryIds, skippedItems),
                modes = parseModesCategory(root, availableCategoryIds, skippedItems),
                history = parseHistoryCategory(root, availableCategoryIds, skippedItems),
            ),
            availableCategoryIds = availableCategoryIds,
            skippedItems = skippedItems,
            payloadSchemaVersion = payloadSchemaVersion,
        )
    }

    private fun parseProviderCredentialsCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): ProviderCredentialsBackup {
        val category = root.readCategoryObject("providerCredentials", SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS, availableCategoryIds, skippedItems)
            ?: return ProviderCredentialsBackup()
        val credentialsElement = category.get("credentials")
        if (credentialsElement == null || credentialsElement.isJsonNull) {
            return ProviderCredentialsBackup()
        }
        if (!credentialsElement.isJsonArray) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS,
                reason = "Expected credentials array.",
            )
            return ProviderCredentialsBackup()
        }

        val credentials = mutableListOf<ProviderCredentialBackupEntry>()
        credentialsElement.asJsonArray.forEachIndexed { index, element ->
            if (!element.isJsonObject) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS,
                    itemKey = "credential[$index]",
                    reason = "Expected credential object.",
                )
                return@forEachIndexed
            }
            val credentialObject = element.asJsonObject
            val providerId = credentialObject.stringValue("providerId").trim()
            val providerName = credentialObject.stringValue("providerName").trim()
            val apiKey = credentialObject.stringValue("apiKey").trim()
            if (providerId.isBlank() || apiKey.isBlank()) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS,
                    itemKey = "credential[$index]",
                    reason = "Credential is missing providerId or apiKey.",
                )
                return@forEachIndexed
            }
            credentials += ProviderCredentialBackupEntry(
                providerId = providerId,
                providerName = providerName.ifBlank { providerId },
                apiKey = apiKey,
            )
        }
        return ProviderCredentialsBackup(credentials = credentials)
    }

    private fun parseActiveSelectionsCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): ActiveSelectionsBackup {
        val category = root.readCategoryObject("activeSelections", SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS, availableCategoryIds, skippedItems)
            ?: return ActiveSelectionsBackup()
        return ActiveSelectionsBackup(
            activeSttProviderId = category.stringValue("activeSttProviderId"),
            activeSttModelId = category.stringValue("activeSttModelId"),
            activeTextProviderId = category.stringValue("activeTextProviderId"),
            activeTextModelId = category.stringValue("activeTextModelId"),
            commandTextProviderId = category.stringValue("commandTextProviderId"),
            commandTextModelId = category.stringValue("commandTextModelId"),
        )
    }

    private fun parseLanguageDefaultsCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): LanguageDefaultsBackup {
        val category = root.readCategoryObject("languageDefaults", SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS, availableCategoryIds, skippedItems)
            ?: return LanguageDefaultsBackup()
        val profilesElement = category.get("profiles")
        if (profilesElement == null || profilesElement.isJsonNull) {
            return LanguageDefaultsBackup()
        }
        if (!profilesElement.isJsonArray) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS,
                reason = "Expected profiles array.",
            )
            return LanguageDefaultsBackup()
        }

        val profiles = mutableListOf<LanguageProfile>()
        profilesElement.asJsonArray.forEachIndexed { index, element ->
            val profile = try {
                gson.fromJson(element, LanguageProfile::class.java)
            } catch (_: Exception) {
                null
            }
            if (profile == null || profile.languageCode.isBlank() || profile.transcriptionProviderId.isBlank() || profile.transcriptionModelId.isBlank() || profile.enhancementProviderId.isBlank() || profile.enhancementModelId.isBlank()) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS,
                    itemKey = "profile[$index]",
                    reason = "Language default is incomplete.",
                )
                return@forEachIndexed
            }
            profiles += profile.copy(languageCode = profile.languageCode.trim())
        }
        return LanguageDefaultsBackup(profiles = profiles)
    }

    private fun parsePromptsProfilesCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): PromptsProfilesBackup {
        val category = root.readCategoryObject("promptsProfiles", SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES, availableCategoryIds, skippedItems)
            ?: return PromptsProfilesBackup()
        val promptProfilesElement = category.get("promptProfiles")
        val promptProfiles = mutableListOf<PromptProfile>()
        when {
            promptProfilesElement == null || promptProfilesElement.isJsonNull -> Unit
            !promptProfilesElement.isJsonArray -> skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES,
                reason = "Expected promptProfiles array.",
            )

            else -> promptProfilesElement.asJsonArray.forEachIndexed { index, element ->
                val profile = try {
                    gson.fromJson(element, PromptProfile::class.java)
                } catch (_: Exception) {
                    null
                }
                val sanitized = profile?.let { sanitizePromptProfiles(listOf(it)).firstOrNull() }
                if (sanitized == null) {
                    skippedItems += SkippedImportItem(
                        categoryId = SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES,
                        itemKey = "promptProfile[$index]",
                        reason = "Prompt profile is invalid.",
                    )
                    return@forEachIndexed
                }
                promptProfiles += sanitized
            }
        }

        return PromptsProfilesBackup(
            globalBasePrompt = sanitizeBasePrompt(category.stringValue("globalBasePrompt")),
            promptProfiles = promptProfiles,
        )
    }

    private fun parseAppMappingsCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): AppMappingsBackup {
        val category = root.readCategoryObject("appMappings", SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS, availableCategoryIds, skippedItems)
            ?: return AppMappingsBackup()
        val mappingsElement = category.get("mappings")
        if (mappingsElement == null || mappingsElement.isJsonNull) {
            return AppMappingsBackup()
        }
        if (!mappingsElement.isJsonArray) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS,
                reason = "Expected mappings array.",
            )
            return AppMappingsBackup()
        }

        val mappings = mutableListOf<AppPromptMapping>()
        mappingsElement.asJsonArray.forEachIndexed { index, element ->
            val mapping = try {
                gson.fromJson(element, AppPromptMapping::class.java)
            } catch (_: Exception) {
                null
            }
            val sanitized = mapping?.let { sanitizeAppPromptMappings(listOf(it)).firstOrNull() }
            if (sanitized == null) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS,
                    itemKey = "mapping[$index]",
                    reason = "App mapping is invalid.",
                )
                return@forEachIndexed
            }
            mappings += sanitized
        }
        return AppMappingsBackup(mappings = mappings)
    }

    private fun parseTransformPresetsCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): TransformPresetsBackup {
        val category = root.readCategoryObject("transformPresets", SETTINGS_BACKUP_CATEGORY_TRANSFORM_PRESETS, availableCategoryIds, skippedItems)
            ?: return TransformPresetsBackup()
        return TransformPresetsBackup(
            enhancementPresetId = category.stringValue("enhancementPresetId").ifBlank { TRANSFORM_PRESET_ID_CLEANUP },
            commandPresetId = category.stringValue("commandPresetId").ifBlank { TRANSFORM_PRESET_ID_TONE_REWRITE },
        )
    }

    private fun parseKeyboardBehaviorCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): KeyboardBehaviorBackup {
        val category = root.readCategoryObject("keyboardBehavior", SETTINGS_BACKUP_CATEGORY_KEYBOARD_BEHAVIOR, availableCategoryIds, skippedItems)
            ?: return KeyboardBehaviorBackup()
        return KeyboardBehaviorBackup(
            autoRecordingStart = category.booleanValue("autoRecordingStart") ?: false,
            autoSwitchBack = category.booleanValue("autoSwitchBack") ?: false,
            autoTranscribeOnPause = category.booleanValue("autoTranscribeOnPause") ?: false,
            cancelConfirmation = category.booleanValue("cancelConfirmation") ?: false,
            addTrailingSpace = category.booleanValue("addTrailingSpace") ?: false,
            hapticFeedbackEnabled = category.booleanValue("hapticFeedbackEnabled") ?: false,
            streamingEnhancementHaptics = category.booleanValue("streamingEnhancementHaptics") ?: false,
            soundEffectsEnabled = category.booleanValue("soundEffectsEnabled") ?: false,
            filterFillersEnabled = category.booleanValue("filterFillersEnabled") ?: false,
            formatParagraphsEnabled = category.booleanValue("formatParagraphsEnabled") ?: false,
            autoSendAfterInsert = category.booleanValue("autoSendAfterInsert") ?: false,
            showLiveTranscript = category.booleanValue("showLiveTranscript") ?: true,
        )
    }

    private fun parsePrivacySafetyCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): PrivacySafetyBackup {
        val category = root.readCategoryObject("privacySafety", SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY, availableCategoryIds, skippedItems)
            ?: return PrivacySafetyBackup()
        val perAppSendPolicies = mutableMapOf<String, Boolean>()
        val policiesElement = category.get("perAppSendPolicies")
        when {
            policiesElement == null || policiesElement.isJsonNull -> Unit
            !policiesElement.isJsonObject -> skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY,
                itemKey = "perAppSendPolicies",
                reason = "Send policy payload must be an object.",
            )

            else -> policiesElement.asJsonObject.entrySet().forEach { entry ->
                val packageName = entry.key.trim()
                val value = entry.value
                if (packageName.isBlank() || !value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) {
                    skippedItems += SkippedImportItem(
                        categoryId = SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY,
                        itemKey = if (packageName.isBlank()) "perAppSendPolicy" else packageName,
                        reason = "Send policy entry is invalid.",
                    )
                } else {
                    perAppSendPolicies[packageName] = value.asBoolean
                }
            }
        }

        return PrivacySafetyBackup(
            enhancementEnabled = category.booleanValue("smartFixEnabled") ?: false,
            useContext = category.booleanValue("useContext") ?: false,
            perAppSendPolicies = perAppSendPolicies,
            secureFieldExplanationDontShowAgain = category.booleanValue("secureFieldExplanationDontShowAgain") ?: false,
            verboseNetworkLogsEnabled = category.booleanValue("verboseNetworkLogsEnabled") ?: false,
            disclosureShownDictationAudio = category.booleanValue("disclosureShownDictationAudio") ?: false,
            disclosureShownEnhancementText = category.booleanValue("disclosureShownEnhancementText") ?: false,
            disclosureShownCommandText = category.booleanValue("disclosureShownCommandText") ?: false,
        )
    }

    private fun parseAdvancedPreferencesCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): AdvancedPreferencesBackup {
        val category = root.readCategoryObject("advancedPreferences", SETTINGS_BACKUP_CATEGORY_ADVANCED_PREFERENCES, availableCategoryIds, skippedItems)
            ?: return AdvancedPreferencesBackup()
        return AdvancedPreferencesBackup(
            updateChannel = category.stringValue("updateChannel").ifBlank { "stable" },
        )
    }

    private fun parseModesCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): ModesBackup {
        val category = root.readCategoryObject("modes", SETTINGS_BACKUP_CATEGORY_MODES, availableCategoryIds, skippedItems)
            ?: return ModesBackup()
        val modesElement = category.get("modes")
        if (modesElement == null || modesElement.isJsonNull) return ModesBackup()
        if (!modesElement.isJsonArray) {
            skippedItems += SkippedImportItem(categoryId = SETTINGS_BACKUP_CATEGORY_MODES, reason = "Expected modes array.")
            return ModesBackup()
        }
        val modes = mutableListOf<Mode>()
        modesElement.asJsonArray.forEachIndexed { index, element ->
            val mode = try {
                ModeRules.sanitize(gson.fromJson(element, Mode::class.java))
            } catch (_: Exception) {
                null
            }
            if (mode == null) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_MODES,
                    itemKey = "mode[$index]",
                    reason = "Mode is invalid.",
                )
                return@forEachIndexed
            }
            modes += mode
        }
        return ModesBackup(modes = modes)
    }

    private fun parseDictionaryCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): DictionaryBackup {
        val category = root.readCategoryObject("dictionary", SETTINGS_BACKUP_CATEGORY_DICTIONARY, availableCategoryIds, skippedItems)
            ?: return DictionaryBackup()
        val decoded = try {
            gson.fromJson(category, DictionaryBackup::class.java)
        } catch (_: Exception) {
            null
        }
        if (decoded == null) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_DICTIONARY,
                reason = "Dictionary is invalid.",
            )
            return DictionaryBackup()
        }
        return sanitizeDictionary(decoded)
    }

    private fun parseHistoryCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): HistoryBackup? {
        val category = root.readCategoryObject("history", SETTINGS_BACKUP_CATEGORY_HISTORY, availableCategoryIds, skippedItems)
            ?: return null
        val decoded = try {
            gson.fromJson(category, HistoryBackup::class.java)
        } catch (_: Exception) {
            null
        }
        if (decoded == null) {
            availableCategoryIds -= SETTINGS_BACKUP_CATEGORY_HISTORY
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_HISTORY,
                reason = "Transcript history is invalid.",
            )
            return null
        }
        return sanitizeHistoryBackup(decoded)
    }

    fun parseProviders(json: String?): List<ServiceProvider> {
        return parseList(json, object : TypeToken<List<ServiceProvider>>() {}.type)
    }

    fun parseLanguageProfiles(json: String?): List<LanguageProfile> {
        return parseList(json, object : TypeToken<List<LanguageProfile>>() {}.type)
    }

    fun parsePromptProfiles(json: String?): List<PromptProfile> {
        return parseList(json, object : TypeToken<List<PromptProfile>>() {}.type)
    }

    fun parseAppPromptMappings(json: String?): List<AppPromptMapping> {
        return parseList(json, object : TypeToken<List<AppPromptMapping>>() {}.type)
    }

    fun parseSendPolicies(json: String?): Map<String, Boolean> {
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
