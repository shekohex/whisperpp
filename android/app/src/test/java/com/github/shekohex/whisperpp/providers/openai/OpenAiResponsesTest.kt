package com.github.shekohex.whisperpp.providers.openai

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import com.github.shekohex.whisperpp.providers.GatedSseServer
import com.github.shekohex.whisperpp.providers.RecordingClient
import com.github.shekohex.whisperpp.providers.baseUrl
import com.github.shekohex.whisperpp.providers.startLoopbackServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiResponsesTest {
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
    fun enhanceStreaming_postsResponsesStream_snapshotsDeltas_reconcilesFromCompleted() = runBlocking {
        server.enqueue(
            sse(
                """{"type":"response.output_text.delta","delta":"Hello"}""",
                """{"type":"response.output_text.delta","delta":" world"}""",
                completed(outputText = "Hello world."),
            )
        )

        val snapshots = mutableListOf<String>()
        val result = responses().enhanceStreaming(
            EnhancementRequest(systemPrompt = "Clean up.", userText = "hello", temperature = 0.3f)
        ) { snapshots += it }

        assertEquals("Hello world.", result.getOrThrow())
        assertEquals(listOf("Hello", "Hello world"), snapshots)
        val recorded = server.takeRequest()
        assertEquals("/v1/responses", recorded.path)
        assertEquals("Bearer sk-resp", recorded.getHeader("Authorization"))
        val json = JSONObject(recorded.body.readUtf8())
        assertEquals("gpt-4o-mini", json.getString("model"))
        assertTrue(json.getBoolean("stream"))
        assertFalse(json.getBoolean("store"))
        assertEquals(0.3, json.getDouble("temperature"), 0.0001)
        assertEquals("Clean up.\n\n<TRANSCRIPT>\nhello\n</TRANSCRIPT>", json.getString("input"))
        assertEquals(
            TimeUnit.MILLISECONDS.toNanos(EnhancementRequest.DEFAULT_TIMEOUT_MS),
            recording.calls.single().timeout().timeoutNanos(),
        )
    }

    @Test
    fun enhanceStreaming_completedOutput_isAuthoritativeOverDeltas() = runBlocking {
        server.enqueue(
            sse(
                """{"type":"response.output_text.delta","delta":"Hel"}""",
                completed(outputText = "Fixed."),
            )
        )

        val snapshots = mutableListOf<String>()
        val result = responses().enhanceStreaming(EnhancementRequest("p", "t")) { snapshots += it }

        assertEquals("Fixed.", result.getOrThrow())
        assertEquals(listOf("Hel"), snapshots)
    }

    @Test
    fun reasoningModel_sendsEffort_omitsTemperature_ignoresReasoningEvents() = runBlocking {
        server.enqueue(
            sse(
                """{"type":"response.reasoning_summary_text.delta","delta":"hmm"}""",
                """{"type":"response.output_text.delta","delta":"ok"}""",
                completed(outputText = "ok"),
            )
        )

        val snapshots = mutableListOf<String>()
        val result = responses(modelId = "gpt-5").enhanceStreaming(
            EnhancementRequest("p", "t", thinking = ThinkingConfig.Enabled("High"))
        ) { snapshots += it }

        assertEquals("ok", result.getOrThrow())
        assertEquals(listOf("ok"), snapshots)
        val json = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("high", json.getJSONObject("reasoning").getString("effort"))
        assertFalse(json.has("temperature"))
    }

    @Test
    fun terminalFailures_non2xx_malformed_eof_areFailures() = runBlocking {
        val provider = responses()
        val failures = listOf(
            // EOF after a delta, no completed event.
            sse("""{"type":"response.output_text.delta","delta":"partial"}"""),
            // Malformed event JSON.
            sse("not json"),
            // Terminal error events.
            sse("""{"type":"response.failed","response":{"status":"failed","error":{"message":"boom sk-leak"}}}"""),
            sse("""{"type":"response.incomplete","response":{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}}"""),
            sse("""{"type":"error","code":"server_error","message":"nope"}"""),
            // Completed but refused / incomplete output.
            sse("""{"type":"response.completed","response":{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"no"}]}]}}"""),
            sse("""{"type":"response.completed","response":{"status":"incomplete"}}"""),
            sse("""{"type":"response.output_text.delta","delta":"withdrawn"}""", completed(outputText = "")),
            sse("""{"type":"response.output_text.delta","delta":"withdrawn"}""",
                """{"type":"response.completed","response":{"status":"completed","output":[]}}"""),
        )
        for (response in failures) {
            server.enqueue(response)
            val error = provider.enhanceStreaming(EnhancementRequest("p", "t")) { }.exceptionOrNull()
            assertTrue("was $error", error is NetworkError.InvalidResponse)
        }

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val unauthorized = provider.enhanceStreaming(EnhancementRequest("p", "t")) {}.exceptionOrNull()
        assertTrue("was $unauthorized", unauthorized is NetworkError.Unauthorized)
    }

    @Test
    fun streamingHttpErrorsPreserveRetryAfter_andRedactCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2"))
        val limited = responses().enhanceStreaming(EnhancementRequest("p", "t")) {}.exceptionOrNull()
        assertTrue(limited is NetworkError.RateLimited)
        assertEquals(2_000L, (limited as NetworkError.RateLimited).retryAfterMs)

        server.enqueue(MockResponse().setResponseCode(503).setBody("failed request with sk-resp"))
        val failed = responses().enhanceStreaming(EnhancementRequest("p", "t")) {}.exceptionOrNull()
        assertTrue(failed is NetworkError.Http)
        assertEquals(503, (failed as NetworkError.Http).status)
        assertFalse(failed.bodySnippet.orEmpty().contains("sk-resp"))
    }

    @Test
    fun sse_comments_crlfMultilineData_andTrailingEventWithoutBlankLine_parse() = runBlocking {
        val body = ": keep-alive\r\n\r\n" +
            "data: {\"type\":\"response.output_text.delta\",\r\n" +
            "data: \"delta\":\"He\"}\r\n\r\n" +
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"y\"}\r\n\r\n" +
            "data: ${completed(outputText = "Hey")}"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))

        val snapshots = mutableListOf<String>()
        val result = responses().enhanceStreaming(EnhancementRequest("p", "t")) { snapshots += it }

        assertEquals("Hey", result.getOrThrow())
        assertEquals(listOf("He", "Hey"), snapshots)
    }

    @Test
    fun enhance_buffered_parsesOutput_andVerifySendsBoundedRequest() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"completed","output":[{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":"  Fixed. "}]}]}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"OK"}}]}}"""))

        assertEquals("Fixed.", responses().enhance(EnhancementRequest("p", "t")).getOrThrow())
        assertTrue(responses().verify().isSuccess)

        val enhance = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(enhance.has("stream"))
        assertFalse(enhance.has("max_output_tokens"))
        assertFalse(enhance.getBoolean("store"))
        val verify = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(16, verify.getInt("max_output_tokens"))
    }

    @Test
    fun pastedChatCompletionsUrl_isNormalizedToResponses() {
        val base = okhttp3.HttpUrl.Builder().scheme("https").host("api.openai.com")
            .addPathSegments("v1/chat/completions").build()
        assertEquals("/v1/responses", OpenAiResponses.responsesUrl(base).encodedPath)
        val responsesBase = base.newBuilder().removePathSegment(base.pathSegments.lastIndex)
            .removePathSegment(base.pathSegments.lastIndex - 1)
            .addPathSegment("responses").build()
        assertEquals("/v1/responses", OpenAiResponses.responsesUrl(responsesBase).encodedPath)
    }

    @Test
    fun enhanceStreaming_cancelMidStream_abortsConnection() = runBlocking {
        GatedSseServer("""{"type":"response.output_text.delta","delta":"Hello"}""").use { gated ->
            val snapshots = mutableListOf<String>()
            val firstSnapshot = CompletableDeferred<Unit>()
            val job = launch {
                responses(endpoint = gated.url).enhanceStreaming(EnhancementRequest("p", "t")) {
                    snapshots += it
                    firstSnapshot.complete(Unit)
                }
            }

            withTimeout(5_000) { firstSnapshot.await() }

            withTimeout(5_000) { job.cancelAndJoin() }

            assertTrue(gated.clientDisconnected.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("Hello"), snapshots.toList())
            assertTrue(recording.calls.all { it.isCanceled() })
        }
    }

    private fun sse(vararg events: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.joinToString("") { "data: $it\n\n" })

    private fun completed(outputText: String): String =
        """{"type":"response.completed","response":{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"$outputText"}]}]}}"""

    private fun responses(modelId: String = "gpt-4o-mini", endpoint: String? = null) = OpenAiResponses(
        client = recording.client,
        provider = ServiceProvider(
            id = "p1",
            name = "OpenAI",
            type = ProviderType.OPENAI,
            endpoint = endpoint ?: server.baseUrl("/v1"),
            models = emptyList(),
        ),
        model = ModelConfig(id = modelId, name = "GPT", capabilities = ModelCapabilities.TEXT),
        credentials = Credentials("sk-resp"),
    )
}
