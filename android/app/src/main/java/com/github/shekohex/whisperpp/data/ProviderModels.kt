package com.github.shekohex.whisperpp.data

import com.google.gson.annotations.SerializedName
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

enum class ModelKind {
    STT,
    TEXT,
    MULTIMODAL,
}

enum class ProviderAuthMode {
    API_KEY,
    NO_AUTH,
}

/**
 * What a model can do (§4.5). Source of truth for selection and routing; filled by the v3 migration
 * from the legacy fields, by discovery (WP-2.6) or by the user.
 */
data class ModelCapabilities(
    val stt: Boolean = false,
    val text: Boolean = false,
    val realtime: Boolean = false,
    val thinking: Boolean = false,
    val vocabulary: Boolean = false,
) {
    /** Closest legacy [ModelKind], kept for older readers of the persisted JSON. */
    val legacyKind: ModelKind
        get() = when {
            stt && text -> ModelKind.MULTIMODAL
            stt -> ModelKind.STT
            else -> ModelKind.TEXT
        }

    /** Editor shortcut: switches the STT/text flags, keeping the others where they still apply. */
    fun withKind(kind: ModelKind): ModelCapabilities {
        val newStt = kind != ModelKind.TEXT
        return copy(
            stt = newStt,
            text = kind != ModelKind.STT,
            realtime = realtime && newStt,
            vocabulary = vocabulary && newStt,
        )
    }

    companion object {
        val TEXT = ModelCapabilities(text = true)

        /** Gemini chat models take inline audio through `generateContent` (G-04, WP-2.2). */
        val GEMINI_AUDIO = ModelCapabilities(
            stt = true,
            text = true,
            vocabulary = ProviderType.GEMINI.acceptsTranscriptionPrompt,
        )

        /**
         * Maps the pre-v3 flags. `gemini-*` models of a Gemini provider are always STT + text: STT
         * used to be Whisper multipart only, so `MULTIMODAL` was first migrated to text-only.
         */
        fun fromLegacy(
            kind: ModelKind?,
            isThinking: Boolean,
            streamingPartialsSupported: Boolean,
            providerType: ProviderType,
            modelId: String = "",
        ): ModelCapabilities {
            val geminiAudio = providerType.isGeminiAudioModel(modelId)
            val stt = kind == ModelKind.STT || geminiAudio
            return ModelCapabilities(
                stt = stt,
                text = kind != ModelKind.STT || geminiAudio,
                realtime = kind == ModelKind.STT && streamingPartialsSupported,
                thinking = isThinking,
                vocabulary = stt && providerType.acceptsTranscriptionPrompt,
            )
        }

        fun stt(providerType: ProviderType, realtime: Boolean = false): ModelCapabilities = ModelCapabilities(
            stt = true,
            realtime = realtime,
            vocabulary = providerType.acceptsTranscriptionPrompt,
        )
    }
}

/** OpenAI-compatible endpoints take a `prompt` field; Gemini gets it as preferred spellings. */
val ProviderType.acceptsTranscriptionPrompt: Boolean
    get() = this == ProviderType.OPENAI || this == ProviderType.CUSTOM || this == ProviderType.GEMINI

/** Gemini `generateContent` models (not embeddings) accept inline audio. */
fun ProviderType.isGeminiAudioModel(modelId: String): Boolean {
    val id = modelId.trim().removePrefix("models/").lowercase()
    return this == ProviderType.GEMINI && id.startsWith("gemini-") && !id.contains("embedding")
}

data class ModelConfig(
    val id: String,
    val name: String,
    val capabilities: ModelCapabilities = ModelCapabilities.TEXT,
) {
    // Legacy mirrors of [capabilities], persisted so older app versions can still read providers
    // and backups. Never read them in new code.
    @Deprecated("Use capabilities.thinking")
    val isThinking: Boolean = capabilities.thinking

    @Deprecated("Use capabilities")
    val kind: ModelKind = capabilities.legacyKind

    @Deprecated("Use capabilities.realtime")
    val streamingPartialsSupported: Boolean = capabilities.realtime
}

data class ServiceProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProviderType,
    val endpoint: String,
    val authMode: ProviderAuthMode = ProviderAuthMode.API_KEY,
    @Transient val apiKey: String = "",
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
    @SerializedName("smartFixProviderId") val enhancementProviderId: String,
    @SerializedName("smartFixModelId") val enhancementModelId: String,
)
