package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.APP_PROMPT_MAPPINGS_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_PRESET_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_PRESET_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.GLOBAL_BASE_PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.MODEL
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROFILES_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPTS_PROFILES_SCHEMA_V1_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPT_PROFILES_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDERS_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDER_API_KEY_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDER_SCHEMA_V2_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDER_SCHEMA_V3_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDER_SELECTIONS_V2_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_BACKEND
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_MODEL
import com.github.shekohex.whisperpp.data.PreferenceKeys.SPEECH_TO_TEXT_BACKEND
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnSettings
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewSchedule
import com.github.shekohex.whisperpp.domain.providers.DiscoveredCache
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.domain.providers.DiscoveryIdentity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsExport(
    val version: Int,
    val timestamp: String,
    val appVersionName: String,
    val providers: List<ServiceProvider>,
    val profiles: List<LanguageProfile>,
    val preferences: Map<String, Any?>
)

sealed class ImportResult {
    object Success : ImportResult()
    data class Error(val message: String) : ImportResult()
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val gson = Gson()

    companion object {
        const val EXPORT_VERSION = 1
        private val EXPORTABLE_STRING_KEYS = listOf(
            PreferenceKeys.SPEECH_TO_TEXT_BACKEND,
            PreferenceKeys.ENDPOINT,
            PreferenceKeys.LANGUAGE_CODE,
            PreferenceKeys.MODEL,
            PreferenceKeys.POSTPROCESSING,
            PreferenceKeys.PROMPT,
            PreferenceKeys.ENHANCEMENT_BACKEND,
            PreferenceKeys.ENHANCEMENT_ENDPOINT,
            PreferenceKeys.ENHANCEMENT_MODEL,
            PreferenceKeys.ENHANCEMENT_PROMPT,
            PreferenceKeys.UPDATE_CHANNEL
        )
        private val EXPORTABLE_BOOLEAN_KEYS = listOf(
            PreferenceKeys.AUTO_RECORDING_START,
            PreferenceKeys.AUTO_SWITCH_BACK,
            PreferenceKeys.AUTO_TRANSCRIBE_ON_PAUSE,
            PreferenceKeys.CANCEL_CONFIRMATION,
            PreferenceKeys.ADD_TRAILING_SPACE,
            PreferenceKeys.USE_CONTEXT,
            PreferenceKeys.ENHANCEMENT_ENABLED,
            PreferenceKeys.HAPTIC_FEEDBACK_ENABLED,
            PreferenceKeys.SOUND_EFFECTS_ENABLED,
            PreferenceKeys.PAUSE_MEDIA_WHILE_RECORDING,
            PreferenceKeys.TAP_TO_RECORD
        )
        private val EXPORTABLE_INT_KEYS = listOf(PreferenceKeys.TIMEOUT)
        private val EXPORTABLE_FLOAT_KEYS = listOf(PreferenceKeys.ENHANCEMENT_TEMPERATURE)
    }
    
    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.ONBOARDING_COMPLETED] ?: false }
        .distinctUntilChanged()

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[PreferenceKeys.ONBOARDING_COMPLETED] = true }
    }

    val soundEffectsEnabled: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.SOUND_EFFECTS_ENABLED] ?: true }
        .distinctUntilChanged()

    val pauseMediaWhileRecording: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.PAUSE_MEDIA_WHILE_RECORDING] ?: true }
        .distinctUntilChanged()

    /** A tap on the idle mic starts a locked (hands-free) recording; holding always records. */
    val tapToRecord: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.TAP_TO_RECORD] ?: true }
        .distinctUntilChanged()

    /** Transcript history (§4.8); see [PreferenceKeys.HISTORY_ENABLED]. */
    val historySettings: Flow<HistorySettings> = dataStore.data
        .map { prefs ->
            HistorySettings(
                enabled = prefs[PreferenceKeys.HISTORY_ENABLED] ?: true,
                keepAudio = prefs[PreferenceKeys.HISTORY_KEEP_AUDIO] ?: false,
                retentionDays = (prefs[PreferenceKeys.HISTORY_RETENTION_DAYS] ?: HistorySettings.DEFAULT_RETENTION_DAYS)
                    .coerceAtLeast(0),
            )
        }
        .distinctUntilChanged()

    suspend fun setHistoryEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.HISTORY_ENABLED] = enabled }
    }

    suspend fun setHistoryKeepAudio(keepAudio: Boolean) {
        dataStore.edit { it[PreferenceKeys.HISTORY_KEEP_AUDIO] = keepAudio }
    }

    /** 0 keeps entries forever. */
    suspend fun setHistoryRetentionDays(days: Int) {
        dataStore.edit { it[PreferenceKeys.HISTORY_RETENTION_DAYS] = days.coerceAtLeast(0) }
    }

    val providers: Flow<List<ServiceProvider>> = dataStore.data.map { prefs ->
        val json = prefs[PROVIDERS_JSON]
        if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            val type = object : TypeToken<List<ServiceProvider>>() {}.type
            try {
                val list: List<ServiceProvider>? = gson.fromJson(json, type)
                list?.map { provider ->
                    // Migration/Sanitization: Handle missing fields (nulls) from old JSON
                    // Gson might set non-nullable fields to null if missing in JSON.
                    val safeType = (provider.type as? ProviderType) ?: ProviderType.CUSTOM
                    val safeEndpoint = (provider.endpoint as? String) ?: ""
                    val safeModels = ((provider.models as? List<ModelConfig>) ?: emptyList()).map { model ->
                        ModelConfig(
                            id = (model.id as? String) ?: "",
                            name = (model.name as? String) ?: "",
                            capabilities = sanitizeCapabilities(model, safeType, safeEndpoint),
                        )
                    }
                    val safeAuthMode = (provider.authMode as? ProviderAuthMode) ?: when (safeType) {
                        ProviderType.WHISPER_ASR -> ProviderAuthMode.NO_AUTH
                        else -> ProviderAuthMode.API_KEY
                    }
                    provider.copy(
                        id = (provider.id as? String) ?: "",
                        name = (provider.name as? String) ?: "",
                        type = safeType,
                        endpoint = safeEndpoint,
                        authMode = safeAuthMode,
                        apiKey = "",
                        models = safeModels,
                        prompt = (provider.prompt as? String) ?: "",
                        languageCode = (provider.languageCode as? String) ?: "auto",
                        timeout = (provider.timeout as? Number)?.toInt() ?: 10000,
                        thinkingEnabled = (provider.thinkingEnabled as? Boolean) ?: false,
                        thinkingType = (provider.thinkingType as? ThinkingType) ?: ThinkingType.LEVEL,
                        thinkingBudget = (provider.thinkingBudget as? Number)?.toInt() ?: 4096,
                        thinkingLevel = (provider.thinkingLevel as? String) ?: "medium"
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                // Log error or handle migration failure
                emptyList()
            }
        }
    }

    val enhancementPresetId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[ENHANCEMENT_PRESET_ID]?.trim()?.takeIf { it.isNotBlank() } ?: TRANSFORM_PRESET_ID_CLEANUP
    }

    val commandPresetId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[COMMAND_PRESET_ID]?.trim()?.takeIf { it.isNotBlank() } ?: TRANSFORM_PRESET_ID_TONE_REWRITE
    }

    suspend fun setEnhancementPresetId(id: String?) {
        val normalized = id?.trim()?.takeIf { it.isNotBlank() }
        dataStore.edit { prefs ->
            if (normalized == null) {
                prefs.remove(ENHANCEMENT_PRESET_ID)
            } else {
                prefs[ENHANCEMENT_PRESET_ID] = normalized
            }
        }
    }

    suspend fun setCommandPresetId(id: String?) {
        val normalized = id?.trim()?.takeIf { it.isNotBlank() }
        dataStore.edit { prefs ->
            if (normalized == null) {
                prefs.remove(COMMAND_PRESET_ID)
            } else {
                prefs[COMMAND_PRESET_ID] = normalized
            }
        }
    }

    /** Auto-Learn (§4.11); the review model falls back to the active text (enhancement) selection. */
    val autoLearnSettings: Flow<AutoLearnSettings> = dataStore.data
        .map { prefs ->
            AutoLearnSettings(
                enabled = prefs[PreferenceKeys.AUTO_LEARN_ENABLED] ?: false,
                providerId = prefs[PreferenceKeys.AUTO_LEARN_PROVIDER_ID].orEmpty(),
                modelId = prefs[PreferenceKeys.AUTO_LEARN_MODEL_ID].orEmpty(),
                fallbackProviderId = prefs[ACTIVE_TEXT_PROVIDER_ID].orEmpty(),
                fallbackModelId = prefs[ACTIVE_TEXT_MODEL_ID].orEmpty(),
                reviewSchedule = AutoLearnReviewSchedule.entries
                    .firstOrNull { it.name == prefs[PreferenceKeys.AUTO_LEARN_REVIEW_SCHEDULE] }
                    ?: AutoLearnReviewSchedule.IMMEDIATELY,
                lastReviewAt = prefs[PreferenceKeys.AUTO_LEARN_LAST_REVIEW_AT] ?: 0L,
            )
        }
        .distinctUntilChanged()

    suspend fun setAutoLearnEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.AUTO_LEARN_ENABLED] = enabled }
    }

    /** Blank ids clear the override so the enhancement selection is used. */
    suspend fun setAutoLearnModel(providerId: String?, modelId: String?) {
        val provider = providerId?.trim().orEmpty()
        val model = modelId?.trim().orEmpty()
        dataStore.edit { prefs ->
            if (provider.isEmpty() || model.isEmpty()) {
                prefs.remove(PreferenceKeys.AUTO_LEARN_PROVIDER_ID)
                prefs.remove(PreferenceKeys.AUTO_LEARN_MODEL_ID)
            } else {
                prefs[PreferenceKeys.AUTO_LEARN_PROVIDER_ID] = provider
                prefs[PreferenceKeys.AUTO_LEARN_MODEL_ID] = model
            }
        }
    }

    suspend fun setAutoLearnReviewSchedule(schedule: AutoLearnReviewSchedule) {
        dataStore.edit { it[PreferenceKeys.AUTO_LEARN_REVIEW_SCHEDULE] = schedule.name }
    }

    suspend fun setAutoLearnLastReviewAt(epochMillis: Long) {
        dataStore.edit { it[PreferenceKeys.AUTO_LEARN_LAST_REVIEW_AT] = epochMillis }
    }
    
    suspend fun saveProviders(providers: List<ServiceProvider>) {
        dataStore.edit { prefs ->
            prefs[PROVIDERS_JSON] = gson.toJson(providers)
        }
    }

    suspend fun upsertProvider(provider: ServiceProvider) {
        val id = provider.id.trim()
        if (id.isBlank()) {
            return
        }

        val currentList = providers.first().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            currentList[index] = provider
        } else {
            currentList.add(provider)
        }
        saveProviders(currentList)
    }

    suspend fun migrateProviderApiKeysIfNeeded(secretsStore: SecretsStore) {
        val migrationDone = dataStore.data.map { prefs ->
            prefs[PROVIDER_API_KEY_MIGRATION_DONE] ?: false
        }.first()

        if (migrationDone) {
            return
        }

        val prefs = dataStore.data.first()
        val rawProvidersJson = prefs[PROVIDERS_JSON]

        if (rawProvidersJson.isNullOrBlank()) {
            dataStore.edit { mutablePrefs ->
                mutablePrefs[PROVIDER_API_KEY_MIGRATION_DONE] = true
            }
            return
        }

        val rootElement = try {
            JsonParser.parseString(rawProvidersJson)
        } catch (_: Exception) {
            return
        }

        val providerArray = extractProviderArray(rootElement) ?: return

        providerArray.forEach { providerElement ->
            if (providerElement.isJsonObject) {
                val providerObject = providerElement.asJsonObject
                val providerId = providerObject.get("id")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
                    ?.trim()
                    .orEmpty()
                if (providerId.isNotEmpty()) {
                    val apiKey = providerObject.get("apiKey")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString
                        ?.trim()
                        .orEmpty()
                    if (apiKey.isNotEmpty()) {
                        secretsStore.setProviderApiKey(providerId, apiKey)
                    }
                }
                providerObject.remove("apiKey")
            }
        }

        val sanitizedJson = gson.toJson(rootElement)
        dataStore.edit { mutablePrefs ->
            mutablePrefs[PROVIDERS_JSON] = sanitizedJson
            mutablePrefs[PROVIDER_API_KEY_MIGRATION_DONE] = true
        }
    }

    suspend fun migrateProviderSchemaV2IfNeeded(secretsStore: SecretsStore) {
        val migrationDone = dataStore.data.map { prefs ->
            prefs[PROVIDER_SCHEMA_V2_MIGRATION_DONE] ?: false
        }.first()

        if (migrationDone) {
            return
        }

        migrateProviderApiKeysIfNeeded(secretsStore)

        val prefs = dataStore.data.first()
        val rawProvidersJson = prefs[PROVIDERS_JSON]

        if (rawProvidersJson.isNullOrBlank()) {
            dataStore.edit { mutablePrefs ->
                mutablePrefs[PROVIDER_SCHEMA_V2_MIGRATION_DONE] = true
            }
            return
        }

        val migratedJson = try {
            migrateProvidersJsonToSchemaV2(rawProvidersJson)
        } catch (_: Exception) {
            return
        }

        dataStore.edit { mutablePrefs ->
            if (!migratedJson.isNullOrBlank()) {
                mutablePrefs[PROVIDERS_JSON] = migratedJson
            }
            mutablePrefs[PROVIDER_SCHEMA_V2_MIGRATION_DONE] = true
        }
    }

    /** Fills `capabilities` on every model from the legacy kind/flags (schema v3). */
    suspend fun migrateProviderSchemaV3IfNeeded() {
        val prefs = dataStore.data.first()
        if (prefs[PROVIDER_SCHEMA_V3_MIGRATION_DONE] == true) {
            return
        }
        val migratedJson = prefs[PROVIDERS_JSON]
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                try {
                    migrateProvidersJsonToSchemaV3(raw)
                } catch (_: Exception) {
                    // Unreadable JSON: leave it; the providers flow already degrades to an empty list.
                    return
                }
            }
        dataStore.edit { mutablePrefs ->
            if (!migratedJson.isNullOrBlank()) {
                mutablePrefs[PROVIDERS_JSON] = migratedJson
            }
            mutablePrefs[PROVIDER_SCHEMA_V3_MIGRATION_DONE] = true
        }
    }

    suspend fun migrateProviderSelectionsV2IfNeeded() {
        val migrationDone = dataStore.data.map { prefs ->
            prefs[PROVIDER_SELECTIONS_V2_MIGRATION_DONE] ?: false
        }.first()

        if (migrationDone) {
            return
        }

        dataStore.edit { prefs ->
            migrateLegacySelectionsToV2(prefs)
            prefs[PROVIDER_SELECTIONS_V2_MIGRATION_DONE] = true
        }
    }
    
    val profiles: Flow<List<LanguageProfile>> = dataStore.data.map { prefs ->
        val json = prefs[PROFILES_JSON]
        if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            val type = object : TypeToken<List<LanguageProfile>>() {}.type
            try {
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    suspend fun saveProfiles(profiles: List<LanguageProfile>) {
        dataStore.edit { prefs ->
            prefs[PROFILES_JSON] = gson.toJson(profiles)
        }
    }

    val globalBasePrompt: Flow<String> = dataStore.data.map { prefs ->
        sanitizeBasePrompt(prefs[GLOBAL_BASE_PROMPT])
    }

    val promptProfiles: Flow<List<PromptProfile>> = dataStore.data.map { prefs ->
        parsePromptProfilesJson(prefs[PROMPT_PROFILES_JSON], gson)
    }

    val appPromptMappings: Flow<List<AppPromptMapping>> = dataStore.data.map { prefs ->
        parseAppPromptMappingsJson(prefs[APP_PROMPT_MAPPINGS_JSON], gson)
    }

    suspend fun saveGlobalBasePrompt(prompt: String) {
        val sanitized = sanitizeBasePrompt(prompt)
        dataStore.edit { prefs ->
            prefs[GLOBAL_BASE_PROMPT] = sanitized
        }
    }

    suspend fun savePromptProfiles(profiles: List<PromptProfile>) {
        dataStore.edit { prefs ->
            prefs[PROMPT_PROFILES_JSON] = gson.toJson(sanitizePromptProfiles(profiles))
        }
    }

    suspend fun upsertPromptProfile(profile: PromptProfile) {
        val sanitized = sanitizePromptProfiles(listOf(profile)).firstOrNull() ?: return

        val current = promptProfiles.first().toMutableList()
        val index = current.indexOfFirst { it.id == sanitized.id }
        if (index >= 0) {
            current[index] = sanitized
        } else {
            current.add(sanitized)
        }
        savePromptProfiles(current)
    }

    suspend fun deletePromptProfile(profileId: String) {
        val id = profileId.trim()
        if (id.isBlank()) {
            return
        }
        val current = promptProfiles.first().filterNot { it.id == id }
        savePromptProfiles(current)
    }

    suspend fun saveAppPromptMappings(mappings: List<AppPromptMapping>) {
        dataStore.edit { prefs ->
            prefs[APP_PROMPT_MAPPINGS_JSON] = gson.toJson(sanitizeAppPromptMappings(mappings))
        }
    }

    suspend fun upsertAppPromptMapping(mapping: AppPromptMapping) {
        val sanitized = sanitizeAppPromptMappings(listOf(mapping)).firstOrNull() ?: return

        val current = appPromptMappings.first().toMutableList()
        val index = current.indexOfFirst { it.packageName == sanitized.packageName }
        if (index >= 0) {
            current[index] = sanitized
        } else {
            current.add(sanitized)
        }
        saveAppPromptMappings(current)
    }

    suspend fun deleteAppPromptMapping(packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) {
            return
        }
        val current = appPromptMappings.first().filterNot { it.packageName == pkg }
        saveAppPromptMappings(current)
    }

    suspend fun migratePromptsProfilesSchemaV1IfNeeded() {
        val migrationDone = dataStore.data.map { prefs ->
            prefs[PROMPTS_PROFILES_SCHEMA_V1_MIGRATION_DONE] ?: false
        }.first()

        if (migrationDone) {
            return
        }

        dataStore.edit { prefs ->
            prefs[PROMPTS_PROFILES_SCHEMA_V1_MIGRATION_DONE] = true
        }
    }

    /**
     * Adds the [PromptLibrary] profiles once (WP-2.4). Idempotent: guarded by a flag so profiles the
     * user deleted are not brought back, and existing ids (user edits) are never overwritten.
     */
    suspend fun seedPromptLibraryIfNeeded() {
        dataStore.edit { prefs ->
            if (prefs[PreferenceKeys.PROMPT_LIBRARY_SEEDED] == true) return@edit
            val current = parsePromptProfilesJson(prefs[PROMPT_PROFILES_JSON], gson)
            prefs[PROMPT_PROFILES_JSON] = gson.toJson(sanitizePromptProfiles(PromptLibrary.seed(current)))
            prefs[PreferenceKeys.PROMPT_LIBRARY_SEEDED] = true
        }
    }

    /**
     * Last model listing for [providerId] (WP-2.6), or null when it was never refreshed or was listed
     * for another endpoint/type than [identity] (default: the saved provider's current one).
     */
    fun discoveredModels(providerId: String, identity: DiscoveryIdentity? = null): Flow<DiscoveredCache?> {
        val id = providerId.trim()
        val caches = dataStore.data.map { prefs -> parseDiscoveryCacheJson(prefs[PreferenceKeys.MODEL_DISCOVERY_CACHE], gson)[id] }
        return combine(providers, caches) { list, cache ->
            val expected = identity ?: list.find { it.id == id }?.let(DiscoveryIdentity::of)
            // An unsaved provider without an explicit identity cannot be checked.
            cache?.takeIf { expected == null || it.identity == expected }
        }.distinctUntilChanged()
    }

    /**
     * Replaces the cached listing for [providerId]; provider models are never touched. [identity]
     * defaults to the saved provider's current endpoint and type.
     */
    suspend fun saveDiscovered(
        providerId: String,
        models: List<DiscoveredModel>,
        fetchedAtMillis: Long = System.currentTimeMillis(),
        identity: DiscoveryIdentity? = null,
    ) {
        val id = providerId.trim()
        if (id.isBlank()) return
        val listed = identity ?: providers.first().find { it.id == id }?.let(DiscoveryIdentity::of)
        dataStore.edit { prefs ->
            val current = parseDiscoveryCacheJson(prefs[PreferenceKeys.MODEL_DISCOVERY_CACHE], gson)
            val updated = current + (id to DiscoveredCache(fetchedAtMillis, models, listed))
            prefs[PreferenceKeys.MODEL_DISCOVERY_CACHE] = gson.toJson(updated)
        }
    }

    suspend fun exportSettings(appVersionName: String): String {
        val prefs = dataStore.data.first()
        val providersList = providers.first()
        val profilesList = profiles.first()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        val prefsMap = mutableMapOf<String, Any?>()
        EXPORTABLE_STRING_KEYS.forEach { key ->
            prefs[key]?.let { prefsMap[key.name] = it }
        }
        EXPORTABLE_BOOLEAN_KEYS.forEach { key ->
            prefs[key]?.let { prefsMap[key.name] = it }
        }
        EXPORTABLE_INT_KEYS.forEach { key ->
            prefs[key]?.let { prefsMap[key.name] = it }
        }
        EXPORTABLE_FLOAT_KEYS.forEach { key ->
            prefs[key]?.let { prefsMap[key.name] = it }
        }

        val export = SettingsExport(
            version = EXPORT_VERSION,
            timestamp = timestamp,
            appVersionName = appVersionName,
            providers = providersList,
            profiles = profilesList,
            preferences = prefsMap
        )
        return gson.toJson(export)
    }

    suspend fun importSettings(json: String): ImportResult {
        return try {
            val jsonObj = gson.fromJson(json, JsonObject::class.java)
                ?: return ImportResult.Error("Invalid JSON format")

            val version = jsonObj.get("version")?.asInt
                ?: return ImportResult.Error("Missing version field")

            if (version > EXPORT_VERSION) {
                return ImportResult.Error("Unsupported version: $version (max supported: $EXPORT_VERSION)")
            }

            if (!jsonObj.has("providers") || !jsonObj.has("preferences")) {
                return ImportResult.Error("Missing required fields: providers or preferences")
            }

            val providersType = object : TypeToken<List<ServiceProvider>>() {}.type
            val importedProviders: List<ServiceProvider> = gson.fromJson(jsonObj.get("providers"), providersType)

            val profilesType = object : TypeToken<List<LanguageProfile>>() {}.type
            val importedProfiles: List<LanguageProfile> = if (jsonObj.has("profiles")) {
                gson.fromJson(jsonObj.get("profiles"), profilesType)
            } else {
                emptyList()
            }

            val prefsObj = jsonObj.getAsJsonObject("preferences")

            dataStore.edit { prefs ->
                prefs[PROVIDERS_JSON] = gson.toJson(importedProviders)
                prefs[PROFILES_JSON] = gson.toJson(importedProfiles)

                EXPORTABLE_STRING_KEYS.forEach { key ->
                    prefsObj.get(key.name)?.asString?.let { prefs[key] = it }
                }
                EXPORTABLE_BOOLEAN_KEYS.forEach { key ->
                    prefsObj.get(key.name)?.asBoolean?.let { prefs[key] = it }
                }
                EXPORTABLE_INT_KEYS.forEach { key ->
                    prefsObj.get(key.name)?.asInt?.let { prefs[key] = it }
                }
                EXPORTABLE_FLOAT_KEYS.forEach { key ->
                    prefsObj.get(key.name)?.asFloat?.let { prefs[key] = it }
                }
            }

            ImportResult.Success
        } catch (e: Exception) {
            ImportResult.Error("Failed to import: ${e.message}")
        }
    }
}

