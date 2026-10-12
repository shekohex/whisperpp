package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import java.io.File

/** Resolved, ready-to-record configuration for one dictation run. */
data class StartPlan(
    val audioFile: File,
    val request: TranscriptionRequest,
    val streaming: StreamingTranscriptionSession? = null,
)
