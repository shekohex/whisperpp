package com.github.shekohex.whisperpp.providers.openai

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleChatTest {
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
    fun enhance_postsChatCompletion_andParsesContent() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"  Fixed text. "}}]}"""))

        val result = chat().enhance(
            EnhancementRequest(
                systemPrompt = "Clean up.",
                userText = "fixed text",
                contextPrompt = "Earlier",
                temperature = 0.3f,
            )
        )

        assertEquals("Fixed text.", result.getOrThrow())
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-chat", recorded.getHeader("Authorization"))
        val json = JSONObject(recorded.body.readUtf8())
        assertEquals("gpt-4o-mini", json.getString("model"))
        assertEquals(0.3, json.getDouble("temperature"), 0.0001)
        assertFalse(json.has("reasoning_effort"))
        val content = json.getJSONArray("messages").getJSONObject(0).getString("content")
        assertEquals(
            "Clean up.\n\n<CONTEXT_INFORMATION>\nEarlier\n</CONTEXT_INFORMATION>\n\n<TRANSCRIPT>\nfixed text\n</TRANSCRIPT>",
            content,
        )
    }

    @Test
    fun thinking_sendsReasoningEffortInsteadOfTemperature() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

        chat().enhance(
            EnhancementRequest(systemPrompt = "p", userText = "t", thinking = ThinkingConfig.Enabled(level = "High"))
        ).getOrThrow()

        val json = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("high", json.getString("reasoning_effort"))
        assertFalse(json.has("temperature"))
    }

    @Test
    fun malformedOrEmptyContent_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("not json"))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"   "}}]}"""))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":null}}]}"""))
        val chat = chat()

        repeat(3) {
            val error = chat.enhance(EnhancementRequest(systemPrompt = "p", userText = "t")).exceptionOrNull()
            assertTrue("was $error", error is NetworkError.InvalidResponse)
        }
    }

    @Test
    fun verify_sendsMinimalRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"OK"}}]}"""))

        assertTrue(chat().verify().isSuccess)

        val json = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(16, json.getInt("max_tokens"))
    }

    private fun chat() = OpenAiCompatibleChat(
        client = recording.client,
        provider = ServiceProvider(
            id = "p1",
            name = "OpenAI",
            type = ProviderType.OPENAI,
            endpoint = server.baseUrl("/v1"),
            models = emptyList(),
        ),
        model = ModelConfig(id = "gpt-4o-mini", name = "GPT", capabilities = ModelCapabilities.TEXT),
        credentials = Credentials("sk-chat"),
    )
}
