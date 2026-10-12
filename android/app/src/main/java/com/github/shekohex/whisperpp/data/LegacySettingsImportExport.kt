package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROFILES_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDERS_JSON
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

/** Unencrypted legacy JSON settings export/import (providers, language profiles and a few preferences). */
internal class LegacySettingsImportExport(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson,
    private val providers: Flow<List<ServiceProvider>>,
    private val profiles: Flow<List<LanguageProfile>>,
) {
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