internal fun migrateLegacySelectionsToV2(prefs: MutablePreferences) {
    val hasAnyNewKey = prefs.contains(ACTIVE_STT_PROVIDER_ID) ||
        prefs.contains(ACTIVE_STT_MODEL_ID) ||
        prefs.contains(ACTIVE_TEXT_PROVIDER_ID) ||
        prefs.contains(ACTIVE_TEXT_MODEL_ID) ||
        prefs.contains(COMMAND_TEXT_PROVIDER_ID) ||
        prefs.contains(COMMAND_TEXT_MODEL_ID)
    if (hasAnyNewKey) {
        return
    }

    val legacySttProviderId = prefs[SPEECH_TO_TEXT_BACKEND]?.trim().orEmpty()
    val legacySttModelId = prefs[MODEL]?.trim().orEmpty()
    val legacyTextProviderId = prefs[ENHANCEMENT_BACKEND]?.trim().orEmpty()
    val legacyTextModelId = prefs[ENHANCEMENT_MODEL]?.trim().orEmpty()

    if (legacySttProviderId.isNotEmpty()) {
        prefs[ACTIVE_STT_PROVIDER_ID] = legacySttProviderId
    }
    if (legacySttModelId.isNotEmpty()) {
        prefs[ACTIVE_STT_MODEL_ID] = legacySttModelId
    }
    if (legacyTextProviderId.isNotEmpty()) {
        prefs[ACTIVE_TEXT_PROVIDER_ID] = legacyTextProviderId
    }
    if (legacyTextModelId.isNotEmpty()) {
        prefs[ACTIVE_TEXT_MODEL_ID] = legacyTextModelId
    }
}

