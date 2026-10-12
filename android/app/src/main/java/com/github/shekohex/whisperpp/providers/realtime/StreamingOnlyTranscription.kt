package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import okhttp3.OkHttpClient

/**
 * Soniox, Speechmatics, AssemblyAI, xAI and Cartesia: realtime only. The base class's batch call
 * fails with a typed "batch transcription not supported" error, so dictation always streams.
 */
internal class StreamingOnlyTranscription(
    client: OkHttpClient,
    provider: ServiceProvider,
    model: ModelConfig,
    credentials: Credentials,
    private val dialectFor: (StreamingConfig) -> StreamingDialect,
) : CloudRealtimeTranscription(client, provider, model, credentials) {
    override fun dialect(config: StreamingConfig): StreamingDialect = dialectFor(config)
}
