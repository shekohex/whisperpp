package com.github.shekohex.whisperpp.domain.providers

/** Result of one transcription. [language] and [durationMs] are set when the provider reports them. */
data class Transcript(
    val text: String,
    val language: String? = null,
    val durationMs: Long? = null,
)