internal data class ProviderSelection(
    val providerId: String,
    val modelId: String,
)

internal data class EffectiveSelections(
    val stt: ProviderSelection,
    val text: ProviderSelection,
    val commandText: ProviderSelection,
)

internal data class SelectionValidationResult(
    val isSttValid: Boolean,
    val isTextValid: Boolean,
    val isCommandOverrideValid: Boolean,
    val keysToClear: Set<PreferenceKey<String>>,
    val effective: EffectiveSelections,
)

internal fun validateSelections(
    providers: List<ServiceProvider>,
    sttProviderId: String,
    sttModelId: String,
    textProviderId: String,
    textModelId: String,
    commandProviderId: String,
    commandModelId: String,
): SelectionValidationResult {
    val sttProvider = providers.find { it.id == sttProviderId }
    val textProvider = providers.find { it.id == textProviderId }
    val commandProvider = providers.find { it.id == commandProviderId }

    val sttProviderValid = sttProviderId.isNotBlank() && sttProvider != null
    val sttModelValid = sttModelId.isNotBlank() && sttProvider?.models?.any { it.id == sttModelId } == true

    val textProviderValid = textProviderId.isNotBlank() && textProvider != null
    val textModelValid = textModelId.isNotBlank() && textProvider?.models?.any { it.id == textModelId } == true

    val commandProviderValid = commandProviderId.isNotBlank() && commandProvider != null
    val commandModelValid = commandModelId.isNotBlank() && commandProvider?.models?.any { it.id == commandModelId } == true

    val sttValid = sttProviderValid && sttModelValid
    val textValid = textProviderValid && textModelValid
    val commandOverridePresent = commandProviderId.isNotBlank() || commandModelId.isNotBlank()
    val commandOverrideValid = if (!commandOverridePresent) {
        true
    } else {
        commandProviderValid && commandModelValid
    }

    val keysToClear = buildSet<PreferenceKey<String>> {
        if (!sttProviderValid && sttProviderId.isNotBlank()) {
            add(ACTIVE_STT_PROVIDER_ID)
            add(ACTIVE_STT_MODEL_ID)
        } else if (!sttModelValid && sttModelId.isNotBlank()) {
            add(ACTIVE_STT_MODEL_ID)
        }

        if (!textProviderValid && textProviderId.isNotBlank()) {
            add(ACTIVE_TEXT_PROVIDER_ID)
            add(ACTIVE_TEXT_MODEL_ID)
        } else if (!textModelValid && textModelId.isNotBlank()) {
            add(ACTIVE_TEXT_MODEL_ID)
        }

        if (!commandOverrideValid && commandOverridePresent) {
            add(COMMAND_TEXT_PROVIDER_ID)
            add(COMMAND_TEXT_MODEL_ID)
        }
    }

    val effectiveStt = ProviderSelection(
        providerId = if (sttProviderValid) sttProviderId else "",
        modelId = if (sttProviderValid && sttModelValid) sttModelId else "",
    )
    val effectiveText = ProviderSelection(
        providerId = if (textProviderValid) textProviderId else "",
        modelId = if (textProviderValid && textModelValid) textModelId else "",
    )
    val effectiveCommand = if (commandOverridePresent && commandOverrideValid) {
        ProviderSelection(providerId = commandProviderId, modelId = commandModelId)
    } else {
        effectiveText
    }

    return SelectionValidationResult(
        isSttValid = sttValid,
        isTextValid = textValid,
        isCommandOverrideValid = commandOverrideValid,
        keysToClear = keysToClear,
        effective = EffectiveSelections(stt = effectiveStt, text = effectiveText, commandText = effectiveCommand),
    )
}

