package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.dictation.FocusKey

/** Optional post-commit enhancement step. Failure always keeps the raw text. */
interface DictationEnhancer {
    data class Request(
        val focus: FocusKey,
        val rawText: String,
        val contextPrompt: String?,
        val options: DictationOptions = DictationOptions(),
    )

    sealed interface Result {
        /** [providerId]/[modelId] identify the text model for analytics and history. */
        data class Enhanced(
            val text: String,
            val label: String,
            val providerId: String? = null,
            val modelId: String? = null,
        ) : Result

        /** Raw text kept; [notice] explains why when the user should know. */
        data class Kept(val notice: String? = null, val isError: Boolean = false) : Result
    }

    /** Cheap check (settings, skip rules) so the UI only shows enhancing when work will happen. */
    suspend fun isEnabled(request: Request): Boolean

    suspend fun enhance(request: Request): Result
}
