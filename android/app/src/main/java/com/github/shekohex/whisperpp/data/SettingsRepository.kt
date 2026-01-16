package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val PROVIDERS_JSON = stringPreferencesKey("providers_json")
val PROFILES_JSON = stringPreferencesKey("profiles_json")

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
            "api-key",
            "model",
            "postprocessing",
            "prompt",
            "smart-fix-backend",
            "smart-fix-endpoint",
            "smart-fix-api-key",
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
                    provider.copy(
                        models = (provider.models as? List<ModelConfig>) ?: emptyList(),
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
