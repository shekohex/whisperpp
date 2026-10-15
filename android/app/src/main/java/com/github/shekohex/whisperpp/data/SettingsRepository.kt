package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.APP_PROMPT_MAPPINGS_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_PRESET_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_PRESET_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.GLOBAL_BASE_PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROFILES_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPTS_PROFILES_SCHEMA_V1_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPT_PROFILES_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDERS_JSON
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnSettings
import com.github.shekohex.whisperpp.data.modes.ModeRules
import com.github.shekohex.whisperpp.data.modes.ModeSeeds
import com.github.shekohex.whisperpp.data.modes.storedModes
import com.github.shekohex.whisperpp.data.modes.writeModes
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewSchedule
import com.github.shekohex.whisperpp.domain.providers.DiscoveredCache
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.domain.providers.DiscoveryIdentity
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val gson = Gson()
    private val providerMigrator = ProviderMigrator(dataStore, gson)

    companion object {
        const val EXPORT_VERSION = LegacySettingsImportExport.EXPORT_VERSION
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.ONBOARDING_COMPLETED] ?: false }
        .distinctUntilChanged()

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[PreferenceKeys.ONBOARDING_COMPLETED] = true }
    }

    /** Installs from before onboarding existed (a provider saved or the IME enabled) skip it; runs at startup. */
    suspend fun migrateOnboardingGateIfNeeded(isImeEnabled: () -> Boolean) {
        if (dataStore.data.first()[PreferenceKeys.ONBOARDING_COMPLETED] != null) return
        if (providers.first().isEmpty() && !isImeEnabled()) return
        dataStore.edit { prefs ->
            if (prefs[PreferenceKeys.ONBOARDING_COMPLETED] == null) prefs[PreferenceKeys.ONBOARDING_COMPLETED] = true
        }
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

    val commandModeEnabled: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.COMMAND_MODE_ENABLED] ?: false }
        .distinctUntilChanged()

    val showModeOnKeyboard: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.KEYBOARD_SHOW_MODE] ?: true }
        .distinctUntilChanged()

    val showLiveTranscript: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.KEYBOARD_SHOW_LIVE_TRANSCRIPT] ?: true }
        .distinctUntilChanged()

    val keepScreenAwake: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PreferenceKeys.KEEP_SCREEN_AWAKE] ?: true }
        .distinctUntilChanged()

    /** Transcript history (§4.8); see [PreferenceKeys.HISTORY_ENABLED]. */
    val historySettings: Flow<HistorySettings> = dataStore.data
        .map { prefs ->
            HistorySettings(
                enabled = prefs[PreferenceKeys.HISTORY_ENABLED] ?: true,
                keepAudio = prefs[PreferenceKeys.HISTORY_KEEP_AUDIO] ?: HistorySettings.DEFAULT_KEEP_AUDIO,
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

    suspend fun historyLastPruneAt(): Long = dataStore.data.first()[PreferenceKeys.HISTORY_LAST_PRUNE_AT] ?: 0L

    suspend fun setHistoryLastPruneAt(millis: Long) {
        dataStore.edit { it[PreferenceKeys.HISTORY_LAST_PRUNE_AT] = millis }
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
                            timeoutMs = (model.timeoutMs as? Number)?.toInt()?.takeIf { it > 0 },
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
        providerMigrator.migrateProviderApiKeysIfNeeded(secretsStore)
    }

    suspend fun migrateProviderSchemaV2IfNeeded(secretsStore: SecretsStore) {
        providerMigrator.migrateProviderSchemaV2IfNeeded(secretsStore)
    }

    /** Fills `capabilities` on every model from the legacy kind/flags (schema v3). */
    suspend fun migrateProviderSchemaV3IfNeeded() {
        providerMigrator.migrateProviderSchemaV3IfNeeded()
    }

    suspend fun migrateProviderSelectionsV2IfNeeded() {
        providerMigrator.migrateProviderSelectionsV2IfNeeded()
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
            if (prefs[PreferenceKeys.PROMPT_LIBRARY_SEEDED] != true) {
                val current = parsePromptProfilesJson(prefs[PROMPT_PROFILES_JSON], gson)
                prefs[PROMPT_PROFILES_JSON] = gson.toJson(sanitizePromptProfiles(PromptLibrary.seed(current)))
                prefs[PreferenceKeys.PROMPT_LIBRARY_SEEDED] = true
            }
            if (prefs[PreferenceKeys.VIBE_CODING_SEEDED] != true) {
                seedVibeCoding(prefs)
                prefs[PreferenceKeys.VIBE_CODING_SEEDED] = true
            }
        }
    }

    /**
     * Adds the Vibe Coding preset and mode once, atomically with its flag. Only the new preset is
     * seeded: old built-ins the user deleted stay deleted and existing ids are never overwritten.
     * The mode is only appended to already stored modes; otherwise [ModeSeeds.ALL] (or the legacy
     * migration) brings it in, so the seeding never decides what the default mode is.
     */
    private fun seedVibeCoding(prefs: MutablePreferences) {
        val profiles = parsePromptProfilesJson(prefs[PROMPT_PROFILES_JSON], gson)
        if (profiles.none { it.id == PromptLibrary.VIBE_CODING_ID }) {
            prefs[PROMPT_PROFILES_JSON] = gson.toJson(sanitizePromptProfiles(profiles + PromptLibrary.VIBE_CODING_PROFILE))
        }
        val modes = storedModes(prefs, gson) ?: return
        if (modes.none { it.id == ModeSeeds.VIBE_CODING_ID }) {
            writeModes(prefs, ModeRules.normalize(modes + ModeSeeds.VIBE_CODING), gson)
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

    private val legacyImportExport = LegacySettingsImportExport(dataStore, gson, providers, profiles)

    suspend fun exportSettings(appVersionName: String): String {
        return legacyImportExport.exportSettings(appVersionName)
    }

    suspend fun importSettings(json: String): ImportResult {
        return legacyImportExport.importSettings(json)
    }
}
