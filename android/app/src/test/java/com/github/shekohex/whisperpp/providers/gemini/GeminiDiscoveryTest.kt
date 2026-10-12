package com.github.shekohex.whisperpp.providers.gemini

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.providers.baseUrl
import com.github.shekohex.whisperpp.providers.startLoopbackServer
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiDiscoveryTest {
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
    fun listModels_followsPageTokens_andClassifies() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"models":[
                    {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","version":"001",
                     "inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent","countTokens"],"thinking":true},
                    {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
                ],"nextPageToken":"page-2"}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"models":[
                    {"name":"models/gemini-2.0-flash-live-001","displayName":"Gemini 2.0 Flash Live",
                     "supportedGenerationMethods":["bidiGenerateContent"]}
                ]}"""
            )
        )

        val models = discovery().listModels().getOrThrow()

        val first = server.takeRequest()
        assertEquals("/v1beta/models?pageSize=200", first.path)
        assertEquals("AIza-test", first.getHeader("x-goog-api-key"))
        assertEquals("/v1beta/models?pageSize=200&pageToken=page-2", server.takeRequest().path)
        // The bidi-only Live model has no execution path and is not listed.
        assertEquals(listOf("gemini-2.5-flash"), models.map { it.id })
        val flash = models[0]
        assertEquals("Gemini 2.5 Flash", flash.displayName)
        assertTrue(flash.capabilities.text && flash.capabilities.stt && flash.capabilities.thinking)
        assertFalse(flash.capabilities.realtime)
        assertEquals("1048576", flash.raw["inputTokenLimit"])
    }

    @Test
    fun listModels_401_isUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"status":"PERMISSION_DENIED"}}"""))

        val error = discovery().listModels().exceptionOrNull()

        assertTrue(error is NetworkError.Unauthorized)
    }

    @Test
    fun listModels_emptyObject_isEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))

        assertEquals(emptyList<Any>(), discovery().listModels().getOrThrow())
    }

    @Test
    fun modelsUrl_usesParentOfPastedGenerateContentUrl() {
        val url = GeminiDiscovery.modelsUrl(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=x".toHttpUrl(),
            pageToken = null,
        )

        assertEquals("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", url.toString())
        assertNull(url.queryParameter("key"))
    }

    private fun discovery() = GeminiDiscovery(
        OkHttpClient(),
        ServiceProvider(
            id = "g",
            name = "Gemini",
            type = ProviderType.GEMINI,
            endpoint = server.baseUrl("/v1beta"),
            models = emptyList(),
        ),
        Credentials("AIza-test"),
    )
}
