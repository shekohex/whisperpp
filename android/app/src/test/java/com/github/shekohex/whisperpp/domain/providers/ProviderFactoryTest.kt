package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.providers.gemini.GeminiChat
import com.github.shekohex.whisperpp.providers.gemini.GeminiTranscription
import com.github.shekohex.whisperpp.providers.openai.OpenAiCompatibleChat
import com.github.shekohex.whisperpp.providers.openai.OpenAiCompatibleTranscription
import com.github.shekohex.whisperpp.providers.openai.OpenAiResponses
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFactoryTest {
    private val factory = ProviderFactory(OkHttpClient()) { id -> "key-$id" }

    @Test
    fun routesProviderTypesToImplementations() {
        val stt = ModelConfig("m", "m", ModelCapabilities(stt = true, text = true))

        assertTrue(factory.transcription(provider(ProviderType.OPENAI), stt).getOrThrow() is OpenAiCompatibleTranscription)
        assertTrue(factory.transcription(provider(ProviderType.WHISPER_ASR), stt).getOrThrow() is OpenAiCompatibleTranscription)
        assertTrue(factory.transcription(provider(ProviderType.CUSTOM), stt).getOrThrow() is OpenAiCompatibleTranscription)
        assertTrue(factory.transcription(provider(ProviderType.GEMINI), stt).getOrThrow() is GeminiTranscription)
        assertTrue(factory.enhancement(provider(ProviderType.OPENAI), stt).getOrThrow() is OpenAiCompatibleChat)
        assertTrue(factory.enhancement(provider(ProviderType.GEMINI), stt).getOrThrow() is GeminiChat)
    }

    @Test
    fun unsupportedCombinations_failWithTypedProblem() {
        val both = ModelConfig("m", "m", ModelCapabilities(stt = true, text = true))

        assertProblem(ProviderConfigException.Problem.UNSUPPORTED_PROVIDER_TYPE, factory.enhancement(provider(ProviderType.WHISPER_ASR), both))
        assertProblem(ProviderConfigException.Problem.MODEL_LACKS_STT, factory.transcription(provider(ProviderType.OPENAI), ModelConfig("t", "t")))
        assertProblem(
            ProviderConfigException.Problem.MODEL_LACKS_TEXT,
            factory.enhancement(provider(ProviderType.OPENAI), ModelConfig("s", "s", ModelCapabilities.stt(ProviderType.OPENAI))),
        )
        assertProblem(ProviderConfigException.Problem.MODEL_NOT_FOUND, factory.transcription(provider(ProviderType.OPENAI), "missing"))
    }

    @Test
    fun credentials_comeFromTheSecretSource_unlessNoAuth() {
        assertEquals(Credentials("key-p"), factory.credentials(provider(ProviderType.OPENAI)))
        assertEquals(Credentials.NONE, factory.credentials(provider(ProviderType.WHISPER_ASR).copy(authMode = ProviderAuthMode.NO_AUTH)))
        assertTrue(!Credentials("secret").toString().contains("secret"))
    }

    @Test
    fun openAiRealHost_orExplicitResponsesPath_routeToResponsesApi() {
        val both = ModelConfig("m", "m", ModelCapabilities(stt = true, text = true))

        assertTrue(factory.enhancement(provider(ProviderType.OPENAI, "https://api.openai.com/v1"), both).getOrThrow() is OpenAiResponses)
        assertTrue(factory.enhancement(provider(ProviderType.OPENAI, "https://api.openai.com/v1/chat/completions"), both).getOrThrow() is OpenAiResponses)
        assertTrue(factory.enhancement(provider(ProviderType.CUSTOM, "https://proxy.example.com/v1/responses"), both).getOrThrow() is OpenAiResponses)

        assertTrue(factory.enhancement(provider(ProviderType.CUSTOM, "https://openrouter.ai/api/v1"), both).getOrThrow() is OpenAiCompatibleChat)
        assertTrue(factory.enhancement(provider(ProviderType.OPENAI, "https://example.com/v1"), both).getOrThrow() is OpenAiCompatibleChat)
    }

    private fun assertProblem(expected: ProviderConfigException.Problem, result: Result<*>) {
        assertEquals(expected, (result.exceptionOrNull() as ProviderConfigException).problem)
    }

    private fun provider(type: ProviderType, endpoint: String = "https://example.com/v1") = ServiceProvider(
        id = "p",
        name = "P",
        type = type,
        endpoint = endpoint,
        models = emptyList(),
    )
}
