package com.github.shekohex.whisperpp.providers.realtime

import okhttp3.HttpUrl

/**
 * Everything a [StreamingDialect] needs to build its session. [language] is null for auto-detect;
 * [vocabulary] is already normalized. [apiKey] is never logged.
 */
internal class StreamingConfig(
    val baseUrl: HttpUrl,
    val apiKey: String,
    val modelId: String,
    val language: String?,
    val vocabulary: List<String>,
) {
    override fun toString(): String = "StreamingConfig(model=$modelId, language=$language)"

    companion object {
        /** The language code to send, or null for "auto"/blank. */
        fun languageOrNull(code: String): String? = code.trim().takeIf { it.isNotEmpty() && !it.equals("auto", ignoreCase = true) }
    }
}
