package com.github.shekohex.whisperpp.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProviderSchemaV3MigrationTest {

    private val v2Json = """
        [
          {
            "id": "openai", "name": "OpenAI", "type": "OPENAI", "endpoint": "https://api.openai.com/v1", "authMode": "API_KEY",
            "models": [
              {"id": "whisper-1", "name": "Whisper", "kind": "STT", "isThinking": false, "streamingPartialsSupported": true},
              {"id": "o4-mini", "name": "o4", "kind": "TEXT", "isThinking": true, "streamingPartialsSupported": false}
            ]
          },
          {
            "id": "gemini", "name": "Gemini", "type": "GEMINI", "endpoint": "https://generativelanguage.googleapis.com/v1beta", "authMode": "API_KEY",
            "models": [
              {"id": "gemini-2.0-flash", "name": "Flash", "kind": "MULTIMODAL", "isThinking": false, "streamingPartialsSupported": false}
            ]
          },
          {
            "id": "asr", "name": "ASR", "type": "WHISPER_ASR", "endpoint": "http://192.168.1.2:9000/asr", "authMode": "NO_AUTH",
            "models": [ {"id": "large", "name": "Large"} ]
          }
        ]
    """.trimIndent()

    @Test
    fun legacyFlags_mapToCapabilities() {
        val migrated = JsonParser.parseString(migrateProvidersJsonToSchemaV3(v2Json)).asJsonArray

        val whisper = migrated[0].model(0)
        assertEquals(capabilities(stt = true, realtime = true, vocabulary = true), whisper.capabilities())
        assertEquals("STT", whisper.get("kind").asString)

        val thinking = migrated[0].model(1)
        assertEquals(capabilities(text = true, thinking = true), thinking.capabilities())

        val asr = migrated[2].model(0)
        assertEquals(capabilities(stt = true), asr.capabilities())
    }

    @Test
    fun multimodal_becomesTextOnly_andLegacyKindIsRewritten() {
        val migrated = JsonParser.parseString(migrateProvidersJsonToSchemaV3(v2Json)).asJsonArray

        val flash = migrated[1].model(0)
        assertEquals(capabilities(text = true), flash.capabilities())
        assertEquals("TEXT", flash.get("kind").asString)
    }

    @Test
    fun alreadyMigrated_isLeftUntouched() {
        val once = migrateProvidersJsonToSchemaV3(v2Json)!!

        assertNull(migrateProvidersJsonToSchemaV3(once))
    }

    @Test
    fun migrateIfNeeded_persistsCapabilities_andRunsOnce() = runBlocking {
        val file = File.createTempFile("settings-v3", ".preferences_pb").apply { delete(); deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        dataStore.edit { it[PreferenceKeys.PROVIDERS_JSON] = v2Json }
        val repository = SettingsRepository(dataStore)

        repository.migrateProviderSchemaV3IfNeeded()

        val prefs = dataStore.data.first()
        assertTrue(prefs[PreferenceKeys.PROVIDER_SCHEMA_V3_MIGRATION_DONE] == true)
        assertTrue(prefs[PreferenceKeys.PROVIDERS_JSON]!!.contains("\"capabilities\""))
        val flash = repository.providers.first().single { it.id == "gemini" }.models.single()
        assertFalse(flash.capabilities.stt)
        assertTrue(flash.capabilities.text)

        dataStore.edit { it[PreferenceKeys.PROVIDERS_JSON] = v2Json }
        repository.migrateProviderSchemaV3IfNeeded()
        assertEquals(v2Json, dataStore.data.first()[PreferenceKeys.PROVIDERS_JSON])
    }

    @Test
    fun providersFlow_derivesCapabilitiesFromLegacyJsonBeforeMigration() = runBlocking {
        val file = File.createTempFile("settings-v3-flow", ".preferences_pb").apply { delete(); deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        dataStore.edit { it[PreferenceKeys.PROVIDERS_JSON] = v2Json }

        val providers = SettingsRepository(dataStore).providers.first()

        val whisper = providers.single { it.id == "openai" }.models.first()
        assertEquals(ModelCapabilities(stt = true, realtime = true, vocabulary = true), whisper.capabilities)
    }

    private fun com.google.gson.JsonElement.model(index: Int): JsonObject =
        asJsonObject.getAsJsonArray("models")[index].asJsonObject

    private fun JsonObject.capabilities(): Map<String, Boolean> =
        getAsJsonObject("capabilities").entrySet().associate { it.key to it.value.asBoolean }

    private fun capabilities(
        stt: Boolean = false,
        text: Boolean = false,
        realtime: Boolean = false,
        thinking: Boolean = false,
        vocabulary: Boolean = false,
    ) = mapOf("stt" to stt, "text" to text, "realtime" to realtime, "thinking" to thinking, "vocabulary" to vocabulary)
}
