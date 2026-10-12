package com.github.shekohex.whisperpp.ui.providers

/** Outcome of a provider test shown in the raw-response dialog. [body] is already redacted. */
data class ProviderTestResult(
    val title: String,
    val status: Int?,
    val body: String,
    val error: String?,
)
