package com.github.shekohex.whisperpp.data

object PromptComposer {
    fun compose(
        basePrompt: String,
        profileAppend: String?,
        appAppend: String?,
        appendMode: AppendMode,
    ): String {
        val base = basePrompt.trim().takeIf { it.isNotBlank() }.orEmpty()

        if (appendMode == AppendMode.NO_APPEND) {
            return base
        }

        val segments = listOf(
            base,
            profileAppend?.trim().orEmpty(),
            appAppend?.trim().orEmpty(),
        ).filter { it.isNotBlank() }

        return segments.joinToString(separator = "\n\n")
    }
}
