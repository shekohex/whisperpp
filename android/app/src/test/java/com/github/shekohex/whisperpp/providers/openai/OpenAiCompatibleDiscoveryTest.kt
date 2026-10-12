package com.github.shekohex.whisperpp.providers.openai

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.providers.baseUrl
import com.github.shekohex.whisperpp.providers.gemini.GeminiDiscovery
import com.github.shekohex.whisperpp.providers.startLoopbackServer
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleDiscoveryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = startLoopbackServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listModels_getsModelsWithBearer_andClassifiesDataArray() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"object":"list","data":[
                    {"id":"whisper-1","object":"model","owned_by":"openai-internal","created":1677532384},
                    {"id":"gpt-4o-transcribe","object":"model","owned_by":"system"},
                    {"id":"gpt-4o-realtime-preview","object":"model"},
                    {"id":"gpt-4o","object":"model"},
                    {"id":"tts-1","object":"model"},
                    {"id":"text-embedding-3-large","object":"model"}
                ]}"""
            )
        )

        val models = discovery().listModels().getOrThrow()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        // The realtime conversation model is not a transcription model and is not listed.
        assertEquals(listOf("whisper-1", "gpt-4o-transcribe", "gpt-4o"), models.map { it.id })
        assertTrue(models[0].capabilities.stt)
        assertEquals("openai-internal", models[0].raw["owned_by"])
        assertTrue(models[1].capabilities.realtime)
        assertTrue(models[2].capabilities.text)
    }

    @Test
    fun listModels_acceptsBareArrays_andNoAuth() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"llama-3.1-8b"},"qwen2.5-7b",{"id":"llama-3.1-8b"}]"""))
        val provider = provider().copy(authMode = ProviderAuthMode.NO_AUTH)

        val models = OpenAiCompatibleDiscovery(OkHttpClient(), provider, Credentials.NONE).listModels().getOrThrow()

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals(listOf("llama-3.1-8b", "qwen2.5-7b"), models.map { it.id })
    }

    @Test
    fun listModels_401_isUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Incorrect API key sk-test"}}"""))

        val error = discovery().listModels().exceptionOrNull()

        assertTrue(error is NetworkError.Unauthorized)
        assertEquals(401, (error as NetworkError.Unauthorized).status)
    }

    @Test
    fun listModels_malformedBody_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"unexpected":true}"""))

        assertTrue(discovery().listModels().exceptionOrNull() is NetworkError.InvalidResponse)
    }

    @Test
    fun modelsUrl_derivesFromBaseOrPastedEndpoint() {
        fun url(base: String) = OpenAiCompatibleDiscovery.modelsUrl(base.toHttpUrl()).toString()

        assertEquals("https://api.openai.com/v1/models", url("https://api.openai.com/v1"))
        assertEquals("https://api.openai.com/v1/models", url("https://api.openai.com/v1/"))
        assertEquals("https://api.groq.com/openai/v1/models", url("https://api.groq.com/openai/v1/chat/completions"))
        assertEquals("https://h/v1/models", url("https://h/v1/audio/transcriptions"))
        assertEquals("https://h/v1/models", url("https://h/v1/models"))
    }

    @Test
    fun factory_routesDiscoveryByProviderType() {
        val factory = ProviderFactory(OkHttpClient()) { "key" }

        assertTrue(factory.discovery(provider()) is OpenAiCompatibleDiscovery)
        assertTrue(factory.discovery(provider().copy(type = ProviderType.CUSTOM)) is OpenAiCompatibleDiscovery)
        assertTrue(factory.discovery(provider().copy(type = ProviderType.GEMINI)) is GeminiDiscovery)
        assertTrue(
            factory.discovery(provider().copy(type = ProviderType.WHISPER_ASR, endpoint = "http://10.0.0.2:8000/v1"))
                is OpenAiCompatibleDiscovery
        )
        assertNull(factory.discovery(provider().copy(type = ProviderType.WHISPER_ASR, endpoint = "http://10.0.0.2:9000/asr")))
    }

    private fun discovery() = OpenAiCompatibleDiscovery(OkHttpClient(), provider(), Credentials("sk-test"))

    private fun provider() = ServiceProvider(
        id = "p",
        name = "P",
        type = ProviderType.OPENAI,
        endpoint = server.baseUrl(),
        models = emptyList(),
    )
}
