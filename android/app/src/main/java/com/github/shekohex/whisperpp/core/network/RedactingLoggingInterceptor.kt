package com.github.shekohex.whisperpp.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Header-only HTTP logging that is off by default and never prints credentials:
 * `Authorization`/`x-goog-api-key` headers and `key=` query parameters are redacted.
 */
class RedactingLoggingInterceptor(tag: String = DEFAULT_TAG) : Interceptor {
    internal val delegate = HttpLoggingInterceptor { message ->
        Log.d(tag, redactQueryKeys(message))
    }.apply {
        level = HttpLoggingInterceptor.Level.NONE
        REDACTED_HEADERS.forEach { redactHeader(it) }
    }

    val level: HttpLoggingInterceptor.Level
        get() = delegate.level

    fun setEnabled(enabled: Boolean) {
        delegate.level = if (enabled) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response = delegate.intercept(chain)

    companion object {
        private const val DEFAULT_TAG = "HTTP"
        private val QUERY_KEY_REGEX = Regex("([?&]key=)[^&\\s]+")
        val REDACTED_HEADERS: List<String> = listOf("Authorization", "x-goog-api-key")

        fun redactQueryKeys(message: String): String {
            return message.replace(QUERY_KEY_REGEX, "$1REDACTED")
        }
    }
}
