package com.github.shekohex.whisperpp.domain.providers

import android.content.Context
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.network.NetworkError

/** User-facing text for a provider failure; never includes credentials or response bodies. */
fun Throwable.toProviderUserMessage(context: Context): String = when (this) {
    is NetworkError -> toUserMessage(context)
    is ProviderConfigException -> toUserMessage(context)
    else -> message?.trim()?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.provider_error_unknown)
}
