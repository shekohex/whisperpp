package com.github.shekohex.whisperpp.domain.dictation

/** Result of the transcribe step (streamed or batch) before post-processing and commit. */
internal sealed interface TranscriptionOutcome {
    data class Text(val text: String) : TranscriptionOutcome
    data object Empty : TranscriptionOutcome
    data class Failed(val message: String) : TranscriptionOutcome

    companion object {
        fun fromBatch(batch: Result<String>): TranscriptionOutcome {
            val text = batch.getOrNull()
            if (!text.isNullOrBlank()) return Text(text)
            return batch.exceptionOrNull()
                ?.let { Failed(it.message?.takeIf(String::isNotBlank) ?: it::class.java.simpleName) }
                ?: Empty
        }
    }
}
