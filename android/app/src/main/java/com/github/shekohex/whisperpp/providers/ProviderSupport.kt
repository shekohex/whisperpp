package com.github.shekohex.whisperpp.providers

import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Fixed bound for "Test connection" round trips. */
internal const val VERIFY_TIMEOUT_MS = 30_000L

/** Like [runCatching], but cancellation always propagates. */
internal inline fun <T> providerCall(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal fun ServiceProvider.baseUrlOrThrow(): HttpUrl {
    val trimmed = endpoint.trim()
    if (trimmed.isEmpty()) throw ProviderConfigException(ProviderConfigException.Problem.MISSING_ENDPOINT)
    return trimmed.toHttpUrlOrNull()
        ?: throw ProviderConfigException(ProviderConfigException.Problem.INVALID_ENDPOINT)
}

/** The API key to send, or null for [ProviderAuthMode.NO_AUTH]; throws when a required key is missing. */
internal fun ServiceProvider.apiKeyOrThrow(credentials: Credentials): String? {
    if (authMode == ProviderAuthMode.NO_AUTH) return null
    return credentials.apiKey?.takeIf { it.isNotBlank() }
        ?: throw ProviderConfigException(ProviderConfigException.Problem.MISSING_API_KEY)
}

/**
 * `response_format` for `/audio/transcriptions`: the GPT-4o transcription family (`gpt-4o-transcribe`,
 * `gpt-4o-mini-transcribe`, …) only accepts `json`; Whisper and compatible servers keep `text`.
 */
internal fun transcriptionResponseFormat(modelId: String): String {
    val name = modelId.trim().substringAfterLast('/').lowercase()
    return if (name.startsWith("gpt-") && name.contains("-transcribe")) "json" else "text"
}

/** Appends [suffix] unless the base URL already ends with it (users may paste full endpoints). */
internal fun HttpUrl.withPathSuffix(vararg suffix: String): HttpUrl {
    val segments = pathSegments.filter { it.isNotBlank() }
    val alreadyDerived = segments.size >= suffix.size && segments.takeLast(suffix.size) == suffix.toList()
    return if (alreadyDerived) this else newBuilder().addPathSegments(suffix.joinToString("/")).build()
}
