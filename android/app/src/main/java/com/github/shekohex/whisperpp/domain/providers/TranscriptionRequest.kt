package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.data.ServiceProvider

/**
 * Everything a batch transcription needs besides the audio file.
 *
 * [provider]/[modelId] route the request through [ProviderFactory]; implementations use their own
 * configuration. [addTrailingSpace] and [useContext] are dictation options applied by the caller;
 * [contextPrompt] is filled by the session.
 */
data class TranscriptionRequest(
    val provider: ServiceProvider,
    val modelId: String,
    val languageCode: String = "auto",
    /** Static prompt / vocabulary hint sent with every request. */
    val prompt: String = "",
    val contextPrompt: String? = null,
    val temperature: Float = 0f,
    /** Socket inactivity timeout (connect, each read/write); recordings vary too much for a total bound. */
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val mediaType: String = "audio/wav",
    val scriptConversion: ScriptConversion = ScriptConversion.NONE,
    val addTrailingSpace: Boolean = false,
    val useContext: Boolean = false,
    /** Custom vocabulary terms, set only for models with `capabilities.vocabulary`; providers with key-term support send them natively. */
    val vocabulary: List<String> = emptyList(),
) {
    /** Static prompt followed by the context prompt, as sent to the provider. */
    val fullPrompt: String
        get() {
            val context = contextPrompt.orEmpty()
            if (context.isEmpty()) return prompt
            return if (prompt.isNotEmpty()) "$prompt\n$context" else context
        }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
