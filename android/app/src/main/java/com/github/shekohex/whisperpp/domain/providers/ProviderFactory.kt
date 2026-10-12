package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException.Problem
import com.github.shekohex.whisperpp.providers.gemini.GeminiChat
import com.github.shekohex.whisperpp.providers.openai.OpenAiCompatibleChat
import com.github.shekohex.whisperpp.providers.openai.OpenAiCompatibleTranscription
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
            // Gemini audio transcription is WP-2.2.
            ProviderType.GEMINI -> unsupported(Problem.UNSUPPORTED_PROVIDER_TYPE)
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
                Result.success(OpenAiCompatibleChat(client, provider, model, credentials))
            ProviderType.GEMINI -> Result.success(GeminiChat(client, provider, model, credentials))
            ProviderType.WHISPER_ASR -> unsupported(Problem.UNSUPPORTED_PROVIDER_TYPE)
        }
    }

    fun enhancement(provider: ServiceProvider, modelId: String): Result<EnhancementProvider> {
        val model = provider.models.find { it.id == modelId } ?: return unsupported(Problem.MODEL_NOT_FOUND)
        return enhancement(provider, model)
    }

    /** Routes [request] to its provider/model and transcribes [audio]. */
    suspend fun transcribe(audio: File, request: TranscriptionRequest): Result<Transcript> {
        val provider = transcription(request.provider, request.modelId).getOrElse { return Result.failure(it) }
        return provider.transcribe(audio, request)
    }

    private fun <T> unsupported(problem: Problem): Result<T> = Result.failure(ProviderConfigException(problem))
}
