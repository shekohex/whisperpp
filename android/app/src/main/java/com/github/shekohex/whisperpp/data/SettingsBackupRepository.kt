package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

private data class ParsedImportPayload(
    val payload: SettingsBackupPayload,
    val availableCategoryIds: Set<String>,
    val skippedItems: List<SkippedImportItem>,
    val payloadSchemaVersion: Int,
)

class SettingsBackupRepository(
    private val dataStore: DataStore<Preferences>,
    private val credentialSource: ProviderCredentialSource,
    private val gson: Gson = Gson(),
    private val timestampProvider: () -> String = { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() },
    private val currentAppVersionNameProvider: () -> String = { "" },
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
                enhancementPresetId = prefs.stringValue(backupEnhancementPresetIdKey).ifBlank { TRANSFORM_PRESET_ID_CLEANUP },
                commandPresetId = prefs.stringValue(backupCommandPresetIdKey).ifBlank { TRANSFORM_PRESET_ID_TONE_REWRITE },
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

    suspend fun analyzeEncryptedBackup(
        envelope: SettingsBackupEnvelope,
        password: String,
        restoreMode: RestoreMode,
    ): ImportAnalysis {
        val payloadJson = SettingsBackupCrypto.decryptUtf8(envelope, password)
        val currentPayload = buildExportPayload()
        val parsed = parseImportPayload(payloadJson)
        val resolvedPayload = buildResolvedPayload(
            current = currentPayload,
            imported = parsed.payload,
            availableCategoryIds = parsed.availableCategoryIds,
            restoreMode = restoreMode,
        )

        return ImportAnalysis(
            envelopeSchemaVersion = envelope.schemaVersion,
            payloadSchemaVersion = parsed.payloadSchemaVersion,
            backupAppVersionName = envelope.appVersionName,
            exportedAtUtc = envelope.exportedAtUtc,
            restoreMode = restoreMode,
            resolvedPayload = resolvedPayload,
            categoryPreviews = buildCategoryPreviews(
                current = currentPayload,
                imported = parsed.payload,
                resolved = resolvedPayload,
                availableCategoryIds = parsed.availableCategoryIds,
                restoreMode = restoreMode,
            ),
            warnings = buildWarnings(envelope, parsed.payloadSchemaVersion),
            skippedItems = parsed.skippedItems,
        )
    }

    private fun buildResolvedPayload(
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
        )
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
            smartFixEnabled = imported.privacySafety.smartFixEnabled,
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

    private fun buildCategoryPreviews(
        current: SettingsBackupPayload,
        imported: SettingsBackupPayload,
        resolved: SettingsBackupPayload,
        availableCategoryIds: Set<String>,
        restoreMode: RestoreMode,
    ): List<CategoryPreview> {
        return SETTINGS_BACKUP_CATEGORY_MANIFEST.map { manifest ->
            val currentKeys = categoryKeys(current, manifest.id)
            val importedKeys = if (manifest.id in availableCategoryIds) {
                categoryKeys(imported, manifest.id)
            } else {
                emptyList()
            }
            CategoryPreview(
                categoryId = manifest.id,
                label = manifest.label,
                containsSensitiveContent = manifest.containsSensitiveContent,
                isAvailable = manifest.id in availableCategoryIds,
                selectable = restoreMode == RestoreMode.MERGE && manifest.id in availableCategoryIds,
                includedByDefault = manifest.id in availableCategoryIds,
                importedItemCount = importedKeys.size,
                existingItemCount = currentKeys.size,
                resultingItemCount = categoryKeys(resolved, manifest.id).size,
                conflictKeys = currentKeys.intersect(importedKeys.toSet()).sorted(),
            )
        }
    }

    private fun buildWarnings(
        envelope: SettingsBackupEnvelope,
        payloadSchemaVersion: Int,
    ): List<RestoreWarning> {
        val warnings = mutableListOf<RestoreWarning>()
        addSchemaWarnings(warnings, envelope.schemaVersion, source = "Envelope")
        if (payloadSchemaVersion != envelope.schemaVersion) {
            addSchemaWarnings(warnings, payloadSchemaVersion, source = "Payload")
        }

        val currentAppVersion = currentAppVersionNameProvider().trim()
        if (currentAppVersion.isNotBlank()) {
            when (compareVersionNames(envelope.appVersionName, currentAppVersion)) {
                1 -> warnings += RestoreWarning(
                    kind = RestoreWarningKind.NEWER_APP_VERSION,
                    message = "Backup app version ${envelope.appVersionName} is newer than local app version $currentAppVersion.",
                )

                -1 -> warnings += RestoreWarning(
                    kind = RestoreWarningKind.OLDER_APP_VERSION,
                    message = "Backup app version ${envelope.appVersionName} is older than local app version $currentAppVersion.",
                )
            }
        }
        return warnings.distinctBy { listOf(it.kind, it.message) }
    }

    private fun addSchemaWarnings(
        warnings: MutableList<RestoreWarning>,
        schemaVersion: Int,
        source: String,
    ) {
        when {
            schemaVersion > SETTINGS_BACKUP_SCHEMA_VERSION -> warnings += RestoreWarning(
                kind = RestoreWarningKind.NEWER_SCHEMA_VERSION,
                message = "$source schema version $schemaVersion is newer than supported version $SETTINGS_BACKUP_SCHEMA_VERSION.",
            )

            schemaVersion < SETTINGS_BACKUP_SCHEMA_VERSION -> warnings += RestoreWarning(
                kind = RestoreWarningKind.OLDER_SCHEMA_VERSION,
                message = "$source schema version $schemaVersion is older than supported version $SETTINGS_BACKUP_SCHEMA_VERSION.",
            )
        }
    }

    private fun parseImportPayload(payloadJson: String): ParsedImportPayload {
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
                providersModels = parseProvidersModelsCategory(root, availableCategoryIds, skippedItems),
                providerCredentials = parseProviderCredentialsCategory(root, availableCategoryIds, skippedItems),
                activeSelections = parseActiveSelectionsCategory(root, availableCategoryIds, skippedItems),
                languageDefaults = parseLanguageDefaultsCategory(root, availableCategoryIds, skippedItems),
                promptsProfiles = parsePromptsProfilesCategory(root, availableCategoryIds, skippedItems),
                appMappings = parseAppMappingsCategory(root, availableCategoryIds, skippedItems),
                transformPresets = parseTransformPresetsCategory(root, availableCategoryIds, skippedItems),
                keyboardBehavior = parseKeyboardBehaviorCategory(root, availableCategoryIds, skippedItems),
                privacySafety = parsePrivacySafetyCategory(root, availableCategoryIds, skippedItems),
                advancedPreferences = parseAdvancedPreferencesCategory(root, availableCategoryIds, skippedItems),
            ),
            availableCategoryIds = availableCategoryIds,
            skippedItems = skippedItems,
            payloadSchemaVersion = payloadSchemaVersion,
        )
    }

    private fun parseProvidersModelsCategory(
        root: JsonObject,
        availableCategoryIds: MutableSet<String>,
        skippedItems: MutableList<SkippedImportItem>,
    ): ProvidersModelsBackup {
        val category = root.readCategoryObject("providersModels", SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS, availableCategoryIds, skippedItems)
            ?: return ProvidersModelsBackup()
        val providersElement = category.get("providers")
        if (providersElement == null || providersElement.isJsonNull) {
            return ProvidersModelsBackup()
        }
        if (!providersElement.isJsonArray) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                reason = "Expected providers array.",
            )
            return ProvidersModelsBackup()
        }

        val providers = mutableListOf<ServiceProvider>()
        providersElement.asJsonArray.forEachIndexed { index, element ->
            parseProvider(element, index, skippedItems)?.let(providers::add)
        }
        return ProvidersModelsBackup(providers = providers)
    }

    private fun parseProvider(
        element: JsonElement,
        index: Int,
        skippedItems: MutableList<SkippedImportItem>,
    ): ServiceProvider? {
        if (!element.isJsonObject) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                itemKey = "provider[$index]",
                reason = "Expected provider object.",
            )
            return null
        }

        val providerObject = element.asJsonObject
        val id = providerObject.stringValue("id").trim()
        val name = providerObject.stringValue("name").trim()
        val endpoint = providerObject.stringValue("endpoint").trim()
        if (id.isBlank() || name.isBlank() || endpoint.isBlank()) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                itemKey = "provider[$index]",
                reason = "Provider is missing id, name, or endpoint.",
            )
            return null
        }

        val type = providerObject.enumValue("type", ProviderType.CUSTOM)
        val authMode = providerObject.enumValue(
            "authMode",
            if (type == ProviderType.WHISPER_ASR) ProviderAuthMode.NO_AUTH else ProviderAuthMode.API_KEY,
        )
        val models = parseProviderModels(
            providerId = id,
            providerType = type,
            endpoint = endpoint,
            modelsElement = providerObject.get("models"),
            skippedItems = skippedItems,
        )

        return ServiceProvider(
            id = id,
            name = name,
            type = type,
            endpoint = endpoint,
            authMode = authMode,
            models = models,
            temperature = providerObject.numberValue("temperature")?.toFloat() ?: 0f,
            prompt = providerObject.stringValue("prompt"),
            languageCode = providerObject.stringValue("languageCode").ifBlank { "auto" },
            timeout = providerObject.numberValue("timeout")?.toInt() ?: 10000,
            thinkingEnabled = providerObject.booleanValue("thinkingEnabled") ?: false,
            thinkingType = providerObject.enumValue("thinkingType", ThinkingType.LEVEL),
            thinkingBudget = providerObject.numberValue("thinkingBudget")?.toInt() ?: 4096,
            thinkingLevel = providerObject.stringValue("thinkingLevel").ifBlank { "medium" },
        )
    }

    private fun parseProviderModels(
        providerId: String,
        providerType: ProviderType,
        endpoint: String,
        modelsElement: JsonElement?,
        skippedItems: MutableList<SkippedImportItem>,
    ): List<ModelConfig> {
        if (modelsElement == null || modelsElement.isJsonNull) {
            return emptyList()
        }
        if (!modelsElement.isJsonArray) {
            skippedItems += SkippedImportItem(
                categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                itemKey = providerId,
                reason = "Provider models must be an array.",
            )
            return emptyList()
        }

        val models = mutableListOf<ModelConfig>()
        modelsElement.asJsonArray.forEachIndexed { index, modelElement ->
            if (!modelElement.isJsonObject) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                    itemKey = "$providerId:model[$index]",
                    reason = "Expected model object.",
                )
                return@forEachIndexed
            }
            val modelObject = modelElement.asJsonObject
            val modelId = modelObject.stringValue("id").trim()
            val modelName = modelObject.stringValue("name").trim()
            if (modelId.isBlank() || modelName.isBlank()) {
                skippedItems += SkippedImportItem(
                    categoryId = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
                    itemKey = "$providerId:model[$index]",
                    reason = "Model is missing id or name.",
                )
                return@forEachIndexed
            }
            models += ModelConfig(
                id = modelId,
                name = modelName,
                isThinking = modelObject.booleanValue("isThinking") ?: false,
                kind = modelObject.enumValue("kind", inferModelKind(providerType, endpoint)),
                streamingPartialsSupported = modelObject.booleanValue("streamingPartialsSupported") ?: false,
            )
        }
        return models
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
            if (profile == null || profile.languageCode.isBlank() || profile.transcriptionProviderId.isBlank() || profile.transcriptionModelId.isBlank() || profile.smartFixProviderId.isBlank() || profile.smartFixModelId.isBlank()) {
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
            soundEffectsEnabled = category.booleanValue("soundEffectsEnabled") ?: false,
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
            smartFixEnabled = category.booleanValue("smartFixEnabled") ?: false,
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

    private fun categoryKeys(
        payload: SettingsBackupPayload,
        categoryId: String,
    ): List<String> {
        return when (categoryId) {
            SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS -> payload.providersModels.providers.map { it.id }
            SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS -> payload.providerCredentials.credentials.map { it.providerId }
            SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS -> buildList {
                if (payload.activeSelections.activeSttProviderId.isNotBlank() && payload.activeSelections.activeSttModelId.isNotBlank()) {
                    add("activeStt")
                }
                if (payload.activeSelections.activeTextProviderId.isNotBlank() && payload.activeSelections.activeTextModelId.isNotBlank()) {
                    add("activeText")
                }
                if (payload.activeSelections.commandTextProviderId.isNotBlank() && payload.activeSelections.commandTextModelId.isNotBlank()) {
                    add("commandText")
                }
            }

            SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS -> payload.languageDefaults.profiles.map { it.languageCode }
            SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES -> buildList {
                if (payload.promptsProfiles.globalBasePrompt.isNotBlank()) {
                    add("globalBasePrompt")
                }
                addAll(payload.promptsProfiles.promptProfiles.map { "profile:${it.id}" })
            }

            SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS -> payload.appMappings.mappings.map { it.packageName }
            SETTINGS_BACKUP_CATEGORY_TRANSFORM_PRESETS -> buildList {
                if (payload.transformPresets.enhancementPresetId.isNotBlank()) {
                    add("enhancementPresetId")
                }
                if (payload.transformPresets.commandPresetId.isNotBlank()) {
                    add("commandPresetId")
                }
            }

            SETTINGS_BACKUP_CATEGORY_KEYBOARD_BEHAVIOR -> listOf(
                "autoRecordingStart",
                "autoSwitchBack",
                "autoTranscribeOnPause",
                "cancelConfirmation",
                "addTrailingSpace",
                "hapticFeedbackEnabled",
                "soundEffectsEnabled",
            )

            SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY -> buildList {
                addAll(
                    listOf(
                        "smartFixEnabled",
                        "useContext",
                        "secureFieldExplanationDontShowAgain",
                        "verboseNetworkLogsEnabled",
                        "disclosureShownDictationAudio",
                        "disclosureShownEnhancementText",
                        "disclosureShownCommandText",
                    )
                )
                addAll(payload.privacySafety.perAppSendPolicies.keys.map { "policy:$it" })
            }

            SETTINGS_BACKUP_CATEGORY_ADVANCED_PREFERENCES -> listOf("updateChannel")
            else -> emptyList()
        }.sorted()
    }

    private fun compareVersionNames(
        left: String,
        right: String,
    ): Int {
        val leftParts = left.split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxSize) {
            val leftValue = leftParts.getOrElse(index) { 0 }
            val rightValue = rightParts.getOrElse(index) { 0 }
            if (leftValue != rightValue) {
                return leftValue.compareTo(rightValue)
            }
        }
        return 0
    }

    private fun inferModelKind(
        providerType: ProviderType,
        endpoint: String,
    ): ModelKind {
        return when {
            providerType == ProviderType.WHISPER_ASR -> ModelKind.STT
            providerType == ProviderType.GEMINI -> ModelKind.MULTIMODAL
            endpoint.contains("/audio/transcriptions") -> ModelKind.STT
            else -> ModelKind.TEXT
        }
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

private fun JsonObject.readCategoryObject(
    propertyName: String,
    categoryId: String,
    availableCategoryIds: MutableSet<String>,
    skippedItems: MutableList<SkippedImportItem>,
): JsonObject? {
    val element = get(propertyName) ?: return null
    if (element.isJsonNull) {
        return null
    }
    if (!element.isJsonObject) {
        skippedItems += SkippedImportItem(
            categoryId = categoryId,
            reason = "Expected category object.",
        )
        return null
    }
    availableCategoryIds += categoryId
    return element.asJsonObject
}

private fun JsonObject.stringValue(name: String): String {
    val value = get(name) ?: return ""
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
        return ""
    }
    return value.asString.trim()
}

private fun JsonObject.booleanValue(name: String): Boolean? {
    val value = get(name) ?: return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) {
        return null
    }
    return value.asBoolean
}

private fun JsonObject.numberValue(name: String): Number? {
    val value = get(name) ?: return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
        return null
    }
    return value.asNumber
}

private inline fun <reified T : Enum<T>> JsonObject.enumValue(
    name: String,
    defaultValue: T,
): T {
    val rawValue = stringValue(name)
    if (rawValue.isBlank()) {
        return defaultValue
    }
    return runCatching { enumValueOf<T>(rawValue) }.getOrElse { defaultValue }
}

private fun Preferences.stringValue(key: Preferences.Key<String>): String {
    return this[key]?.trim().orEmpty()
}
