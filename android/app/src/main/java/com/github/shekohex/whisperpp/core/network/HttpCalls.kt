package com.github.shekohex.whisperpp.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private const val BODY_SNIPPET_MAX_CHARS = 200
private const val REDACTED = "REDACTED"
private val KEY_LIKE_TOKENS = Regex("(sk-[A-Za-z0-9_\\-]{8,}|AIza[0-9A-Za-z_\\-]{20,})")

/**
 * One cancellable attempt: checks the transport (J-01), applies [timeoutMs] to every socket read and
 * write, bounds the whole call (connect, upload, wait, download) by [callTimeoutMs] (0 = no total
 * bound) and maps I/O failures to [NetworkError]. Any status is returned as is.
 */
suspend fun OkHttpClient.executeRaw(request: Request, timeoutMs: Long, callTimeoutMs: Long = timeoutMs): RawResponse {
    val host = request.url.host
    if (!isTransportAllowed(request.url.scheme, host)) {
        throw NetworkError.InsecureTransport(host)
    }
    val client = if (timeoutMs > 0) {
        // Shares the pool and dispatcher; only the per-socket timeouts change.
        newBuilder()
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    } else {
        this
    }
    val call = client.newCall(request)
    if (callTimeoutMs > 0) {
        call.timeout().timeout(callTimeoutMs, TimeUnit.MILLISECONDS)
    }
    return try {
        call.awaitBody()
    } catch (e: IOException) {
        throw e.toNetworkError()
    }
}

/** [executeRaw] that also turns every non-2xx status into a [NetworkError]; [secret] is scrubbed from snippets. */
suspend fun OkHttpClient.executeForBody(
    request: Request,
    timeoutMs: Long,
    secret: String? = null,
    callTimeoutMs: Long = timeoutMs,
): RawResponse {
    val response = executeRaw(request, timeoutMs, callTimeoutMs)
    if (response.code in 200..299) {
        return response
    }
    throw response.toNetworkError(request.url.host, secret)
}

internal fun IOException.toNetworkError(): NetworkError = when (this) {
    is SocketTimeoutException -> NetworkError.Timeout(this)
    // OkHttp reports an expired call timeout as InterruptedIOException("timeout").
    is InterruptedIOException -> NetworkError.Timeout(this)
    is UnknownHostException, is ConnectException, is NoRouteToHostException -> NetworkError.Offline(this)
    // Resets and other transport failures: the request did not complete either way.
    else -> NetworkError.Offline(this)
}

internal fun RawResponse.toNetworkError(host: String, secret: String?): NetworkError = when (code) {
    401, 403 -> NetworkError.Unauthorized(code, host)
    429 -> NetworkError.RateLimited(host, parseRetryAfterMs(retryAfter))
    else -> NetworkError.Http(code, host, bodySnippet(body, secret))
}

/** First [BODY_SNIPPET_MAX_CHARS] characters of [body] with [secret] and key-like tokens removed. */
fun bodySnippet(body: String, secret: String?): String? {
    var text = body.trim()
    if (text.isEmpty()) return null
    if (!secret.isNullOrBlank()) {
        text = text.replace(secret, REDACTED)
    }
    text = text.replace(KEY_LIKE_TOKENS, REDACTED)
    return text.take(BODY_SNIPPET_MAX_CHARS)
}

/** `Retry-After` is either delta-seconds or an HTTP date. */
fun parseRetryAfterMs(value: String?, now: Instant = Instant.now()): Long? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    trimmed.toLongOrNull()?.let { seconds -> return (seconds * 1_000).coerceAtLeast(0) }
    return runCatching {
        val date = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        (date.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0)
    }.getOrNull()
}
