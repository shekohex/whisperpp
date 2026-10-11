package com.github.shekohex.whisperpp.privacy

import com.github.shekohex.whisperpp.core.network.HttpClientFactory
import com.github.shekohex.whisperpp.core.network.RedactingLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLoggingPrivacyTest {

    @Test
    fun loggingInterceptor_defaultsToNone_andRedactsSensitiveHeaders() {
        val interceptor = RedactingLoggingInterceptor()

        assertEquals(HttpLoggingInterceptor.Level.NONE, interceptor.level)
        assertSensitiveHeadersRedacted(interceptor.delegate)
    }

    @Test
    fun loggingInterceptor_enablingLogging_capsAtHeaders() {
        val interceptor = RedactingLoggingInterceptor()

        interceptor.setEnabled(true)
        assertEquals(HttpLoggingInterceptor.Level.HEADERS, interceptor.level)

        interceptor.setEnabled(false)
        assertEquals(HttpLoggingInterceptor.Level.NONE, interceptor.level)
    }

    @Test
    fun loggingInterceptor_redactsKeyQueryParameters() {
        val message = "--> GET https://example.com/v1/models?key=secret123&alt=json"

        val redacted = RedactingLoggingInterceptor.redactQueryKeys(message)

        assertEquals("--> GET https://example.com/v1/models?key=REDACTED&alt=json", redacted)
    }

    @Test
    fun sharedClient_usesRedactingInterceptor() {
        val interceptor = RedactingLoggingInterceptor()

        val client = HttpClientFactory.createOkHttpClient(interceptor)

        assertTrue(client.interceptors.contains(interceptor))
        assertEquals(HttpLoggingInterceptor.Level.NONE, interceptor.level)
    }

    private fun assertSensitiveHeadersRedacted(interceptor: HttpLoggingInterceptor) {
        val redactedHeaders = redactedHeadersOf(interceptor).map { it.lowercase() }.toSet()
        assertTrue(redactedHeaders.contains("authorization"))
        assertTrue(redactedHeaders.contains("x-goog-api-key"))
    }

    private fun redactedHeadersOf(interceptor: HttpLoggingInterceptor): Set<String> {
        val field = HttpLoggingInterceptor::class.java.getDeclaredField("headersToRedact")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(interceptor) as Set<String>
    }
}
