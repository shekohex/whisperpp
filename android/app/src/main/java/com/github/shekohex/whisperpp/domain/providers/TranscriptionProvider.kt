package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.data.ModelCapabilities
import java.io.File

/**
 * Batch (and optionally realtime) speech-to-text for one configured provider + model (§4.5).
 * Implementations never delete [transcribe]'s input file (G-09) and report failures as
 * [com.github.shekohex.whisperpp.core.network.NetworkError] or [ProviderConfigException].
 * Cancelling the caller cancels the in-flight request.
 */
interface TranscriptionProvider {
    val id: String
    val capabilities: ModelCapabilities

    suspend fun transcribe(audio: File, req: TranscriptionRequest): Result<Transcript>

    /** No batch endpoint: dictation always streams, whatever the realtime setting says. */
    val streamingOnly: Boolean
        get() = false

    /** Realtime session for capable models (WP-2.5); null when unsupported. */
    fun openStream(req: TranscriptionRequest): StreamingTranscriptionSession? = null

    /** Cheap reachability + auth check for "Test connection". */
    suspend fun verify(): Result<Unit>
}
