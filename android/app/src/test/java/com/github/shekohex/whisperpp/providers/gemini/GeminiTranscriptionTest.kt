package com.github.shekohex.whisperpp.providers.gemini

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.RecordingClient
import com.github.shekohex.whisperpp.providers.baseUrl
import com.github.shekohex.whisperpp.providers.startLoopbackServer
import com.github.shekohex.whisperpp.providers.tempAudioFile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class GeminiTranscriptionTest {
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
    fun transcribe_postsInlineAudioWithInstruction_andParsesText() = runBlocking {
        server.enqueue(MockResponse().setBody(candidate("\"Hello ", "world.\" ")))
        val audio = tempAudioFile()

        val result = transcriber().transcribe(audio, request(language = "ar", prompt = "Kotlin", contextPrompt = "Before", temperature = 0.3f))

        assertEquals("Hello world.", result.getOrThrow().text)
        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.0-flash:generateContent", recorded.path)
        assertEquals("AIza-test", recorded.getHeader("x-goog-api-key"))
        assertNull(recorded.getHeader("Authorization"))
        val json = JSONObject(recorded.body.readUtf8())
        val parts = json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val inline = parts.getJSONObject(0).getJSONObject("inlineData")
        assertEquals("audio/wav", inline.getString("mimeType"))
        assertEquals("AQIDBA==", inline.getString("data"))
        val instruction = parts.getJSONObject(1).getString("text")
        assertTrue(instruction.startsWith("Transcribe this audio verbatim. Output only the transcript text, no commentary."))
        assertTrue(instruction.contains("The speech is in Arabic."))
        assertTrue(instruction.contains("Preferred spellings: Kotlin\nBefore"))
        val config = json.getJSONObject("generationConfig")
        assertEquals(0.3, config.getDouble("temperature"), 0.0001)
        assertEquals("text/plain", config.getString("responseMimeType"))
        assertTrue(audio.exists())
    }

    @Test
    fun autoLanguageAndNoVocabulary_leaveTheInstructionPlain() = runBlocking {
        server.enqueue(MockResponse().setBody(candidate("ok")))

        transcriber().transcribe(tempAudioFile(), request()).getOrThrow()

        val json = JSONObject(server.takeRequest().body.readUtf8())
        val instruction = json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(1).getString("text")
        assertEquals("Transcribe this audio verbatim. Output only the transcript text, no commentary.", instruction)
    }

    @Test
    fun thoughtParts_areExcluded_andScriptConversionIsApplied() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":" hi "}]}}]}"""
            )
        )
        val provider = GeminiTranscription(recording.client, provider(), model(), Credentials("AIza-test")) { text, mode ->
            "$text|$mode"
        }

        val result = provider.transcribe(tempAudioFile(), request(scriptConversion = ScriptConversion.TO_SIMPLIFIED))

        assertEquals("hi|TO_SIMPLIFIED", result.getOrThrow().text)
    }

    @Test
    fun emptyOrMissingCandidates_returnEmptyTranscript() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"candidates":[]}"""))
        server.enqueue(MockResponse().setBody(candidate("  ")))
        val provider = transcriber()

        repeat(2) { assertEquals("", provider.transcribe(tempAudioFile(), request()).getOrThrow().text) }
    }

    @Test
    fun blockedPromptOrCandidate_isTypedFailure_notSilence() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"promptFeedback":{"blockReason":"OTHER"}}"""))
        server.enqueue(MockResponse().setBody("""{"candidates":[{"finishReason":"SAFETY"}]}"""))
        val provider = transcriber()

        repeat(2) {
            val error = provider.transcribe(tempAudioFile(), request()).exceptionOrNull()
            assertTrue(error is NetworkError.InvalidResponse)
            assertEquals(R.string.gemini_error_blocked, (error as NetworkError.InvalidResponse).userMessageRes)
        }
    }

    @Test
    fun malformedJson_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("not json"))

        val error = transcriber().transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertTrue(error is NetworkError.InvalidResponse)
    }

    @Test
    fun unauthorized_mapsStatus() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(403))
        val provider = transcriber()

        val first = provider.transcribe(tempAudioFile(), request()).exceptionOrNull()
        val second = provider.transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertEquals(401, (first as NetworkError.Unauthorized).status)
        assertEquals(403, (second as NetworkError.Unauthorized).status)
    }

    @Test
    fun rateLimited_parsesRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7"))

        val error = transcriber().transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertEquals(7_000L, (error as NetworkError.RateLimited).retryAfterMs)
    }

    @Test
    fun serverError_isHttpError_withoutLeakingTheKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"bad key AIza-test"}"""))

        val error = transcriber().transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertEquals(500, (error as NetworkError.Http).status)
        assertFalse(error.bodySnippet.orEmpty().contains("AIza-test"))
    }

    @Test
    fun inlineLimit_accountsForBase64Expansion() {
        // 14 MB raw ≈ 18.7 MB encoded: under the 20 MB request limit with room for the envelope.
        val encoded = GeminiTranscription.base64Length(GeminiTranscription.MAX_INLINE_AUDIO_BYTES)
        assertTrue(encoded < 19L * 1024 * 1024)
        assertEquals(8L, GeminiTranscription.base64Length(4))
    }

    @Test
    fun oversizeAudio_isRejectedBeforeAnyRequest() = runBlocking {
        val audio = File.createTempFile("big", ".wav").apply { deleteOnExit() }
        RandomAccessFile(audio, "rw").use { it.setLength(GeminiTranscription.MAX_INLINE_AUDIO_BYTES + 1) }

        val error = transcriber().transcribe(audio, request()).exceptionOrNull()

        assertTrue(error is NetworkError.InvalidResponse)
        assertEquals("audio too large for inline Gemini transcription", (error as NetworkError.InvalidResponse).detail)
        assertEquals(R.string.gemini_error_audio_too_large, error.userMessageRes)
        assertEquals(0, server.requestCount)
        assertTrue(audio.exists())
    }

    @Test
    fun missingApiKey_failsBeforeAnyRequest() = runBlocking {
        val error = transcriber(key = null).transcribe(tempAudioFile(), request()).exceptionOrNull()

        assertTrue(error != null)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun verify_listsModels_andMapsAuthFailures() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"models":[]}"""))
        server.enqueue(MockResponse().setResponseCode(403))
        val provider = transcriber()

        assertTrue(provider.verify().isSuccess)
        val recorded = server.takeRequest()
        assertEquals("/v1beta/models", recorded.path)
        assertEquals("AIza-test", recorded.getHeader("x-goog-api-key"))
        assertTrue(provider.verify().exceptionOrNull() is NetworkError.Unauthorized)
    }

    private fun candidate(vararg texts: String): String {
        val parts = texts.joinToString(",") { """{"text":${JSONObject.quote(it)}}""" }
        return """{"candidates":[{"content":{"parts":[$parts]}}]}"""
    }

    private fun transcriber(key: String? = "AIza-test") =
        GeminiTranscription(recording.client, provider(), model(), Credentials(key))

    private fun provider() = ServiceProvider(
        id = "g1",
        name = "Gemini",
        type = ProviderType.GEMINI,
        endpoint = server.baseUrl("/v1beta"),
        models = listOf(model()),
    )

    private fun model() = ModelConfig(id = "gemini-2.0-flash", name = "Flash", capabilities = ModelCapabilities.GEMINI_AUDIO)

    private fun request(
        language: String = "auto",
        prompt: String = "",
        contextPrompt: String? = null,
        temperature: Float = 0f,
        scriptConversion: ScriptConversion = ScriptConversion.NONE,
    ) = TranscriptionRequest(
        provider = provider(),
        modelId = "gemini-2.0-flash",
        languageCode = language,
        prompt = prompt,
        contextPrompt = contextPrompt,
        temperature = temperature,
        timeoutMs = 5_000,
        scriptConversion = scriptConversion,
    )
}
