package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException.Problem
import com.github.shekohex.whisperpp.providers.deepgram.DeepgramTranscription
import com.github.shekohex.whisperpp.providers.elevenlabs.ElevenLabsTranscription
import com.github.shekohex.whisperpp.providers.gemini.GeminiChat
import com.github.shekohex.whisperpp.providers.gemini.GeminiDiscovery
import com.github.shekohex.whisperpp.providers.gemini.GeminiTranscription
import com.github.shekohex.whisperpp.providers.mistral.MistralTranscription
import com.github.shekohex.whisperpp.providers.openai.OpenAiCompatibleChat
import com.github.shekohex.whisperpp.providers.openai.OpenAiCompatibleDiscovery
import com.github.shekohex.whisperpp.providers.openai.OpenAiCompatibleTranscription
import com.github.shekohex.whisperpp.providers.openai.OpenAiResponses
import com.github.shekohex.whisperpp.providers.realtime.AssemblyAiDialect
import com.github.shekohex.whisperpp.providers.realtime.CartesiaDialect
import com.github.shekohex.whisperpp.providers.realtime.SonioxDialect
import com.github.shekohex.whisperpp.providers.realtime.SpeechmaticsDialect
import com.github.shekohex.whisperpp.providers.realtime.StreamingConfig
import com.github.shekohex.whisperpp.providers.realtime.StreamingDialect
import com.github.shekohex.whisperpp.providers.realtime.StreamingOnlyTranscription
import com.github.shekohex.whisperpp.providers.realtime.XaiDialect
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File

/**
 * Maps a configured [ServiceProvider] + [ModelConfig] to a provider implementation. Unsupported
 * combinations fail with [ProviderConfigException] instead of sending a request that cannot work.
 */
