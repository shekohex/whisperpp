package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeRaw
import com.github.shekohex.whisperpp.core.text.Vocabulary
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.isStreamingOnly
import com.github.shekohex.whisperpp.domain.providers.BatchUnsupportedException
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.Transcript
import com.github.shekohex.whisperpp.domain.providers.TranscriptionProvider
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.ChineseScriptConverter
import com.github.shekohex.whisperpp.providers.VERIFY_TIMEOUT_MS
import com.github.shekohex.whisperpp.providers.apiKeyOrThrow
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import com.github.shekohex.whisperpp.providers.providerCall
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Base of the cloud providers that dictate over their own WebSocket protocol (Deepgram, ElevenLabs,
 * Mistral, ...). Every STT model streams through a [WebSocketStreamingSession] with the provider's
 * [StreamingDialect]; subclasses add a batch endpoint ([batchText]) when the provider has one, which
 * the dictation session uses when the stream fails. Providers without one fail with
 * [BatchUnsupportedException] and always stream. The input file is never deleted here (G-09).
 */
internal abstract class CloudRealtimeTranscription(
    protected val client: OkHttpClient,
    protected val provider: ServiceProvider,
    protected val model: ModelConfig,
    protected val credentials: Credentials,
    private val postProcess: suspend (String, ScriptConversion) -> String = ChineseScriptConverter::convert,
) : TranscriptionProvider {
    override val id: String = provider.id

    // The provider's whole point is streaming: no per-model flag needed.
    override val capabilities: ModelCapabilities = model.capabilities.let { declared ->
        if (declared.stt) declared.copy(realtime = true) else declared
    }

    override val streamingOnly: Boolean
        get() = provider.type.isStreamingOnly

    protected abstract fun dialect(config: StreamingConfig): StreamingDialect

    /** Transcribes the recorded [audio] through the provider's batch endpoint. */
    protected open suspend fun batchText(audio: File, req: TranscriptionRequest, apiKey: String): String =
        throw BatchUnsupportedException(provider.type.toString())

    /** Request proving the key works (a cheap authenticated GET), or null when the provider has none. */
    protected open fun verifyRequest(apiKey: String): Request? = null

    override suspend fun transcribe(audio: File, req: TranscriptionRequest): Result<Transcript> = providerCall {
        val text = batchText(audio, req, requireApiKey())
        Transcript(text = postProcess(text.trim(), req.scriptConversion))
    }

    override fun openStream(req: TranscriptionRequest): StreamingTranscriptionSession {
        val sockets = client.newBuilder()
            .pingInterval(WebSocketStreamingSession.PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
        return WebSocketStreamingSession(sockets, req, { dialect(streamingConfig(req)) }, postProcess)
    }

    /** Any answer below 500 other than 401/403/429 proves the server is reachable. */
    override suspend fun verify(): Result<Unit> = providerCall {
        val apiKey = requireApiKey()
        val request = verifyRequest(apiKey) ?: throw BatchUnsupportedException(provider.type.toString())
        val response = client.executeRaw(request, VERIFY_TIMEOUT_MS)
        val host = request.url.host
        when {
            response.code == 401 || response.code == 403 -> throw NetworkError.Unauthorized(response.code, host)
            response.code == 429 -> throw NetworkError.RateLimited(host, null)
            response.code >= 500 -> throw NetworkError.Http(response.code, host, null)
        }
    }

    protected fun requireApiKey(): String =
        provider.apiKeyOrThrow(credentials) ?: throw ProviderConfigException(ProviderConfigException.Problem.MISSING_API_KEY)

    internal fun streamingConfig(req: TranscriptionRequest) = StreamingConfig(
        baseUrl = provider.baseUrlOrThrow(),
        apiKey = requireApiKey(),
        modelId = model.id.trim(),
        language = StreamingConfig.languageOrNull(req.languageCode),
        vocabulary = Vocabulary.normalize(req.vocabulary),
    )
}
