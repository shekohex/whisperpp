package com.github.shekohex.whisperpp.data

const val SETTINGS_BACKUP_SCHEMA_VERSION = 1
const val SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS = "providers_models"
const val SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS = "provider_credentials"
const val SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS = "active_selections"
const val SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS = "language_defaults"
const val SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES = "prompts_profiles"
const val SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS = "app_mappings"
const val SETTINGS_BACKUP_CATEGORY_TRANSFORM_PRESETS = "transform_presets"
const val SETTINGS_BACKUP_CATEGORY_KEYBOARD_BEHAVIOR = "keyboard_behavior"
const val SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY = "privacy_safety"
const val SETTINGS_BACKUP_CATEGORY_ADVANCED_PREFERENCES = "advanced_preferences"

data class SettingsBackupCategoryManifestEntry(
    val id: String,
    val label: String,
    val containsSensitiveContent: Boolean,
)

data class SettingsBackupKdfHeader(
    val algorithm: String,
    val iterations: Int,
    val keyLengthBits: Int,
    val saltBase64: String,
)

data class SettingsBackupCryptoHeader(
    val algorithm: String,
    val ivBase64: String,
    val authTagBits: Int,
    val kdf: SettingsBackupKdfHeader,
)

data class SettingsBackupEnvelope(
    val schemaVersion: Int,
    val appVersionName: String,
    val exportedAtUtc: String,
    val categoryManifest: List<SettingsBackupCategoryManifestEntry>,
    val crypto: SettingsBackupCryptoHeader,
    val encryptedPayloadBase64: String,
)

data class SettingsBackupPayload(
    val schemaVersion: Int = SETTINGS_BACKUP_SCHEMA_VERSION,
    val providersModels: ProvidersModelsBackup = ProvidersModelsBackup(),
    val providerCredentials: ProviderCredentialsBackup = ProviderCredentialsBackup(),
    val activeSelections: ActiveSelectionsBackup = ActiveSelectionsBackup(),
    val languageDefaults: LanguageDefaultsBackup = LanguageDefaultsBackup(),
    val promptsProfiles: PromptsProfilesBackup = PromptsProfilesBackup(),
    val appMappings: AppMappingsBackup = AppMappingsBackup(),
    val transformPresets: TransformPresetsBackup = TransformPresetsBackup(),
    val keyboardBehavior: KeyboardBehaviorBackup = KeyboardBehaviorBackup(),
    val privacySafety: PrivacySafetyBackup = PrivacySafetyBackup(),
    val advancedPreferences: AdvancedPreferencesBackup = AdvancedPreferencesBackup(),
)

data class ProvidersModelsBackup(
    val providers: List<ServiceProvider> = emptyList(),
)

data class ProviderCredentialBackupEntry(
    val providerId: String,
    val providerName: String,
    val apiKey: String,
)

data class ProviderCredentialsBackup(
    val credentials: List<ProviderCredentialBackupEntry> = emptyList(),
)

data class ActiveSelectionsBackup(
    val activeSttProviderId: String = "",
    val activeSttModelId: String = "",
    val activeTextProviderId: String = "",
    val activeTextModelId: String = "",
    val commandTextProviderId: String = "",
    val commandTextModelId: String = "",
)

data class LanguageDefaultsBackup(
    val profiles: List<LanguageProfile> = emptyList(),
)

data class PromptsProfilesBackup(
    val globalBasePrompt: String = "",
    val promptProfiles: List<PromptProfile> = emptyList(),
)

data class AppMappingsBackup(
    val mappings: List<AppPromptMapping> = emptyList(),
)

data class TransformPresetsBackup(
    val enhancementPresetId: String = "cleanup",
    val commandPresetId: String = "tone_rewrite",
)

data class KeyboardBehaviorBackup(
    val autoRecordingStart: Boolean = false,
    val autoSwitchBack: Boolean = false,
    val autoTranscribeOnPause: Boolean = false,
    val cancelConfirmation: Boolean = false,
    val addTrailingSpace: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
    val soundEffectsEnabled: Boolean = false,
)

data class PrivacySafetyBackup(
    val smartFixEnabled: Boolean = false,
    val useContext: Boolean = false,
    val perAppSendPolicies: Map<String, Boolean> = emptyMap(),
    val secureFieldExplanationDontShowAgain: Boolean = false,
    val verboseNetworkLogsEnabled: Boolean = false,
    val disclosureShownDictationAudio: Boolean = false,
    val disclosureShownEnhancementText: Boolean = false,
    val disclosureShownCommandText: Boolean = false,
)

data class AdvancedPreferencesBackup(
    val updateChannel: String = "stable",
)

enum class RestoreMode {
    OVERWRITE,
    MERGE,
}

enum class RestoreWarningKind {
    NEWER_SCHEMA_VERSION,
    OLDER_SCHEMA_VERSION,
    NEWER_APP_VERSION,
    OLDER_APP_VERSION,
}

data class RestoreWarning(
    val kind: RestoreWarningKind,
    val message: String,
)

data class SkippedImportItem(
    val categoryId: String,
    val itemKey: String? = null,
    val reason: String,
)

data class CategoryPreview(
    val categoryId: String,
    val label: String,
    val containsSensitiveContent: Boolean,
    val isAvailable: Boolean,
    val selectable: Boolean,
    val includedByDefault: Boolean,
    val importedItemCount: Int,
    val existingItemCount: Int,
    val resultingItemCount: Int,
    val conflictKeys: List<String> = emptyList(),
)

data class ImportAnalysis(
    val envelopeSchemaVersion: Int,
    val payloadSchemaVersion: Int,
    val backupAppVersionName: String,
    val exportedAtUtc: String,
    val restoreMode: RestoreMode,
    val resolvedPayload: SettingsBackupPayload,
    val categoryPreviews: List<CategoryPreview>,
    val warnings: List<RestoreWarning> = emptyList(),
    val skippedItems: List<SkippedImportItem> = emptyList(),
)

val SETTINGS_BACKUP_CATEGORY_MANIFEST: List<SettingsBackupCategoryManifestEntry> = listOf(
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS,
        label = "Providers & models",
        containsSensitiveContent = false,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS,
        label = "Provider credentials",
        containsSensitiveContent = true,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS,
        label = "Active selections",
        containsSensitiveContent = false,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS,
        label = "Language defaults",
        containsSensitiveContent = false,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES,
        label = "Prompts & profiles",
        containsSensitiveContent = false,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS,
        label = "App mappings",
        containsSensitiveContent = false,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_TRANSFORM_PRESETS,
        label = "Transform presets",
        containsSensitiveContent = false,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_KEYBOARD_BEHAVIOR,
        label = "Keyboard behavior",
        containsSensitiveContent = false,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY,
        label = "Privacy & safety",
        containsSensitiveContent = false,
    ),
    SettingsBackupCategoryManifestEntry(
        id = SETTINGS_BACKUP_CATEGORY_ADVANCED_PREFERENCES,
        label = "Advanced preferences",
        containsSensitiveContent = false,
    ),
)
