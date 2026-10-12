package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.TranscriptionProvider
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.baseUrl
import com.github.shekohex.whisperpp.providers.startLoopbackServer
import com.github.shekohex.whisperpp.providers.tempAudioFile
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Batch fallbacks, capabilities and factory routing of the Deepgram, ElevenLabs and Mistral providers. */
class CloudRealtimeTranscriptionTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = startLoopbackServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun provider(type: ProviderType, modelId: String) = ServiceProvider(
        id = "p1",
        name = type.toString(),
        type = type,
        endpoint = server.baseUrl("/v1"),
        models = listOf(model(type, modelId)),
    )

    private fun model(type: ProviderType, modelId: String) =
        ModelConfig(modelId, modelId, ModelCapabilities(stt = true, vocabulary = true))

    private fun transcription(type: ProviderType, modelId: String, key: String? = "test-key"): TranscriptionProvider {
        val factory = ProviderFactory(client) { key }
        return factory.transcription(provider(type, modelId), modelId).getOrThrow()
    }

    private fun request(
        type: ProviderType,
        modelId: String,
        language: String = "auto",
        vocabulary: List<String> = emptyList(),
    ) = TranscriptionRequest(
        provider = provider(type, modelId),
        modelId = modelId,
        languageCode = language,
        timeoutMs = 5_000,
        vocabulary = vocabulary,
    )

    @Test
    fun cloudProviders_alwaysStream_withoutAPerModelFlag() {
        for ((type, id) in listOf(
            ProviderType.DEEPGRAM to "nova-3",
            ProviderType.ELEVENLABS to "scribe_v2",
            ProviderType.MISTRAL to "voxtral-mini-latest",
        )) {
            val provider = transcription(type, id)
            assertTrue(type.name, provider.capabilities.realtime)
            assertTrue(type.name, provider.capabilities.stt)
            assertFalse("batch exists, so the setting is honoured", provider.streamingOnly)
            assertNotNull(type.name, provider.openStream(request(type, id)))
        }
    }

    @Test
    fun missingKey_failsTheBatchCall_andTheStreamStart() = runBlocking {
        val provider = transcription(ProviderType.DEEPGRAM, "nova-3", key = null)

        val batch = provider.transcribe(tempAudioFile(), request(ProviderType.DEEPGRAM, "nova-3")).exceptionOrNull()
        assertEquals(
            ProviderConfigException.Problem.MISSING_API_KEY,
            (batch as ProviderConfigException).problem,
        )
        val stream = provider.openStream(request(ProviderType.DEEPGRAM, "nova-3"))!!
        try {
            stream.start()
            fail("expected missing key")
        } catch (e: ProviderConfigException) {
            assertEquals(ProviderConfigException.Problem.MISSING_API_KEY, e.problem)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun deepgramBatch_postsRawWav_withTokenAuth_andParsesTranscript() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"results":{"channels":[{"alternatives":[{"transcript":" hello world ","confidence":0.9}]}]}}""",
            ),
        )
        val audio = tempAudioFile()

        val result = transcription(ProviderType.DEEPGRAM, "nova-3")
            .transcribe(audio, request(ProviderType.DEEPGRAM, "nova-3", language = "ar", vocabulary = listOf("Kotlin")))

        assertEquals("hello world", result.getOrThrow().text)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/listen", recorded.requestUrl?.encodedPath)
        assertEquals("nova-3", recorded.requestUrl?.queryParameter("model"))
        assertEquals("true", recorded.requestUrl?.queryParameter("smart_format"))
        assertEquals("ar", recorded.requestUrl?.queryParameter("language"))
        assertEquals(listOf("Kotlin"), recorded.requestUrl?.queryParameterValues("keyterm"))
        assertEquals("Token test-key", recorded.getHeader("Authorization"))
        assertEquals("audio/wav", recorded.getHeader("Content-Type"))
        assertEquals(audio.readBytes().toList(), recorded.body.readByteArray().toList())
        assertTrue("providers never delete their input", audio.exists())
    }

    @Test
    fun deepgramBatch_autoLanguage_isNotSent_andBadJsonIsInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":{"channels":[]}}"""))

        val error = transcription(ProviderType.DEEPGRAM, "nova-3")
            .transcribe(tempAudioFile(), request(ProviderType.DEEPGRAM, "nova-3"))
            .exceptionOrNull()

        assertTrue(error is NetworkError.InvalidResponse)
        assertNull(server.takeRequest().requestUrl?.queryParameter("language"))
    }

    @Test
    fun elevenLabsBatch_postsMultipartWithModelId_andParsesText() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"text":" salam ","language_code":"ar"}"""))

        val result = transcription(ProviderType.ELEVENLABS, "scribe_v2")
            .transcribe(tempAudioFile(), request(ProviderType.ELEVENLABS, "scribe_v2", language = "ar"))

        assertEquals("salam", result.getOrThrow().text)
        val recorded = server.takeRequest()
        assertEquals("/v1/speech-to-text", recorded.requestUrl?.encodedPath)
        assertEquals("test-key", recorded.getHeader("xi-api-key"))
        assertNull(recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"audio.wav\""))
        assertTrue(body.contains("name=\"model_id\"\r\n\r\nscribe_v2\r\n"))
        assertTrue(body.contains("name=\"language_code\"\r\n\r\nar\r\n"))
    }

    @Test
    fun mistralBatch_postsOpenAiStyleMultipart_withBearerAuth() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"text":" bonjour ","model":"voxtral-mini-latest"}"""))

        val result = transcription(ProviderType.MISTRAL, "voxtral-mini-latest")
            .transcribe(tempAudioFile(), request(ProviderType.MISTRAL, "voxtral-mini-latest"))

        assertEquals("bonjour", result.getOrThrow().text)
        val recorded = server.takeRequest()
        assertEquals("/v1/audio/transcriptions", recorded.requestUrl?.encodedPath)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"model\"\r\n\r\nvoxtral-mini-latest\r\n"))
        assertFalse(body.contains("name=\"language\""))
        assertFalse("Mistral rejects unknown fields", body.contains("name=\"prompt\"") || body.contains("name=\"response_format\""))
    }

    @Test
    fun batchErrors_mapToTypedNetworkErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val provider = transcription(ProviderType.ELEVENLABS, "scribe_v2")
        val req = request(ProviderType.ELEVENLABS, "scribe_v2")

        assertTrue(provider.transcribe(tempAudioFile(), req).exceptionOrNull() is NetworkError.Unauthorized)
        assertEquals(3_000L, (provider.transcribe(tempAudioFile(), req).exceptionOrNull() as NetworkError.RateLimited).retryAfterMs)
        assertEquals(500, (provider.transcribe(tempAudioFile(), req).exceptionOrNull() as NetworkError.Http).status)
    }

    @Test
    fun verify_usesEachProvidersAuthHeader() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("{}")) }

        transcription(ProviderType.DEEPGRAM, "nova-3").verify().getOrThrow()
        transcription(ProviderType.ELEVENLABS, "scribe_v2").verify().getOrThrow()
        transcription(ProviderType.MISTRAL, "voxtral-mini-latest").verify().getOrThrow()

        val deepgram = server.takeRequest()
        assertEquals("/v1/projects", deepgram.requestUrl?.encodedPath)
        assertEquals("Token test-key", deepgram.getHeader("Authorization"))
        val eleven = server.takeRequest()
        assertEquals("/v1/models", eleven.requestUrl?.encodedPath)
        assertEquals("test-key", eleven.getHeader("xi-api-key"))
        val mistral = server.takeRequest()
        assertEquals("/v1/models", mistral.requestUrl?.encodedPath)
        assertEquals("Bearer test-key", mistral.getHeader("Authorization"))
    }

    @Test
    fun verify_mapsUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))

        val error = transcription(ProviderType.DEEPGRAM, "nova-3").verify().exceptionOrNull()

        assertTrue(error is NetworkError.Unauthorized)
    }

    @Test
    fun factory_hasNoDiscovery_andNoEnhancement_forCloudProviders() {
        val factory = ProviderFactory(client) { "k" }
        for (type in listOf(ProviderType.DEEPGRAM, ProviderType.ELEVENLABS, ProviderType.MISTRAL)) {
            assertNull(type.name, factory.discovery(provider(type, "m")))
            val model = ModelConfig("m", "m", ModelCapabilities.TEXT)
            val error = factory.enhancement(provider(type, "m"), model).exceptionOrNull()
            assertEquals(
                type.name,
                ProviderConfigException.Problem.UNSUPPORTED_PROVIDER_TYPE,
                (error as ProviderConfigException).problem,
            )
        }
    }
}
