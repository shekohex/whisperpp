package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.BatchUnsupportedException
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.TranscriptionProvider
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.baseUrl
import com.github.shekohex.whisperpp.providers.startLoopbackServer
import com.github.shekohex.whisperpp.providers.tempAudioFile
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Soniox, Speechmatics, AssemblyAI, xAI and Cartesia: capabilities, typed batch failure and socket verify. */
class StreamingOnlyTranscriptionTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val types = mapOf(
        ProviderType.SONIOX to "stt-async-v5",
        ProviderType.SPEECHMATICS to "speechmatics-enhanced",
        ProviderType.ASSEMBLYAI to "universal-3-6-pro",
        ProviderType.XAI to "grok-voice-transcribe-2.0",
        ProviderType.CARTESIA to "ink-2",
    )

    @Before
    fun setUp() {
        server = startLoopbackServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun provider(type: ProviderType, modelId: String, endpoint: String = server.baseUrl("/")) = ServiceProvider(
        id = "p1",
        name = type.toString(),
        type = type,
        endpoint = endpoint,
        models = listOf(ModelConfig(modelId, modelId, ModelCapabilities(stt = true))),
    )

    private fun transcription(type: ProviderType, key: String? = "test-key"): TranscriptionProvider {
        val modelId = types.getValue(type)
        return ProviderFactory(client) { key }.transcription(provider(type, modelId), modelId).getOrThrow()
    }

    @Test
    fun everyType_streamsAlways_andOpensASession() {
        for ((type, modelId) in types) {
            val provider = transcription(type)
            assertTrue(type.name, provider.capabilities.realtime)
            assertTrue("no batch endpoint, so the setting is ignored: ${type.name}", provider.streamingOnly)
            val request = TranscriptionRequest(provider = provider(type, modelId), modelId = modelId)
            assertNotNull(type.name, provider.openStream(request))
        }
    }

    @Test
    fun batchTranscribe_failsWithATypedUnsupportedError_andNeverTouchesTheNetwork() = runBlocking {
        for ((type, modelId) in types) {
            val audio = tempAudioFile()
            val request = TranscriptionRequest(provider = provider(type, modelId), modelId = modelId)

            val error = transcription(type).transcribe(audio, request).exceptionOrNull()

            assertTrue(type.name, error is BatchUnsupportedException)
            assertEquals(type.toString(), (error as BatchUnsupportedException).providerLabel)
            assertTrue(error.message.orEmpty().contains("not supported"))
            assertTrue("providers never delete their input", audio.exists())
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun batchTranscribe_withoutAKey_reportsTheMissingKey() = runBlocking {
        val request = TranscriptionRequest(provider = provider(ProviderType.CARTESIA, "ink-2"), modelId = "ink-2")

        val error = transcription(ProviderType.CARTESIA, key = null).transcribe(tempAudioFile(), request).exceptionOrNull()

        assertEquals(ProviderConfigException.Problem.MISSING_API_KEY, (error as ProviderConfigException).problem)
    }

    @Test
    fun factory_hasNoDiscoveryOrEnhancement() {
        val factory = ProviderFactory(client) { "k" }
        for ((type, modelId) in types) {
            assertNull(type.name, factory.discovery(provider(type, modelId)))
            val error = factory.enhancement(provider(type, modelId), ModelConfig("m", "m", ModelCapabilities.TEXT)).exceptionOrNull()
            assertTrue(type.name, error is ProviderConfigException)
        }
    }

    @Test
    fun verify_succeedsWhenTheSocketComesUp() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))

        val result = transcription(ProviderType.CARTESIA).verify()

        assertTrue("was ${result.exceptionOrNull()}", result.isSuccess)
        val upgrade = server.takeRequest()
        assertEquals("/stt/turns/websocket", upgrade.requestUrl?.encodedPath)
        assertEquals("test-key", upgrade.getHeader("X-API-Key"))
    }

    @Test
    fun verify_mapsAnUnauthorizedHandshake() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = transcription(ProviderType.CARTESIA).verify().exceptionOrNull()

        assertTrue("was $error", error is NetworkError.Unauthorized)
    }

    @Test
    fun verify_withoutAKey_failsBeforeConnecting() = runBlocking {
        val error = transcription(ProviderType.XAI, key = null).verify().exceptionOrNull()

        assertTrue(error is ProviderConfigException)
        assertFalse(server.requestCount > 0)
    }
}
