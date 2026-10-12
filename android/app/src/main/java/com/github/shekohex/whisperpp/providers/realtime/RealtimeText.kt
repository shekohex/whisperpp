package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.providers.withPathSuffix
import okhttp3.HttpUrl
import org.json.JSONException
import org.json.JSONObject

/** Small helpers shared by the dialects. */
internal object RealtimeText {
    private val AUTH_KINDS = setOf("auth_error", "unauthorized", "invalid_api_key", "authentication_error", "forbidden", "401", "403")
    private val RATE_KINDS = setOf(
        "quota_exceeded", "rate_limited", "resource_exhausted", "rate_limit_exceeded", "too_many_requests", "429",
    )
    private const val MAX_KIND_CHARS = 64

    /** Non-blank [parts] trimmed and joined with a single space. */
    fun join(parts: List<String>): String = parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    /** [committed] followed by the in-progress [current] segment. */
    fun cumulative(committed: List<String>, current: String): String = join(committed + current)

    fun parse(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (_: JSONException) {
        null
    }

    /** Maps a provider error kind (never its message, which may echo user content) to a typed error. */
    fun errorFor(host: String, kind: String): NetworkError {
        val normalized = kind.trim().lowercase()
        return when {
            normalized in AUTH_KINDS -> NetworkError.Unauthorized(401, host)
            normalized in RATE_KINDS -> NetworkError.RateLimited(host, null)
            else -> NetworkError.InvalidResponse(
                "realtime error ${normalized.ifEmpty { "unknown" }.take(MAX_KIND_CHARS)}",
            )
        }
    }

    /** Appends `v1` unless the base already has it, then [suffix] (users may paste a bare host). */
    fun v1Url(base: HttpUrl, vararg suffix: String): HttpUrl {
        val segments = base.pathSegments.filter { it.isNotBlank() }
        val versioned = if ("v1" in segments) base else base.newBuilder().addPathSegment("v1").build()
        return versioned.withPathSuffix(*suffix)
    }
}
