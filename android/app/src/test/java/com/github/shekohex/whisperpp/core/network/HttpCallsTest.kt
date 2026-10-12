package com.github.shekohex.whisperpp.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

class HttpCallsTest {
    @Test
    fun retryAfter_parsesSecondsAndHttpDates() {
        val now = Instant.parse("2026-10-12T10:00:00Z")

        assertEquals(30_000L, parseRetryAfterMs("30", now))
        assertEquals(90_000L, parseRetryAfterMs("Mon, 12 Oct 2026 10:01:30 GMT", now))
        assertEquals(0L, parseRetryAfterMs("Mon, 12 Oct 2026 09:00:00 GMT", now))
        assertNull(parseRetryAfterMs("soon", now))
        assertNull(parseRetryAfterMs(null, now))
    }

    @Test
    fun ioExceptions_mapToTypedErrors() {
        assertTrue(SocketTimeoutException().toNetworkError() is NetworkError.Timeout)
        assertTrue(UnknownHostException("api.example.com").toNetworkError() is NetworkError.Offline)
        assertTrue(ConnectException().toNetworkError() is NetworkError.Offline)
    }

    @Test
    fun bodySnippet_redactsSecretsAndKeyLikeTokens() {
        val snippet = bodySnippet("key=my-secret token sk-abcdefghijkl and AIzaSyA1234567890123456789012", "my-secret")

        assertEquals("key=REDACTED token REDACTED and REDACTED", snippet)
        assertNull(bodySnippet("   ", null))
        assertEquals(200, bodySnippet("x".repeat(1_000), null)?.length)
    }
}
