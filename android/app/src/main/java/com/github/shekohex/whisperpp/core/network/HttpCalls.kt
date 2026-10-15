package com.github.shekohex.whisperpp.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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

/**
 * Server-sent events of [request] as a cancellable flow of `data:` payloads (multi-line data
 * joined with `\n`; comments and other fields ignored). Completes at EOF, fails with [NetworkError]
 * on transport failures and non-2xx statuses. Cancelling collection cancels the call, which aborts
 * the headers wait and a blocked body read alike.
 */
fun OkHttpClient.executeForEvents(
    request: Request,
    timeoutMs: Long,
    secret: String? = null,
    callTimeoutMs: Long = timeoutMs,
): Flow<String> = callbackFlow {
    val host = request.url.host
    if (!isTransportAllowed(request.url.scheme, host)) {
        close(NetworkError.InsecureTransport(host))
        return@callbackFlow
    }
    val client = if (timeoutMs > 0) {
        newBuilder()
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    } else {
        this@executeForEvents
    }
    val call = client.newCall(request)
    if (callTimeoutMs > 0) {
        call.timeout().timeout(callTimeoutMs, TimeUnit.MILLISECONDS)
    }
    val reader = launch(Dispatchers.IO) {
        try {
            val response = call.await()
            if (response.code !in 200..299) {
                val raw = response.use {
                    RawResponse(
                        code = it.code,
                        contentType = it.header("Content-Type"),
                        retryAfter = it.header("Retry-After"),
                        body = it.body.string(),
                    )
                }
                close(raw.toNetworkError(host, secret))
                return@launch
            }
            response.use { readable ->
                val source = readable.body.source()
                val data = StringBuilder()
                var pending = false
                var firstLine = true
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val content = if (firstLine) {
                        firstLine = false
                        line.removePrefix("\uFEFF")
                    } else {
                        line
                    }
                    when {
                        content.isEmpty() -> {
                            if (pending) {
                                send(data.toString())
                                data.setLength(0)
                                pending = false
                            }
                        }
                        content.startsWith("data:") -> {
                            if (pending) data.append('\n')
                            data.append(content.removePrefix("data:").removePrefix(" "))
                            pending = true
                        }
                    }
                }
                if (pending) send(data.toString())
                close()
            }
        } catch (e: IOException) {
            close(e.toNetworkError())
        }
    }
    awaitClose {
        call.cancel()
        reader.cancel()
    }
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
