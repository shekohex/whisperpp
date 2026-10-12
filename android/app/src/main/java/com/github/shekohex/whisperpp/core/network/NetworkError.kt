package com.github.shekohex.whisperpp.core.network

import android.content.Context
import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R

/**
 * Typed failure of one provider HTTP call. Messages never contain credentials; [Http.bodySnippet]
 * is capped and redacted before it is stored.
 */
sealed class NetworkError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** HTTP 401/403. */
    class Unauthorized(val status: Int, val host: String) :
        NetworkError("Unauthorized (HTTP $status, host: $host)")

    /** HTTP 429; [retryAfterMs] parsed from `Retry-After` when present. */
    class RateLimited(val host: String, val retryAfterMs: Long?) :
        NetworkError("Rate limited (host: $host)")

    class Timeout(cause: Throwable? = null) : NetworkError("Request timed out", cause)

    /** Any other non-2xx response. */
    class Http(val status: Int, val host: String, val bodySnippet: String?) :
        NetworkError("HTTP $status (host: $host)")

    /** DNS failure, refused connection, no route. */
    class Offline(cause: Throwable? = null) : NetworkError("Network unavailable", cause)

    /** 2xx response that could not be parsed or carried no usable content. */
    class InvalidResponse(
        val detail: String,
        cause: Throwable? = null,
        /** Specific user text for failures the generic "unexpected response" message would obscure. */
        @StringRes val userMessageRes: Int? = null,
    ) :
        NetworkError("Invalid response: $detail", cause)

    /** Cleartext to a non-local host (J-01); the request is never sent. */
    class InsecureTransport(val host: String) : NetworkError("Insecure HTTP to $host")

    fun toUserMessage(context: Context): String = when (this) {
        is Unauthorized -> context.getString(R.string.network_error_unauthorized, host)
        is RateLimited -> context.getString(R.string.network_error_rate_limited, host)
        is Timeout -> context.getString(R.string.network_error_timeout)
        is Http -> context.getString(R.string.network_error_http, status, host)
        is Offline -> context.getString(R.string.network_error_offline)
        is InvalidResponse -> context.getString(userMessageRes ?: R.string.network_error_invalid_response)
        is InsecureTransport -> context.getString(R.string.error_insecure_http_endpoint)
    }
}
