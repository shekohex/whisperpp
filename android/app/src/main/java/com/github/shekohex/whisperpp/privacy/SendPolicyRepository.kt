package com.github.shekohex.whisperpp.privacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.PER_APP_SEND_POLICY_JSON
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class SendPolicyRepository(private val dataStore: DataStore<Preferences>) {
    fun isBlockedFlow(packageName: String?): Flow<Boolean> {
        if (packageName.isNullOrBlank()) {
            return dataStore.data.map { false }
        }
        return getAllRulesFlow().map { rules ->
            rules[packageName.trim()] == true
        }
    }

    suspend fun setBlocked(packageName: String, blocked: Boolean) {
        val normalizedPackage = packageName.trim()
        if (normalizedPackage.isEmpty()) {
            return
        }
        dataStore.edit { prefs ->
            val updatedRules = decodeRules(prefs[PER_APP_SEND_POLICY_JSON]).toMutableMap()
            if (blocked) {
                updatedRules[normalizedPackage] = true
            } else {
                updatedRules.remove(normalizedPackage)
            }
            prefs[PER_APP_SEND_POLICY_JSON] = encodeRules(updatedRules)
        }
    }

    fun getAllRulesFlow(): Flow<Map<String, Boolean>> {
        return dataStore.data.map { prefs ->
            decodeRules(prefs[PER_APP_SEND_POLICY_JSON])
        }
    }

    private fun decodeRules(serialized: String?): Map<String, Boolean> {
        if (serialized.isNullOrBlank()) {
            return emptyMap()
        }
        return try {
            val json = JSONObject(serialized)
            val result = mutableMapOf<String, Boolean>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next().trim()
                if (key.isNotEmpty()) {
                    result[key] = json.optBoolean(key, false)
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeRules(rules: Map<String, Boolean>): String {
        val json = JSONObject()
        rules.forEach { (packageName, blocked) ->
            json.put(packageName, blocked)
        }
        return json.toString()
    }
}
