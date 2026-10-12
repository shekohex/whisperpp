package com.github.shekohex.whisperpp.providers.openai

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.RecordingClient
import com.github.shekohex.whisperpp.providers.baseUrl
import com.github.shekohex.whisperpp.providers.startLoopbackServer
import com.github.shekohex.whisperpp.providers.tempAudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiCompatibleTranscriptionTest {
    private lateinit var server: MockWebServer
    private val recording = RecordingClient()

    @Before
    fun setUp() {
        server = startLoopbackServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun openAi_happyPath_sendsMultipartWithLanguage_andParsesText() = runBlocking {
        server.enqueue(MockResponse().setBody(" hello world \n"))
        val audio = tempAudioFile()

        val result = transcriber(ProviderType.OPENAI).transcribe(
            audio,
            request(language = "ar", prompt = "Vocab", contextPrompt = "Before", temperature = 0.2f),
        )

        assertEquals("hello world", result.getOrThrow().text)
        val recorded = server.takeRequest()
        assertEquals("/v1/audio/transcriptions", recorded.path)
        assertEquals("Bearer sk-test-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.formField("model", "whisper-1"))
        assertTrue(body.formField("response_format", "text"))
        assertTrue(body.formField("language", "ar"))
        assertTrue(body.formField("prompt", "Vocab\nBefore"))
        assertTrue(body.formField("temperature", "0.2"))
        assertTrue(body.contains("name=\"file\"; filename=\"@audio.wav\""))
        // Providers never delete their input (G-09).
        assertTrue(audio.exists())
    }

    @Test
    fun gpt4oTranscribeModels_requestJson_andParseText() = runBlocking {
        for (id in listOf("gpt-4o-transcribe", "gpt-4o-mini-transcribe")) {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"text":" hi "}"""))

            val result = transcriber(ProviderType.OPENAI, modelId = id).transcribe(tempAudioFile(), request())

            assertEquals("hi", result.getOrThrow().text)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(id, body.formField("response_format", "json"))
        }
    }

    @Test
    fun openAi_autoLanguage_isNotSent() = runBlocking {
        server.enqueue(MockResponse().setBody("hi"))

        transcriber(ProviderType.OPENAI).transcribe(tempAudioFile(), request(language = "auto")).getOrThrow()

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("name=\"language\""))
        assertFalse(body.contains("name=\"prompt\""))
        assertFalse(body.contains("name=\"temperature\""))
    }

    @Test
    fun whisperAsr_usesQueryLanguageAndAudioFilePart_withoutAuth() = runBlocking {
        server.enqueue(MockResponse().setBody("salam"))
        server.enqueue(MockResponse().setBody("auto"))
        val provider = transcriber(ProviderType.WHISPER_ASR, path = "/asr", authMode = ProviderAuthMode.NO_AUTH, key = null)

        provider.transcribe(tempAudioFile(), request(language = "ar")).getOrThrow()
        provider.transcribe(tempAudioFile(), request(language = "")).getOrThrow()

        val first = server.takeRequest()
        assertEquals("/asr", first.requestUrl?.encodedPath)
        assertEquals("ar", first.requestUrl?.queryParameter("language"))
        assertEquals("transcribe", first.requestUrl?.queryParameter("task"))
        assertEquals("txt", first.requestUrl?.queryParameter("output"))
        assertNull(first.getHeader("Authorization"))
        assertTrue(first.body.readUtf8().contains("name=\"audio_file\""))
        assertEquals("auto", server.takeRequest().requestUrl?.queryParameter("language"))
    }

    @Test
    fun jsonResponse_isUnwrapped_andScriptConversionHookRuns() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"text":" 头发 "}"""))
        var conversion: ScriptConversion? = null
        val provider = OpenAiCompatibleTranscription(
            client = recording.client,
            provider = provider(ProviderType.OPENAI),
            model = model(),
            credentials = Credentials("sk-test-key"),
            postProcess = { text, mode ->
                conversion = mode
                "converted:$text"
            },
        )

        val result = provider.transcribe(tempAudioFile(), request(scriptConversion = ScriptConversion.TO_TRADITIONAL))

        assertEquals("converted:头发", result.getOrThrow().text)
        assertEquals(ScriptConversion.TO_TRADITIONAL, conversion)
    }

    @Test
    fun customProvider_stripsWrappingQuotes() = runBlocking {
        server.enqueue(MockResponse().setBody("\"quoted text\""))

        val result = transcriber(ProviderType.CUSTOM).transcribe(tempAudioFile(), request())

        assertEquals("quoted text", result.getOrThrow().text)
    }

    @Test
    fun unauthorized_mapsTo401And403() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad key"))
        server.enqueue(MockResponse().setResponseCode(403))
        val provider = transcriber(ProviderType.OPENAI)

        val first = provider.transcribe(tempAudioFile(), request()).exceptionOrNull()
        val second = provider.transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertEquals(401, (first as NetworkError.Unauthorized).status)
        assertEquals("127.0.0.1", first.host)
        assertEquals(403, (second as NetworkError.Unauthorized).status)
    }

    @Test
    fun rateLimited_parsesRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7"))

        val error = transcriber(ProviderType.OPENAI).transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertTrue(error is NetworkError.RateLimited)
        assertEquals(7_000L, (error as NetworkError.RateLimited).retryAfterMs)
    }

    @Test
    fun serverError_keepsARedactedCappedSnippet() = runBlocking {
        val body = "upstream failed for key sk-test-key " + "x".repeat(500)
        server.enqueue(MockResponse().setResponseCode(500).setBody(body))

        val error = transcriber(ProviderType.OPENAI).transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertTrue(error is NetworkError.Http)
        error as NetworkError.Http
        assertEquals(500, error.status)
        assertEquals("127.0.0.1", error.host)
        val snippet = assertNotNullAndGet(error.bodySnippet)
        assertTrue(snippet.length <= 200)
        assertTrue(snippet.startsWith("upstream failed for key REDACTED"))
        assertFalse(snippet.contains("sk-test-key"))
        assertFalse(error.message.orEmpty().contains("sk-test-key"))
    }

    @Test
    fun noResponse_mapsToTimeout() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val error = transcriber(ProviderType.OPENAI).transcribe(tempAudioFile(), request(timeoutMs = 300)).exceptionOrNull()

        assertTrue("was $error", error is NetworkError.Timeout)
    }

    @Test
    fun cancellingTheCoroutine_cancelsTheCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val provider = transcriber(ProviderType.OPENAI)

        val job = launch(Dispatchers.IO) {
            provider.transcribe(tempAudioFile(), request(timeoutMs = 60_000))
        }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        job.cancel()
        job.join()

        assertEquals(1, server.requestCount)
        assertEquals(1, recording.calls.size)
        assertTrue(recording.calls.single().isCanceled())
    }

    @Test
    fun cleartextToRemoteHost_isRefusedWithoutSending() = runBlocking {
        val provider = OpenAiCompatibleTranscription(
            client = recording.client,
            provider = provider(ProviderType.OPENAI).copy(endpoint = "http://api.example.com/v1"),
            model = model(),
            credentials = Credentials("sk-test-key"),
        )

        val error = provider.transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertTrue(error is NetworkError.InsecureTransport)
        assertTrue(recording.calls.isEmpty())
    }

    @Test
    fun missingApiKey_failsBeforeSending() = runBlocking {
        val error = transcriber(ProviderType.OPENAI, key = null).transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertEquals(ProviderConfigException.Problem.MISSING_API_KEY, (error as ProviderConfigException).problem)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun verify_checksModelsEndpointAndMapsAuthFailures() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        val provider = transcriber(ProviderType.OPENAI)

        assertTrue(provider.verify().isSuccess)
        assertTrue(provider.verify().exceptionOrNull() is NetworkError.Unauthorized)
        assertEquals("/v1/models", server.takeRequest().path)
    }

    private fun transcriber(
        type: ProviderType,
        path: String = "/v1",
        authMode: ProviderAuthMode = ProviderAuthMode.API_KEY,
        key: String? = "sk-test-key",
        modelId: String = "whisper-1",
    ) = OpenAiCompatibleTranscription(
        client = recording.client,
        provider = provider(type, path).copy(authMode = authMode),
        model = model().copy(id = modelId),
        credentials = Credentials(key),
    )

    private fun provider(type: ProviderType, path: String = "/v1") = ServiceProvider(
        id = "p1",
        name = "Provider",
        type = type,
        endpoint = server.baseUrl(path),
        models = listOf(model()),
    )

    private fun model() = ModelConfig(id = "whisper-1", name = "Whisper", capabilities = ModelCapabilities.stt(ProviderType.OPENAI))

    private fun request(
        language: String = "auto",
        prompt: String = "",
        contextPrompt: String? = null,
        temperature: Float = 0f,
        timeoutMs: Long = 5_000,
        scriptConversion: ScriptConversion = ScriptConversion.NONE,
    ) = TranscriptionRequest(
        provider = provider(ProviderType.OPENAI),
        modelId = "whisper-1",
        languageCode = language,
        prompt = prompt,
        contextPrompt = contextPrompt,
        temperature = temperature,
        timeoutMs = timeoutMs,
        scriptConversion = scriptConversion,
    )

    private fun String.formField(name: String, value: String): Boolean =
        contains("name=\"$name\"") && contains("\r\n\r\n$value\r\n")

    private fun <T : Any> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
