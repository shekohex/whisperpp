package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDERS_JSON
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDER_API_KEY_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDER_SCHEMA_V2_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDER_SCHEMA_V3_MIGRATION_DONE
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROVIDER_SELECTIONS_V2_MIGRATION_DONE
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One-shot provider/selection storage migrations; each is guarded by its own DataStore flag. */
internal class ProviderMigrator(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson,
) {
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
}
