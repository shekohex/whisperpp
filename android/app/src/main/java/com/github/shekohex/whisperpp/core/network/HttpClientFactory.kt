package com.github.shekohex.whisperpp.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds the single process-wide [OkHttpClient]. Callers that need different timeouts derive
 * a client with [OkHttpClient.newBuilder], which shares the connection pool and dispatcher.
 */
object HttpClientFactory {
    private const val CONNECT_TIMEOUT_SECONDS = 15L

    fun createOkHttpClient(loggingInterceptor: RedactingLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    fun createKtorClient(okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
        }
    }
}
