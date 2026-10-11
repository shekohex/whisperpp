package com.github.shekohex.whisperpp.domain.providers

import kotlinx.coroutines.flow.Flow

/**
 * Realtime STT contract (§4.5.1). One instance per dictation run, owned by the dictation session.
 * Implementations: WP-2.5. Until then the legacy OpenAI realtime client is adapted to it.
 */
interface StreamingTranscriptionSession {
    val events: Flow<StreamEvent>

    /** Opens the stream and sends the session config. */
    suspend fun start()

    /** 16 kHz mono PCM16 chunks straight from the recorder. */
    suspend fun sendAudio(pcm16: ByteArray)

    /** Finalizes the stream and awaits the final transcript (bounded by a timeout). */
    suspend fun commit(): Result<Transcript>

    /** Closes without committing. Idempotent. */
    suspend fun cancel()
}