class ProviderFactory(
    private val client: OkHttpClient,
    private val apiKeyFor: (providerId: String) -> String?,
) {
    fun credentials(provider: ServiceProvider): Credentials {
        if (provider.authMode == ProviderAuthMode.NO_AUTH) return Credentials.NONE
        return Credentials(apiKeyFor(provider.id))
    }

    fun transcription(
        provider: ServiceProvider,
        model: ModelConfig,
        credentials: Credentials = credentials(provider),
    ): Result<TranscriptionProvider> {
        if (!model.capabilities.stt) return unsupported(Problem.MODEL_LACKS_STT)
        return when (provider.type) {
            ProviderType.OPENAI, ProviderType.WHISPER_ASR, ProviderType.CUSTOM ->
                Result.success(OpenAiCompatibleTranscription(client, provider, model, credentials))
            ProviderType.GEMINI -> Result.success(GeminiTranscription(client, provider, model, credentials))
            ProviderType.DEEPGRAM -> Result.success(DeepgramTranscription(client, provider, model, credentials))
            ProviderType.ELEVENLABS -> Result.success(ElevenLabsTranscription(client, provider, model, credentials))
            ProviderType.MISTRAL -> Result.success(MistralTranscription(client, provider, model, credentials))
            ProviderType.SONIOX -> streamingOnly(provider, model, credentials, ::SonioxDialect)
            ProviderType.SPEECHMATICS -> streamingOnly(provider, model, credentials, ::SpeechmaticsDialect)
            ProviderType.ASSEMBLYAI -> streamingOnly(provider, model, credentials, ::AssemblyAiDialect)
            ProviderType.XAI -> streamingOnly(provider, model, credentials, ::XaiDialect)
            ProviderType.CARTESIA -> streamingOnly(provider, model, credentials, ::CartesiaDialect)
        }
    }

    fun transcription(provider: ServiceProvider, modelId: String): Result<TranscriptionProvider> {
        val model = provider.models.find { it.id == modelId } ?: return unsupported(Problem.MODEL_NOT_FOUND)
        return transcription(provider, model)
    }

    fun enhancement(
        provider: ServiceProvider,
        model: ModelConfig,
        credentials: Credentials = credentials(provider),
    ): Result<EnhancementProvider> {
        if (!model.capabilities.text) return unsupported(Problem.MODEL_LACKS_TEXT)
        return when (provider.type) {
            ProviderType.OPENAI, ProviderType.CUSTOM ->
                if (usesResponsesApi(provider)) {
                    Result.success(OpenAiResponses(client, provider, model, credentials))
                } else {
                    Result.success(OpenAiCompatibleChat(client, provider, model, credentials))
                }
            ProviderType.GEMINI -> Result.success(GeminiChat(client, provider, model, credentials))
            // Speech providers have no chat endpoint here.
            ProviderType.WHISPER_ASR, ProviderType.DEEPGRAM, ProviderType.ELEVENLABS, ProviderType.MISTRAL,
            ProviderType.SONIOX, ProviderType.SPEECHMATICS, ProviderType.ASSEMBLYAI, ProviderType.XAI,
            ProviderType.CARTESIA -> unsupported(Problem.UNSUPPORTED_PROVIDER_TYPE)
        }
    }

    fun enhancement(provider: ServiceProvider, modelId: String): Result<EnhancementProvider> {
        val model = provider.models.find { it.id == modelId } ?: return unsupported(Problem.MODEL_NOT_FOUND)
        return enhancement(provider, model)
    }

    /**
     * Model listing for [provider] (§4.5.2), or null when it has none (Whisper ASR webservice
     * without an OpenAI-style `/v1` base) — the editor then keeps manual entry.
     */
    fun discovery(provider: ServiceProvider, credentials: Credentials = credentials(provider)): ModelDiscovery? {
        return when (provider.type) {
            ProviderType.OPENAI, ProviderType.CUSTOM -> OpenAiCompatibleDiscovery(client, provider, credentials)
            ProviderType.WHISPER_ASR -> if (hasOpenAiStyleBase(provider)) {
                OpenAiCompatibleDiscovery(client, provider, credentials)
            } else {
                null
            }
            ProviderType.GEMINI -> GeminiDiscovery(client, provider, credentials)
            // No listing endpoint worth using: models are entered by hand.
            ProviderType.DEEPGRAM, ProviderType.ELEVENLABS, ProviderType.MISTRAL, ProviderType.SONIOX,
            ProviderType.SPEECHMATICS, ProviderType.ASSEMBLYAI, ProviderType.XAI, ProviderType.CARTESIA -> null
        }
    }

    private fun streamingOnly(
        provider: ServiceProvider,
        model: ModelConfig,
        credentials: Credentials,
        dialect: (StreamingConfig) -> StreamingDialect,
    ): Result<TranscriptionProvider> = Result.success(StreamingOnlyTranscription(client, provider, model, credentials, dialect))

    /**
     * Real OpenAI (`api.openai.com`) or an explicitly pasted `…/responses` endpoint speaks the
     * Responses API; other OpenAI-compatible servers stay on `chat/completions` since not all of
     * them implement `/responses`.
     */
    private fun usesResponsesApi(provider: ServiceProvider): Boolean {
        val url = provider.endpoint.trim().toHttpUrlOrNull() ?: return false
        if (url.host == "api.openai.com") return true
        return url.pathSegments.lastOrNull { it.isNotBlank() } == "responses"
    }

    private fun hasOpenAiStyleBase(provider: ServiceProvider): Boolean =
        provider.endpoint.trim().toHttpUrlOrNull()?.pathSegments?.contains("v1") == true

    /** Routes [request] to its provider/model and transcribes [audio]. */
    suspend fun transcribe(audio: File, request: TranscriptionRequest): Result<Transcript> {
        val provider = transcription(request.provider, request.modelId).getOrElse { return Result.failure(it) }
        return provider.transcribe(audio, request)
    }

    private fun <T> unsupported(problem: Problem): Result<T> = Result.failure(ProviderConfigException(problem))
}
