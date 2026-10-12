package com.github.shekohex.whisperpp.domain.dictation

/** Result of the transcribe step (streamed or batch) before post-processing and commit. */
internal sealed interface TranscriptionOutcome {
    data class Text(val text: String) : TranscriptionOutcome
    data object Empty : TranscriptionOutcome

    /** [network]: the provider could not be reached or answered with a service error. */
    data class Failed(val message: String, val network: Boolean = false) : TranscriptionOutcome

    companion object {
        fun fromBatch(batch: Result<String>): TranscriptionOutcome {
            val text = batch.getOrNull()
            if (!text.isNullOrBlank()) return Text(text)
            val error = batch.exceptionOrNull() ?: return Empty
            return Failed(error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName, error.isNetworkFailure())
        }
    }
}
