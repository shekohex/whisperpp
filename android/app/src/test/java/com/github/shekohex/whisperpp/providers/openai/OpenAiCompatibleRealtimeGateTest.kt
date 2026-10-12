package com.github.shekohex.whisperpp.providers.openai

import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.ModelClassifier
import com.github.shekohex.whisperpp.providers.realtime.OpenAiRealtimeSession
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which OpenAI-compatible models stream: the explicit flag for gateways, auto for OpenAI's own. */
class OpenAiCompatibleRealtimeGateTest {
    private fun transcription(type: ProviderType, modelId: String, realtime: Boolean) = OpenAiCompatibleTranscription(
        client = OkHttpClient(),
        provider = ServiceProvider(id = "p", name = "P", type = type, endpoint = "https://litellm.example.com", models = emptyList()),
        model = ModelConfig(modelId, modelId, ModelCapabilities(stt = true, realtime = realtime)),
        credentials = Credentials("sk-test-key"),
    )

    private fun request(type: ProviderType, modelId: String) = TranscriptionRequest(
        provider = ServiceProvider(name = "P", type = type, endpoint = "https://litellm.example.com", models = emptyList()),
        modelId = modelId,
    )

    @Test
    fun customGateway_streamsOnlyWhenTheModelIsFlagged() {
        val flagged = transcription(ProviderType.CUSTOM, "whisper-large-v3", realtime = true)
        assertTrue(flagged.capabilities.realtime)
        assertTrue(flagged.openStream(request(ProviderType.CUSTOM, "whisper-large-v3")) is OpenAiRealtimeSession)

        val unflagged = transcription(ProviderType.CUSTOM, "gpt-4o-transcribe", realtime = false)
        assertFalse(unflagged.capabilities.realtime)
        assertNull(unflagged.openStream(request(ProviderType.CUSTOM, "gpt-4o-transcribe")))
    }

    @Test
    fun openAi_ownTranscribeModelsStreamWithoutTheFlag() {
        for (id in listOf("whisper-1", "gpt-4o-transcribe", "gpt-4o-mini-transcribe")) {
            val provider = transcription(ProviderType.OPENAI, id, realtime = false)
            assertTrue(id, provider.capabilities.realtime)
            assertNotNull(id, provider.openStream(request(ProviderType.OPENAI, id)))
        }
        assertFalse(transcription(ProviderType.OPENAI, "some-future-stt", realtime = false).capabilities.realtime)
    }

    @Test
    fun whisperAsrWebservice_neverStreams_evenWhenFlagged() {
        val provider = transcription(ProviderType.WHISPER_ASR, "base", realtime = true)
        assertNull(provider.openStream(request(ProviderType.WHISPER_ASR, "base")))
    }

    @Test
    fun realtimeFlag_isOfferedForOpenAiAndGatewaysOnly() {
        assertEquals(
            setOf(ProviderType.OPENAI, ProviderType.CUSTOM),
            ProviderType.entries.filter { ModelClassifier.acceptsRealtimeFlag(it) }.toSet(),
        )
    }
}
