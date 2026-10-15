package com.github.shekohex.whisperpp.data

/** Builds the per-category previews and compatibility warnings shown before a restore. */
internal class BackupImportAnalyzer(
    private val currentAppVersionNameProvider: () -> String,
) {
    fun buildCategoryPreviews(
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
                includedByDefault = manifest.id in availableCategoryIds && manifest.includedByDefault,
                importedItemCount = importedKeys.size,
                existingItemCount = currentKeys.size,
                resultingItemCount = categoryKeys(resolved, manifest.id).size,
                conflictKeys = currentKeys.intersect(importedKeys.toSet()).sorted(),
            )
        }
    }

    fun buildWarnings(
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
                "streamingEnhancementHaptics",
                "soundEffectsEnabled",
                "filterFillersEnabled",
                "formatParagraphsEnabled",
                "autoSendAfterInsert",
                "showLiveTranscript",
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
            SETTINGS_BACKUP_CATEGORY_MODES -> payload.modes.modes.map { it.id }
            SETTINGS_BACKUP_CATEGORY_DICTIONARY -> buildList {
                addAll(payload.dictionary.replacements.flatMap { rule -> rule.originals.map { "replacement:${it.lowercase()}" } })
                addAll(payload.dictionary.vocabulary.map { "vocabulary:${it.lowercase()}" })
            }
            SETTINGS_BACKUP_CATEGORY_HISTORY -> payload.history?.entries.orEmpty().map { "entry:${it.createdAt}:${it.rawText.hashCode()}" }
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
}
