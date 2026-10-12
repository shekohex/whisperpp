package com.github.shekohex.whisperpp.domain.providers

import android.content.Context
import com.github.shekohex.whisperpp.R

/** The provider cannot be called as configured; nothing was sent. */
class ProviderConfigException(val problem: Problem) : Exception("Provider misconfigured: $problem") {
    enum class Problem {
        MISSING_ENDPOINT,
        INVALID_ENDPOINT,
        MISSING_API_KEY,
        MISSING_MODEL,

        /** The provider type has no implementation for this channel (e.g. Gemini STT, WP-2.2). */
        UNSUPPORTED_PROVIDER_TYPE,
        MODEL_NOT_FOUND,
        MODEL_LACKS_STT,
        MODEL_LACKS_TEXT,
    }

    fun toUserMessage(context: Context): String = when (problem) {
        Problem.MISSING_ENDPOINT -> context.getString(R.string.error_endpoint_unset)
        Problem.INVALID_ENDPOINT -> context.getString(R.string.provider_error_invalid_endpoint)
        Problem.MISSING_API_KEY -> context.getString(R.string.error_apikey_unset)
        Problem.MISSING_MODEL, Problem.MODEL_NOT_FOUND -> context.getString(R.string.provider_error_missing_model)
        Problem.UNSUPPORTED_PROVIDER_TYPE -> context.getString(R.string.provider_error_unsupported_type)
        Problem.MODEL_LACKS_STT -> context.getString(R.string.provider_error_model_lacks_stt)
        Problem.MODEL_LACKS_TEXT -> context.getString(R.string.provider_error_model_lacks_text)
    }
}
