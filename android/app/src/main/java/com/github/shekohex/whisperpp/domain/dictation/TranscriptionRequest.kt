package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.data.ServiceProvider

/** Everything a batch transcription needs besides the audio file. [contextPrompt] is filled by the session. */
data class TranscriptionRequest(
    val provider: ServiceProvider,
    val modelId: String,
    val languageCode: String,
    val mediaType: String,
    val postprocessing: String,
    val addTrailingSpace: Boolean,
    val timeoutMs: Int,
    val staticPrompt: String,
    val temperature: Float,
    val useContext: Boolean,
    val contextPrompt: String? = null,
)
