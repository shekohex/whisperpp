package com.github.shekohex.whisperpp.privacy

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ModelCapabilities
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
            models = listOf(ModelConfig(id = "whisper-1", name = "Whisper 1", capabilities = ModelCapabilities.stt(ProviderType.OPENAI))),
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
            models = listOf(ModelConfig(id = "gemini-1.5-pro", name = "Gemini 1.5 Pro", capabilities = ModelCapabilities.TEXT)),
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
            models = listOf(ModelConfig(id = "gpt-4o", name = "GPT-4o", capabilities = ModelCapabilities.TEXT)),
        )

        val enhancement = PrivacyDisclosureFormatter.disclosureForEnhancement(
            provider = provider,
            selectedModelId = "gpt-4o",
            useContext = false,
        )
        assertEquals("http://localhost:8080", enhancement.endpoints[0].baseUrl)
        assertEquals("/v1/chat/completions", enhancement.endpoints[0].path)
    }

    @Test
    fun commandDisclosure_listsInstructionAudioAndTextTransformHops() {
        val sttProvider = ServiceProvider(
            id = "stt",
            name = "STT",
            type = ProviderType.OPENAI,
            endpoint = "https://stt.example.com/v1",
            models = listOf(ModelConfig(id = "whisper-1", name = "Whisper 1", capabilities = ModelCapabilities.stt(ProviderType.OPENAI))),
        )
        val textProvider = ServiceProvider(
            id = "text",
            name = "Gemini",
            type = ProviderType.GEMINI,
            endpoint = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf(ModelConfig(id = "gemini-1.5-pro", name = "Gemini 1.5 Pro", capabilities = ModelCapabilities.TEXT)),
        )

        val disclosure = PrivacyDisclosureFormatter.disclosureForCommand(
            sttProvider = sttProvider,
            sttModelId = "whisper-1",
            textProvider = textProvider,
            textModelId = "gemini-1.5-pro",
            useContext = true,
        )

        assertEquals(R.string.privacy_disclosure_command_title, disclosure.title)
        assertEquals(R.string.privacy_disclosure_command_data, disclosure.dataSent)
        assertEquals(2, disclosure.endpoints.size)
        assertEquals(R.string.privacy_endpoint_instruction_audio, disclosure.endpoints[0].label)
        assertEquals("https://stt.example.com", disclosure.endpoints[0].baseUrl)
        assertEquals("/v1/audio/transcriptions", disclosure.endpoints[0].path)
        assertEquals(R.string.privacy_endpoint_text_transform, disclosure.endpoints[1].label)
        assertEquals("https://generativelanguage.googleapis.com", disclosure.endpoints[1].baseUrl)
        assertEquals("/v1beta/models/gemini-1.5-pro:generateContent", disclosure.endpoints[1].path)
        assertEquals(R.string.privacy_disclosure_command_context_on, disclosure.contextLine)
    }

    @Test
    fun invalidAndMissingProviders_renderSafeCommandDisclosureRows() {
        val invalidProvider = ServiceProvider(
            id = "broken",
            name = "Broken",
            type = ProviderType.OPENAI,
            endpoint = "not a url",
            models = listOf(ModelConfig(id = "gpt-4o", name = "GPT-4o", capabilities = ModelCapabilities.TEXT)),
        )

        val disclosure = PrivacyDisclosureFormatter.disclosureForCommand(
            sttProvider = null,
            sttModelId = "",
            textProvider = invalidProvider,
            textModelId = "gpt-4o",
            useContext = false,
        )

        assertEquals(PrivacyDisclosureFormatter.EndpointStatus.NOT_CONFIGURED, disclosure.endpoints[0].status)
        assertEquals("", disclosure.endpoints[0].baseUrl)
        assertEquals(PrivacyDisclosureFormatter.EndpointStatus.INVALID, disclosure.endpoints[1].status)
        assertEquals("", disclosure.endpoints[1].path)
        assertEquals(R.string.privacy_disclosure_command_context_off, disclosure.contextLine)
    }
}
