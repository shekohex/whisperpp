package com.github.shekohex.whisperpp.domain.providers

import kotlinx.coroutines.flow.Flow

/**
 * Realtime STT contract (§4.5.1). One instance per dictation run, owned by the dictation session,
 * which always records the WAV too and falls back to batch when [commit] fails.
 */
interface StreamingTranscriptionSession {
    val events: Flow<StreamEvent>

    /** Opens the stream and sends the session config (model, language, vocabulary, VAD off). */
    suspend fun start()

    /** 16 kHz mono PCM16 chunks straight from the recorder, in capture order. */
    suspend fun sendAudio(pcm16: ByteArray)

    /**
     * Finalizes everything sent so far and awaits the transcript produced for it. Only a final that
     * arrives after the commit counts; a stale final or partial is never returned. Fails with
     * [com.github.shekohex.whisperpp.core.network.NetworkError.Timeout] when none arrives in time.
     * Closes the stream either way.
     */
    suspend fun commit(): Result<Transcript>

    /** Closes without committing. Idempotent. */
    suspend fun cancel()
}
