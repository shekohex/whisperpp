package com.github.shekohex.whisperpp.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiRealtimeSttClientTest {

    @Test
    fun buildRequest_httpsEndpoint_doesNotCrash_andKeepsHttpsScheme() {
        val request = OpenAiRealtimeSttClient.buildRequest(
            "https://api.openai.com",
            "gpt-4o-realtime-preview",
            "sk-test",
        )
        assertNotNull(request)
        assertEquals("https", request!!.url.scheme)
        assertEquals("api.openai.com", request.url.host)
        assertEquals("/v1/realtime", request.url.encodedPath)
        assertEquals("gpt-4o-realtime-preview", request.url.queryParameter("model"))
    }

    @Test
    fun buildRequest_httpEndpoint_normalizesToHttpScheme() {
        val request = OpenAiRealtimeSttClient.buildRequest(
            "http://localhost:8080",
            "test-model",
            "sk-test",
        )
        assertNotNull(request)
        assertEquals("http", request!!.url.scheme)
    }

    @Test
    fun buildRequest_userEnteredWssEndpoint_isAcceptedAndNormalizedToHttps() {
        val request = OpenAiRealtimeSttClient.buildRequest(
            "wss://api.openai.com",
            "test-model",
            "sk-test",
        )
        assertNotNull(request)
        assertEquals("https", request!!.url.scheme)
    }

    @Test
    fun buildRequest_userEnteredWsEndpoint_isAcceptedAndNormalizedToHttp() {
        val request = OpenAiRealtimeSttClient.buildRequest(
            "ws://localhost:8080",
            "test-model",
            "sk-test",
        )
        assertNotNull(request)
        assertEquals("http", request!!.url.scheme)
    }

    @Test
    fun buildRequest_invalidScheme_returnsNull() {
        val request = OpenAiRealtimeSttClient.buildRequest(
            "ftp://example.com",
            "test-model",
            "sk-test",
        )
        assertNull(request)
    }

    @Test
    fun buildRequest_endpointAlreadyEndingInV1_appendsRealtimeSegment() {
        val request = OpenAiRealtimeSttClient.buildRequest(
            "https://api.openai.com/v1",
            "test-model",
            "sk-test",
        )
        assertNotNull(request)
        assertEquals("/v1/realtime", request!!.url.encodedPath)
    }
}