/** Gson leaves missing fields null; entries without a usable id are dropped. */
@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
internal fun parseDiscoveryCacheJson(json: String?, gson: Gson): Map<String, DiscoveredCache> {
    if (json.isNullOrBlank()) return emptyMap()
    val type = object : TypeToken<Map<String, DiscoveredCache>>() {}.type
    val parsed: Map<String, DiscoveredCache>? = try {
        gson.fromJson(json, type)
    } catch (e: Exception) {
        // The cache is disposable: an unreadable one is rebuilt by the next refresh.
        null
    }
    return parsed.orEmpty().mapNotNull { (providerId, cache) ->
        if (providerId == null || cache == null) return@mapNotNull null
        val models = (cache.models ?: emptyList()).mapNotNull { model ->
            val id = (model?.id ?: "").trim()
            if (id.isEmpty()) return@mapNotNull null
            DiscoveredModel(
                id = id,
                displayName = model.displayName ?: id,
                capabilities = model.capabilities ?: ModelCapabilities(),
                raw = model.raw ?: emptyMap(),
            )
        }
        // Gson leaves missing or unknown fields null; such an identity is treated as absent.
        val identity = cache.identity?.let { raw ->
            val type = raw.providerType as ProviderType?
            val endpoint = raw.endpoint as String?
            if (type != null && endpoint != null) DiscoveryIdentity(type, endpoint) else null
        }
        providerId to DiscoveredCache(cache.fetchedAtMillis, models, identity)
    }.toMap()
}

