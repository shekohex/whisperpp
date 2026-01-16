package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val PROVIDERS_JSON = stringPreferencesKey("providers_json")
val PROFILES_JSON = stringPreferencesKey("profiles_json")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val gson = Gson()
    
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
    

}
