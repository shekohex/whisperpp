package com.github.shekohex.whisperpp.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProviderSchemaV2MigrationTest {

    @Test
    fun openAiLegacyAudioEndpoint_rewritesToBaseUrl_andInjectsModelDefaults() {
        val raw = """
            [
              {
                "id": "p1",
                "name": "OpenAI Whisper",
                "type": "OPENAI",
                "endpoint": "https://api.openai.com/v1/audio/transcriptions",
                "models": [
                  {"id": "whisper-1", "name": "Whisper 1"}
                ]
              }
            ]
        """.trimIndent()

        val migrated = migrateProvidersJsonToSchemaV2(raw)
        assertNotNull(migrated)

        val root = JsonParser.parseString(migrated).asJsonArray
        val provider = root[0].asJsonObject
        assertEquals("p1", provider.get("id").asString)
        assertEquals("https://api.openai.com/v1", provider.get("endpoint").asString)
        assertEquals("API_KEY", provider.get("authMode").asString)

        val model = provider.getAsJsonArray("models")[0].asJsonObject
        assertEquals("STT", model.get("kind").asString)
        assertFalse(model.get("streamingPartialsSupported").asBoolean)
    }

    @Test
    fun openAiLegacyChatEndpoint_rewritesToBaseUrl_andDefaultsTextKind() {
        val raw = """
            [
              {
                "id": "p2",
                "name": "OpenAI Chat",
                "type": "OPENAI",
                "endpoint": "https://api.openai.com/v1/chat/completions",
                "models": [
                  {"id": "gpt-4o", "name": "GPT-4o"}
                ]
              }
            ]
        """.trimIndent()

        val migrated = migrateProvidersJsonToSchemaV2(raw)
        assertNotNull(migrated)

        val root = JsonParser.parseString(migrated).asJsonArray
        val provider = root[0].asJsonObject
        assertEquals("p2", provider.get("id").asString)
        assertEquals("https://api.openai.com/v1", provider.get("endpoint").asString)
        assertEquals("API_KEY", provider.get("authMode").asString)

        val model = provider.getAsJsonArray("models")[0].asJsonObject
        assertEquals("TEXT", model.get("kind").asString)
        assertFalse(model.get("streamingPartialsSupported").asBoolean)
    }

    @Test
    fun geminiLegacyGenerateContentEndpoint_rewritesToV1betaBaseUrl() {
        val raw = """
            [
              {
                "id": "p3",
                "name": "Gemini",
                "type": "GEMINI",
                "endpoint": "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent",
                "models": [
                  {"id": "gemini-1.5-pro", "name": "Gemini 1.5 Pro"}
                ]
              }
            ]
        """.trimIndent()

        val migrated = migrateProvidersJsonToSchemaV2(raw)
        assertNotNull(migrated)

        val root = JsonParser.parseString(migrated).asJsonArray
        val provider = root[0].asJsonObject
        assertEquals("p3", provider.get("id").asString)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", provider.get("endpoint").asString)
        assertEquals("API_KEY", provider.get("authMode").asString)

        val model = provider.getAsJsonArray("models")[0].asJsonObject
        assertEquals("MULTIMODAL", model.get("kind").asString)
        assertFalse(model.get("streamingPartialsSupported").asBoolean)
    }

    @Test
    fun whisperAsrDefaultsToNoAuth() {
        val raw = """
            [
              {
                "id": "p4",
                "name": "Whisper ASR",
                "type": "WHISPER_ASR",
                "endpoint": "http://localhost:9000/asr",
                "models": []
              }
            ]
        """.trimIndent()

        val migrated = migrateProvidersJsonToSchemaV2(raw)
        assertNotNull(migrated)

        val root = JsonParser.parseString(migrated).asJsonArray
        val provider = root[0].asJsonObject
        assertEquals("p4", provider.get("id").asString)
        assertEquals("NO_AUTH", provider.get("authMode").asString)
    }
}