internal fun sanitizeBasePrompt(value: String?): String {
    val trimmed = value.orEmpty().trim()
    return if (trimmed.isBlank()) "" else trimmed
}

internal fun parsePromptProfilesJson(json: String?, gson: Gson): List<PromptProfile> {
    val raw = json?.trim().orEmpty()
    if (raw.isBlank()) {
        return emptyList()
    }

    val type = object : TypeToken<List<PromptProfile>>() {}.type
    return try {
        val decoded: List<PromptProfile>? = gson.fromJson(raw, type)
        sanitizePromptProfiles(decoded ?: emptyList())
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun sanitizePromptProfiles(profiles: List<PromptProfile>): List<PromptProfile> {
    return profiles.mapNotNull { profile ->
        val id = (profile.id as String?).orEmpty().trim()
        val name = (profile.name as String?).orEmpty().trim()
        val append = (profile.promptAppend as String?).orEmpty().trim()

        if (id.isBlank() || name.isBlank()) {
            return@mapNotNull null
        }

        PromptProfile(
            id = id,
            name = name,
            promptAppend = append,
        )
    }
}

internal fun parseAppPromptMappingsJson(json: String?, gson: Gson): List<AppPromptMapping> {
    val raw = json?.trim().orEmpty()
    if (raw.isBlank()) {
        return emptyList()
    }

    val type = object : TypeToken<List<AppPromptMapping>>() {}.type
    return try {
        val decoded: List<AppPromptMapping>? = gson.fromJson(raw, type)
        sanitizeAppPromptMappings(decoded ?: emptyList())
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun sanitizeAppPromptMappings(mappings: List<AppPromptMapping>): List<AppPromptMapping> {
    return mappings.mapNotNull { mapping ->
        val packageName = (mapping.packageName as String?).orEmpty().trim()
        if (packageName.isBlank()) {
            return@mapNotNull null
        }

        val profileId = mapping.profileId?.trim()?.takeIf { it.isNotBlank() }
        val appendMode = (mapping.appendMode as AppendMode?) ?: AppendMode.APPEND
        val appPromptAppend = mapping.appPromptAppend?.trim()?.takeIf { it.isNotBlank() }
        val sttOverride = mapping.sttOverride?.let {
            ProviderModelOverride(
                providerId = (it.providerId as String?).orEmpty().trim(),
                modelId = (it.modelId as String?).orEmpty().trim(),
            )
        }
        val textOverride = mapping.textOverride?.let {
            ProviderModelOverride(
                providerId = (it.providerId as String?).orEmpty().trim(),
                modelId = (it.modelId as String?).orEmpty().trim(),
            )
        }

        AppPromptMapping(
            packageName = packageName,
            profileId = profileId,
            appendMode = appendMode,
            appPromptAppend = appPromptAppend,
            sttOverride = sttOverride,
            textOverride = textOverride,
            autoSend = mapping.autoSend,
        )
    }
}

internal fun extractProviderArray(rootElement: JsonElement): JsonArray? {
    if (rootElement.isJsonArray) {
        return rootElement.asJsonArray
    }
    if (!rootElement.isJsonObject) {
        return null
    }

    val rootObject = rootElement.asJsonObject
    rootObject.get("providers")?.let { providersElement ->
        if (providersElement.isJsonArray) {
            return providersElement.asJsonArray
        }
    }

    rootObject.entrySet().forEach { entry ->
        if (entry.value.isJsonArray) {
            return entry.value.asJsonArray
        }
    }
    return null
}

private fun rewriteEndpointToBaseUrlIfNeeded(endpoint: String): String {
    val trimmed = endpoint.trim()
    val parsed = trimmed.toHttpUrlOrNull() ?: return endpoint
    val segments = parsed.pathSegments.filter { it.isNotBlank() }

    val openAiBaseSegments = when {
        segments.size >= 2 && segments.takeLast(2) == listOf("audio", "transcriptions") -> segments.dropLast(2)
        segments.size >= 2 && segments.takeLast(2) == listOf("chat", "completions") -> segments.dropLast(2)
        else -> null
    }
    if (openAiBaseSegments != null && openAiBaseSegments.isNotEmpty()) {
        val basePath = "/" + openAiBaseSegments.joinToString("/")
        return parsed.newBuilder().encodedPath(basePath).query(null).fragment(null).build().toString()
    }

    if (trimmed.contains(":generateContent")) {
        val v1betaIndex = segments.indexOf("v1beta")
        if (v1betaIndex != -1) {
            val baseSegments = segments.take(v1betaIndex + 1)
            val basePath = "/" + baseSegments.joinToString("/")
            return parsed.newBuilder().encodedPath(basePath).query(null).fragment(null).build().toString()
        }
    }

    return endpoint
}

private fun inferModelKindFromLegacyEndpoint(providerType: ProviderType, legacyEndpoint: String): ModelKind {
    if (providerType == ProviderType.WHISPER_ASR) {
        return ModelKind.STT
    }
    if (providerType == ProviderType.GEMINI) {
        return ModelKind.MULTIMODAL
    }

    val parsed = legacyEndpoint.trim().toHttpUrlOrNull()
    val segments = parsed?.pathSegments?.filter { it.isNotBlank() }.orEmpty()
    return if (segments.size >= 2 && segments.takeLast(2) == listOf("audio", "transcriptions")) {
        ModelKind.STT
    } else {
        ModelKind.TEXT
    }
}

internal fun migrateProvidersJsonToSchemaV2(rawProvidersJson: String): String? {
    val rootElement = JsonParser.parseString(rawProvidersJson)
    val providerArray = extractProviderArray(rootElement) ?: return null
    var changed = false

    providerArray.forEach providerLoop@ { providerElement ->
        if (!providerElement.isJsonObject) {
            return@providerLoop
        }

        val providerObject = providerElement.asJsonObject
        val providerType = providerObject.get("type")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.let { raw -> runCatching { ProviderType.valueOf(raw) }.getOrNull() }
            ?: ProviderType.CUSTOM

        val legacyEndpoint = providerObject.get("endpoint")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            .orEmpty()

        if (legacyEndpoint.isNotBlank()) {
            val rewritten = rewriteEndpointToBaseUrlIfNeeded(legacyEndpoint)
            if (rewritten != legacyEndpoint) {
                providerObject.addProperty("endpoint", rewritten)
                changed = true
            }
        }

        val authModeElement = providerObject.get("authMode")
        val authModeRaw = authModeElement
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val hasValidAuthMode = authModeRaw != null && runCatching {
            ProviderAuthMode.valueOf(authModeRaw)
        }.isSuccess
        if (!hasValidAuthMode) {
            val defaultAuthMode = if (providerType == ProviderType.WHISPER_ASR) {
                ProviderAuthMode.NO_AUTH
            } else {
                ProviderAuthMode.API_KEY
            }
            providerObject.addProperty("authMode", defaultAuthMode.name)
            changed = true
        }

        providerObject.get("models")?.let { modelsElement ->
            if (modelsElement.isJsonArray) {
                val modelsArray = modelsElement.asJsonArray
                modelsArray.forEach modelLoop@ { modelElement ->
                    if (!modelElement.isJsonObject) {
                        return@modelLoop
                    }
                    val modelObject = modelElement.asJsonObject

                    val kindElement = modelObject.get("kind")
                    val kindRaw = kindElement
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    val hasValidKind = kindRaw != null && runCatching {
                        ModelKind.valueOf(kindRaw)
                    }.isSuccess
                    if (!hasValidKind) {
                        val inferredKind = inferModelKindFromLegacyEndpoint(providerType, legacyEndpoint)
                        modelObject.addProperty("kind", inferredKind.name)
                        changed = true
                    }

                    val streamingElement = modelObject.get("streamingPartialsSupported")
                    val hasValidStreamingFlag = streamingElement?.let {
                        it.isJsonPrimitive && it.asJsonPrimitive.isBoolean
                    } == true
                    if (!hasValidStreamingFlag) {
                        modelObject.addProperty("streamingPartialsSupported", false)
                        changed = true
                    }
                }
            }
        }
    }

    return if (changed) {
        Gson().toJson(rootElement)
    } else {
        null
    }
}

/**
 * Gson leaves fields that are missing from older JSON as null, so every value is re-checked here.
 * Models stored before schema v3 have no `capabilities`; derive them from the legacy fields.
 */
@Suppress("DEPRECATION", "USELESS_CAST", "SENSELESS_COMPARISON")
private fun sanitizeCapabilities(model: ModelConfig, providerType: ProviderType, endpoint: String): ModelCapabilities {
    (model.capabilities as? ModelCapabilities)?.let { return it }
    return ModelCapabilities.fromLegacy(
        kind = (model.kind as? ModelKind) ?: inferModelKindFromLegacyEndpoint(providerType, endpoint),
        isThinking = (model.isThinking as? Boolean) ?: false,
        streamingPartialsSupported = (model.streamingPartialsSupported as? Boolean) ?: false,
        providerType = providerType,
        modelId = model.id,
    )
}

/**
 * Schema v3: adds `capabilities` to every model that lacks it (from `kind`, `isThinking`,
 * `streamingPartialsSupported`, and the Gemini model id) and rewrites those legacy fields as
 * mirrors of the result.
 * Returns null when nothing changed.
 */
internal fun migrateProvidersJsonToSchemaV3(rawProvidersJson: String): String? {
    val rootElement = JsonParser.parseString(rawProvidersJson)
    val providerArray = extractProviderArray(rootElement) ?: return null
    val gson = Gson()
    var changed = false

    providerArray.forEach providerLoop@ { providerElement ->
        if (!providerElement.isJsonObject) {
            return@providerLoop
        }
        val providerObject = providerElement.asJsonObject
        val providerType = providerObject.stringOrNull("type")
            ?.let { raw -> runCatching { ProviderType.valueOf(raw) }.getOrNull() }
            ?: ProviderType.CUSTOM
        val endpoint = providerObject.stringOrNull("endpoint").orEmpty()
        val modelsElement = providerObject.get("models")
        if (modelsElement == null || !modelsElement.isJsonArray) {
            return@providerLoop
        }
        modelsElement.asJsonArray.forEach modelLoop@ { modelElement ->
            if (!modelElement.isJsonObject) {
                return@modelLoop
            }
            val modelObject = modelElement.asJsonObject
            if (modelObject.get("capabilities")?.isJsonObject == true) {
                return@modelLoop
            }
            val kind = modelObject.stringOrNull("kind")
                ?.let { raw -> runCatching { ModelKind.valueOf(raw) }.getOrNull() }
                ?: inferModelKindFromLegacyEndpoint(providerType, endpoint)
            val capabilities = ModelCapabilities.fromLegacy(
                kind = kind,
                isThinking = modelObject.booleanOrNull("isThinking") ?: false,
                streamingPartialsSupported = modelObject.booleanOrNull("streamingPartialsSupported") ?: false,
                providerType = providerType,
                modelId = modelObject.stringOrNull("id").orEmpty(),
            )
            modelObject.add("capabilities", gson.toJsonTree(capabilities))
            modelObject.addProperty("kind", capabilities.legacyKind.name)
            modelObject.addProperty("isThinking", capabilities.thinking)
            modelObject.addProperty("streamingPartialsSupported", capabilities.realtime)
            changed = true
        }
    }

    return if (changed) gson.toJson(rootElement) else null
}

private fun JsonObject.stringOrNull(key: String): String? = get(key)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString
    ?.trim()
    ?.takeIf { it.isNotBlank() }

private fun JsonObject.booleanOrNull(key: String): Boolean? = get(key)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
    ?.asBoolean
