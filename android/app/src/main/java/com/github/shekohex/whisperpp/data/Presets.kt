package com.github.shekohex.whisperpp.data

import java.util.UUID

object Presets {
    val OpenAIWhisper = ServiceProvider(
        id = UUID.randomUUID().toString(),
        name = "OpenAI Whisper",
        type = ProviderType.OPENAI,
        endpoint = "https://api.openai.com/v1",
        apiKey = "",
        models = listOf(
            ModelConfig(id = "whisper-1", name = "Whisper 1", capabilities = ModelCapabilities.stt(ProviderType.OPENAI))
        ),
        temperature = 0.0f,
        prompt = "",
        languageCode = "auto"
    )

    val OpenAIChat = ServiceProvider(
        id = UUID.randomUUID().toString(),
        name = "OpenAI Chat",
        type = ProviderType.OPENAI,
        endpoint = "https://api.openai.com/v1",
        apiKey = "",
        models = listOf(
            ModelConfig(id = "gpt-4o", name = "GPT-4o"),
            ModelConfig(id = "gpt-4-turbo", name = "GPT-4 Turbo"),
            ModelConfig(id = "gpt-3.5-turbo", name = "GPT-3.5 Turbo")
        ),
        temperature = 0.7f,
        prompt = "You are a helpful assistant that fixes text.",
        languageCode = "auto"
    )

    val GoogleGemini = ServiceProvider(
        id = UUID.randomUUID().toString(),
        name = "Google Gemini",
        type = ProviderType.GEMINI,
        endpoint = "https://generativelanguage.googleapis.com/v1beta",
        apiKey = "",
        models = listOf(
            ModelConfig(id = "gemini-1.5-flash", name = "Gemini 1.5 Flash", capabilities = ModelCapabilities.GEMINI_AUDIO),
            ModelConfig(id = "gemini-1.5-pro", name = "Gemini 1.5 Pro", capabilities = ModelCapabilities.GEMINI_AUDIO),
            ModelConfig(id = "gemini-2.0-flash", name = "Gemini 2.0 Flash", capabilities = ModelCapabilities.GEMINI_AUDIO)
        ),
        temperature = 0.7f,
        prompt = "You are a helpful assistant.",
        languageCode = "auto"
    )

    val WhisperASR = ServiceProvider(
        id = UUID.randomUUID().toString(),
        name = "Whisper ASR Service",
        type = ProviderType.WHISPER_ASR,
        endpoint = "http://YOUR_SERVER_IP:9000/asr",
        authMode = ProviderAuthMode.NO_AUTH,
        apiKey = "", // Usually no key
        models = emptyList(), // Managed by server
        temperature = 0.0f,
        prompt = "",
        languageCode = "auto"
    )

    val Deepgram = cloudSttPreset(
        type = ProviderType.DEEPGRAM,
        endpoint = "https://api.deepgram.com/v1",
        models = listOf("nova-3" to "Nova 3", "nova-3-medical" to "Nova 3 Medical"),
    )

    val ElevenLabs = cloudSttPreset(
        type = ProviderType.ELEVENLABS,
        endpoint = "https://api.elevenlabs.io/v1",
        models = listOf("scribe_v2" to "Scribe v2"),
    )

    val Mistral = cloudSttPreset(
        type = ProviderType.MISTRAL,
        endpoint = "https://api.mistral.ai/v1",
        models = listOf("voxtral-mini-latest" to "Voxtral Mini"),
    )

    val AllPresets = listOf(OpenAIWhisper, OpenAIChat, GoogleGemini, WhisperASR, Deepgram, ElevenLabs, Mistral)

    /** A cloud realtime speech provider with its STT models (realtime and vocabulary included). */
    private fun cloudSttPreset(type: ProviderType, endpoint: String, models: List<Pair<String, String>>) = ServiceProvider(
        id = UUID.randomUUID().toString(),
        name = type.toString(),
        type = type,
        endpoint = endpoint,
        apiKey = "",
        models = models.map { (id, name) -> ModelConfig(id = id, name = name, capabilities = ModelCapabilities.stt(type)) },
        temperature = 0.0f,
        prompt = "",
        languageCode = "auto"
    )
}
