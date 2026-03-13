package com.github.shekohex.whisperpp.privacy

import android.content.ContextWrapper
import com.github.shekohex.whisperpp.SmartFixer
import com.github.shekohex.whisperpp.WhisperTranscriber
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLoggingPrivacyTest {

    @Test
    fun whisperTranscriber_defaultsToNone_andRedactsSensitiveHeaders() {
        val interceptor = loggingInterceptorOf(WhisperTranscriber())

        assertEquals(HttpLoggingInterceptor.Level.NONE, interceptor.level)
        assertSensitiveHeadersRedacted(interceptor)
    }

    @Test
    fun whisperTranscriber_enablingLogging_capsAtHeaders() {
        val transcriber = WhisperTranscriber()

        transcriber.setNetworkLoggingEnabled(true)
        assertEquals(HttpLoggingInterceptor.Level.HEADERS, loggingInterceptorOf(transcriber).level)

        transcriber.setNetworkLoggingEnabled(false)
        assertEquals(HttpLoggingInterceptor.Level.NONE, loggingInterceptorOf(transcriber).level)
    }

    @Test
    fun smartFixer_defaultsToNone_andRedactsSensitiveHeaders() {
        val interceptor = loggingInterceptorOf(SmartFixer(ContextWrapper(null)))

        assertEquals(HttpLoggingInterceptor.Level.NONE, interceptor.level)
        assertSensitiveHeadersRedacted(interceptor)
    }

    @Test
    fun smartFixer_enablingLogging_capsAtHeaders() {
        val smartFixer = SmartFixer(ContextWrapper(null))

        smartFixer.setNetworkLoggingEnabled(true)
        assertEquals(HttpLoggingInterceptor.Level.HEADERS, loggingInterceptorOf(smartFixer).level)

        smartFixer.setNetworkLoggingEnabled(false)
        assertEquals(HttpLoggingInterceptor.Level.NONE, loggingInterceptorOf(smartFixer).level)
    }

    private fun assertSensitiveHeadersRedacted(interceptor: HttpLoggingInterceptor) {
        val redactedHeaders = redactedHeadersOf(interceptor).map { it.lowercase() }.toSet()
        assertTrue(redactedHeaders.contains("authorization"))
        assertTrue(redactedHeaders.contains("x-goog-api-key"))
    }

    private fun loggingInterceptorOf(target: Any): HttpLoggingInterceptor {
        val field = target.javaClass.getDeclaredField("loggingInterceptor")
        field.isAccessible = true
        return field.get(target) as HttpLoggingInterceptor
    }

    private fun redactedHeadersOf(interceptor: HttpLoggingInterceptor): Set<String> {
        val field = HttpLoggingInterceptor::class.java.getDeclaredField("headersToRedact")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(interceptor) as Set<String>
    }
}
