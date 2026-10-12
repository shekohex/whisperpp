package com.github.shekohex.whisperpp.core.text

/** One dictionary rule: any of [originals] (case-insensitive) becomes [replacement]. */
data class Replacement(
    val originals: List<String>,
    val replacement: String,
)
