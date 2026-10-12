package com.github.shekohex.whisperpp.providers.gemini

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import com.github.shekohex.whisperpp.providers.RecordingClient
import com.github.shekohex.whisperpp.providers.baseUrl
import com.github.shekohex.whisperpp.providers.startLoopbackServer
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

class GeminiChatTest {
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
    fun enhance_postsGenerateContent_andJoinsTextParts() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"Hello "},{"text":"world. "}]}}]}"""
            )
        )

        val result = gemini().enhance(EnhancementRequest(systemPrompt = "Fix", userText = "hello world", temperature = 0.5f))

        assertEquals("Hello world.", result.getOrThrow())
        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.0-flash:generateContent", recorded.path)
        assertEquals("AIza-test", recorded.getHeader("x-goog-api-key"))
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.requestUrl?.queryParameter("key"))
        val json = JSONObject(recorded.body.readUtf8())
        val part = json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0)
        assertEquals("Fix\n\n<TRANSCRIPT>\nhello world\n</TRANSCRIPT>", part.getString("text"))
        assertEquals(0.5, json.getJSONObject("generationConfig").getDouble("temperature"), 0.0001)
    }

    @Test
    fun thinkingBudget_replacesTemperature() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""))
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""))
        val gemini = gemini()

        gemini.enhance(EnhancementRequest("p", "t", thinking = ThinkingConfig.Enabled("medium", budgetTokens = 1024))).getOrThrow()
        gemini.enhance(EnhancementRequest("p", "t", thinking = ThinkingConfig.Enabled("low"))).getOrThrow()

        val budget = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("generationConfig")
        assertEquals(1024, budget.getJSONObject("thinkingConfig").getInt("thinkingBudget"))
        assertFalse(budget.has("temperature"))
        val level = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("generationConfig")
        assertEquals("LOW", level.getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }

    @Test
    fun missingCandidates_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))

        val error = gemini().enhance(EnhancementRequest("p", "t")).exceptionOrNull()

        assertTrue("was $error", error is NetworkError.InvalidResponse)
    }

    @Test
    fun fullGenerateContentEndpoint_isUsedAsIs() {
        val base = okhttp3.HttpUrl.Builder().scheme("https").host("example.com")
            .addPathSegments("v1beta/models/custom:generateContent").build()

        assertEquals(base, GeminiChat.generateContentUrl(base, "ignored"))
    }

    private fun gemini() = GeminiChat(
        client = recording.client,
        provider = ServiceProvider(
            id = "g1",
            name = "Gemini",
            type = ProviderType.GEMINI,
            endpoint = server.baseUrl("/v1beta"),
            models = emptyList(),
        ),
        model = ModelConfig(id = "gemini-2.0-flash", name = "Flash", capabilities = ModelCapabilities.TEXT),
        credentials = Credentials("AIza-test"),
    )
}
