package com.example.whispertoinput.data

import java.util.UUID

enum class ProviderType {
    OPENAI, WHISPER_ASR, GEMINI, CUSTOM;
    
    override fun toString(): String {
        return when(this) {
            OPENAI -> "OpenAI"
            WHISPER_ASR -> "Whisper ASR"
            GEMINI -> "Google Gemini"
            CUSTOM -> "Custom"
        }
    }
}

enum class ThinkingType {
    BUDGET, LEVEL
}

data class ModelConfig(
    val id: String,
    val name: String,
    val isThinking: Boolean = false
)

data class ServiceProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProviderType,
    val endpoint: String,
    val apiKey: String,
    val models: List<ModelConfig>,
    // Configuration
    val temperature: Float = 0.0f,
    val prompt: String = "",
    val languageCode: String = "auto", // "auto" means detect, or force specific language
    val timeout: Int = 10000, // Default 10s
    // Thinking
    val thinkingEnabled: Boolean = false,
    val thinkingType: ThinkingType = ThinkingType.LEVEL,
    val thinkingBudget: Int = 4096,
    val thinkingLevel: String = "medium"
)


data class LanguageProfile(
    val languageCode: String,
    val transcriptionProviderId: String,
    val transcriptionModelId: String,
    val smartFixProviderId: String,
    val smartFixModelId: String
)
