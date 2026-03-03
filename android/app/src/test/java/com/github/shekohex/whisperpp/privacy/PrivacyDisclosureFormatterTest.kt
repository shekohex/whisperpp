package com.github.shekohex.whisperpp.privacy

import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ModelKind
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyDisclosureFormatterTest {

    @Test
    fun openAiBaseUrl_derivesAudioAndChatPaths() {
        val provider = ServiceProvider(
            id = "p1",
            name = "OpenAI",
            type = ProviderType.OPENAI,
            endpoint = "https://api.openai.com/v1",
            models = listOf(ModelConfig(id = "whisper-1", name = "Whisper 1", kind = ModelKind.STT)),
        )

        val dictation = PrivacyDisclosureFormatter.disclosureForDictation(
            provider = provider,
            selectedModelId = "whisper-1",
            useContext = false,
        )
        assertEquals("https://api.openai.com", dictation.endpoints[0].baseUrl)
        assertEquals("/v1/audio/transcriptions", dictation.endpoints[0].path)

        val enhancement = PrivacyDisclosureFormatter.disclosureForEnhancement(
            provider = provider,
            selectedModelId = "gpt-4o",
            useContext = false,
        )
        assertEquals("https://api.openai.com", enhancement.endpoints[0].baseUrl)
        assertEquals("/v1/chat/completions", enhancement.endpoints[0].path)
    }

    @Test
    fun geminiBaseUrl_derivesGenerateContentPath() {
        val provider = ServiceProvider(
            id = "p2",
            name = "Gemini",
            type = ProviderType.GEMINI,
            endpoint = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf(ModelConfig(id = "gemini-1.5-pro", name = "Gemini 1.5 Pro", kind = ModelKind.MULTIMODAL)),
        )

        val enhancement = PrivacyDisclosureFormatter.disclosureForEnhancement(
            provider = provider,
            selectedModelId = "gemini-1.5-pro",
            useContext = false,
        )
        assertEquals("https://generativelanguage.googleapis.com", enhancement.endpoints[0].baseUrl)
        assertEquals("/v1beta/models/gemini-1.5-pro:generateContent", enhancement.endpoints[0].path)
    }

    @Test
    fun baseUrlFormatting_preservesNonDefaultPort() {
        val provider = ServiceProvider(
            id = "p3",
            name = "Local OpenAI",
            type = ProviderType.OPENAI,
            endpoint = "http://localhost:8080/v1",
            models = listOf(ModelConfig(id = "gpt-4o", name = "GPT-4o", kind = ModelKind.TEXT)),
        )

        val enhancement = PrivacyDisclosureFormatter.disclosureForEnhancement(
            provider = provider,
            selectedModelId = "gpt-4o",
            useContext = false,
        )
        assertEquals("http://localhost:8080", enhancement.endpoints[0].baseUrl)
        assertEquals("/v1/chat/completions", enhancement.endpoints[0].path)
    }
}
