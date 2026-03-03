package com.github.shekohex.whisperpp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.shekohex.whisperpp.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.MODEL
import com.github.shekohex.whisperpp.SMART_FIX_BACKEND
import com.github.shekohex.whisperpp.SMART_FIX_MODEL
import com.github.shekohex.whisperpp.SPEECH_TO_TEXT_BACKEND
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val PROVIDERS_JSON = stringPreferencesKey("providers_json")
val PROFILES_JSON = stringPreferencesKey("profiles_json")
val PROVIDER_API_KEY_MIGRATION_DONE = booleanPreferencesKey("provider_api_key_migration_done")
val PROVIDER_SCHEMA_V2_MIGRATION_DONE = booleanPreferencesKey("provider_schema_v2_migration_done")
val PROVIDER_SELECTIONS_V2_MIGRATION_DONE = booleanPreferencesKey("provider_selections_v2_migration_done")

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
            "speech-to-text-backend",
            "endpoint",
            "language-code",
            "model",
            "postprocessing",
            "prompt",
            "smart-fix-backend",
            "smart-fix-endpoint",
            "smart-fix-model",
            "smart-fix-prompt",
            "update-channel"
        )
        private val EXPORTABLE_BOOLEAN_KEYS = listOf(
            "is-auto-recording-start",
            "auto-switch-back",
            "auto-transcribe-on-pause",
            "cancel-confirmation",
            "add-trailing-space",
            "use-context",
            "smart-fix-enabled",
            "haptic-feedback-enabled",
            "sound-effects-enabled"
        )
        private val EXPORTABLE_INT_KEYS = listOf("timeout")
        private val EXPORTABLE_FLOAT_KEYS = listOf("smart-fix-temperature")
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
                        val inferredKind = (model.kind as? ModelKind)
                            ?: inferModelKindFromLegacyEndpoint(safeType, safeEndpoint)
                        model.copy(
                            id = (model.id as? String) ?: "",
                            name = (model.name as? String) ?: "",
                            isThinking = (model.isThinking as? Boolean) ?: false,
                            kind = inferredKind,
                            streamingPartialsSupported = (model.streamingPartialsSupported as? Boolean) ?: false,
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
    
    suspend fun saveProviders(providers: List<ServiceProvider>) {
        dataStore.edit { prefs ->
            prefs[PROVIDERS_JSON] = gson.toJson(providers)
        }
    }

    suspend fun migrateProviderApiKeysIfNeeded(context: Context) {
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
        val secretsStore = SecretsStore(context.applicationContext)

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

    suspend fun migrateProviderSchemaV2IfNeeded(context: Context) {
        val migrationDone = dataStore.data.map { prefs ->
            prefs[PROVIDER_SCHEMA_V2_MIGRATION_DONE] ?: false
        }.first()

        if (migrationDone) {
            return
        }

        migrateProviderApiKeysIfNeeded(context)

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

    suspend fun exportSettings(appVersionName: String): String {
        val prefs = dataStore.data.first()
        val providersList = providers.first()
        val profilesList = profiles.first()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        val prefsMap = mutableMapOf<String, Any?>()
        EXPORTABLE_STRING_KEYS.forEach { key ->
            prefs[stringPreferencesKey(key)]?.let { prefsMap[key] = it }
        }
        EXPORTABLE_BOOLEAN_KEYS.forEach { key ->
            prefs[booleanPreferencesKey(key)]?.let { prefsMap[key] = it }
        }
        EXPORTABLE_INT_KEYS.forEach { key ->
            prefs[intPreferencesKey(key)]?.let { prefsMap[key] = it }
        }
        EXPORTABLE_FLOAT_KEYS.forEach { key ->
            prefs[floatPreferencesKey(key)]?.let { prefsMap[key] = it }
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
                    prefsObj.get(key)?.asString?.let { prefs[stringPreferencesKey(key)] = it }
                }
                EXPORTABLE_BOOLEAN_KEYS.forEach { key ->
                    prefsObj.get(key)?.asBoolean?.let { prefs[booleanPreferencesKey(key)] = it }
                }
                EXPORTABLE_INT_KEYS.forEach { key ->
                    prefsObj.get(key)?.asInt?.let { prefs[intPreferencesKey(key)] = it }
                }
                EXPORTABLE_FLOAT_KEYS.forEach { key ->
                    prefsObj.get(key)?.asFloat?.let { prefs[floatPreferencesKey(key)] = it }
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
    val legacyTextProviderId = prefs[SMART_FIX_BACKEND]?.trim().orEmpty()
    val legacyTextModelId = prefs[SMART_FIX_MODEL]?.trim().orEmpty()

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
