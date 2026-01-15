package com.example.whispertoinput.data

import java.util.UUID

object Presets {
    val OpenAIWhisper = ServiceProvider(
        id = UUID.randomUUID().toString(),
        name = "OpenAI Whisper",
        type = ProviderType.OPENAI,
        endpoint = "https://api.openai.com/v1/audio/transcriptions",
        apiKey = "",
        models = listOf(
            ModelConfig(id = "whisper-1", name = "Whisper 1")
        ),
        temperature = 0.0f,
        prompt = "",
        languageCode = "auto"
    )

    val OpenAIChat = ServiceProvider(
        id = UUID.randomUUID().toString(),
        name = "OpenAI Chat",
        type = ProviderType.OPENAI,
        endpoint = "https://api.openai.com/v1/chat/completions",
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
            ModelConfig(id = "gemini-1.5-flash", name = "Gemini 1.5 Flash"),
            ModelConfig(id = "gemini-1.5-pro", name = "Gemini 1.5 Pro"),
            ModelConfig(id = "gemini-2.0-flash", name = "Gemini 2.0 Flash")
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
        apiKey = "", // Usually no key
        models = emptyList(), // Managed by server
        temperature = 0.0f,
        prompt = "",
        languageCode = "auto"
    )

    val AllPresets = listOf(OpenAIWhisper, OpenAIChat, GoogleGemini, WhisperASR)
}
