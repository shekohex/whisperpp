package com.github.shekohex.whisperpp.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudProviderTypesTest {
    private val backup = """
        {
          "providersModels": {
            "providers": [
              {"id": "dg", "name": "Deepgram", "type": "DEEPGRAM", "endpoint": "https://api.deepgram.com/v1", "authMode": "API_KEY",
               "models": [{"id": "nova-3", "name": "Nova 3", "capabilities": {"stt": true, "realtime": true, "vocabulary": true}}]},
              {"id": "el", "name": "ElevenLabs", "type": "ELEVENLABS", "endpoint": "https://api.elevenlabs.io/v1",
               "models": [{"id": "scribe_v2", "name": "Scribe", "kind": "STT"}]},
              {"id": "future", "name": "Future", "type": "SOME_FUTURE_PROVIDER", "endpoint": "https://example.com/v1",
               "models": [{"id": "m", "name": "M", "kind": "STT"}]}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun backupParsing_readsCloudTypes_andUnknownTypesStillLoadAsCustom() {
        val skipped = mutableListOf<SkippedImportItem>()
        val providers = BackupProviderParser.parseProvidersModelsCategory(
            JsonParser.parseString(backup).asJsonObject,
            mutableSetOf(),
            skipped,
        ).providers

        assertEquals(listOf(ProviderType.DEEPGRAM, ProviderType.ELEVENLABS, ProviderType.CUSTOM), providers.map { it.type })
        assertEquals(ModelCapabilities(stt = true, realtime = true, vocabulary = true), providers[0].models.single().capabilities)
        // A model without capabilities falls back to the legacy mapping; cloud providers always stream.
        val legacy = providers[1].models.single().capabilities
        assertTrue(legacy.stt)
        assertTrue(legacy.realtime)
        assertTrue(legacy.vocabulary)
        assertEquals(ProviderAuthMode.API_KEY, providers[1].authMode)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun cloudProvider_roundTripsThroughGson() {
        val provider = Presets.Deepgram.copy(id = "dg")
        val gson = Gson()

        val decoded = gson.fromJson(gson.toJson(provider), ServiceProvider::class.java)

        assertEquals(ProviderType.DEEPGRAM, decoded.type)
        assertEquals(provider.models.map { it.id }, decoded.models.map { it.id })
        assertTrue(decoded.models.all { it.capabilities.realtime && it.capabilities.stt })
    }

    @Test
    fun displayNames_areBrandNames() {
        assertEquals("Deepgram", ProviderType.DEEPGRAM.toString())
        assertEquals("ElevenLabs", ProviderType.ELEVENLABS.toString())
        assertEquals("Mistral", ProviderType.MISTRAL.toString())
    }

    @Test
    fun sttCapabilities_streamAndAcceptVocabulary_whereTheProviderTakesKeyTerms() {
        assertEquals(ModelCapabilities(stt = true, realtime = true, vocabulary = true), ModelCapabilities.stt(ProviderType.DEEPGRAM))
        assertEquals(ModelCapabilities(stt = true, realtime = true, vocabulary = true), ModelCapabilities.stt(ProviderType.ELEVENLABS))
        assertEquals(ModelCapabilities(stt = true, realtime = true, vocabulary = false), ModelCapabilities.stt(ProviderType.MISTRAL))
        // Existing providers are unchanged.
        assertEquals(ModelCapabilities(stt = true, realtime = false, vocabulary = true), ModelCapabilities.stt(ProviderType.OPENAI))
        assertFalse(ModelCapabilities.stt(ProviderType.WHISPER_ASR).realtime)
    }

    @Test
    fun presets_offerEachCloudProviderWithAnSttModel() {
        for ((preset, type, model) in listOf(
            Triple(Presets.Deepgram, ProviderType.DEEPGRAM, "nova-3"),
            Triple(Presets.ElevenLabs, ProviderType.ELEVENLABS, "scribe_v2"),
            Triple(Presets.Mistral, ProviderType.MISTRAL, "voxtral-mini-latest"),
        )) {
            assertTrue(type.name, preset in Presets.AllPresets)
            assertEquals(type, preset.type)
            assertTrue(preset.endpoint.startsWith("https://"))
            val default = preset.models.first()
            assertEquals(model, default.id)
            assertTrue(default.capabilities.stt && default.capabilities.realtime)
            assertEquals(ProviderAuthMode.API_KEY, preset.authMode)
        }
    }
}
