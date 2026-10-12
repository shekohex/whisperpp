package com.github.shekohex.whisperpp.data

import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class ProviderType {
    OPENAI, WHISPER_ASR, GEMINI, CUSTOM,
    DEEPGRAM, ELEVENLABS, MISTRAL, SONIOX, SPEECHMATICS, ASSEMBLYAI, XAI, CARTESIA;

    override fun toString(): String {
        return when(this) {
            OPENAI -> "OpenAI"
            WHISPER_ASR -> "Whisper ASR"
            GEMINI -> "Google Gemini"
            CUSTOM -> "Custom"
            DEEPGRAM -> "Deepgram"
            ELEVENLABS -> "ElevenLabs"
            MISTRAL -> "Mistral"
            SONIOX -> "Soniox"
            SPEECHMATICS -> "Speechmatics"
            ASSEMBLYAI -> "AssemblyAI"
            XAI -> "xAI"
            CARTESIA -> "Cartesia"
        }
    }
}

/** Cloud speech providers that dictate over their own WebSocket protocol (no on-device models). */
val ProviderType.isCloudRealtime: Boolean
    get() = when (this) {
        ProviderType.DEEPGRAM, ProviderType.ELEVENLABS, ProviderType.MISTRAL, ProviderType.SONIOX,
        ProviderType.SPEECHMATICS, ProviderType.ASSEMBLYAI, ProviderType.XAI, ProviderType.CARTESIA -> true
        ProviderType.OPENAI, ProviderType.WHISPER_ASR, ProviderType.GEMINI, ProviderType.CUSTOM -> false
    }

/**
 * Cloud providers without a batch endpoint here: dictation always streams, whatever the "Use
 * realtime transcription" setting says. Deepgram, ElevenLabs and Mistral also batch (fallback).
 */
val ProviderType.isStreamingOnly: Boolean
    get() = when (this) {
        ProviderType.SONIOX, ProviderType.SPEECHMATICS, ProviderType.ASSEMBLYAI, ProviderType.XAI,
        ProviderType.CARTESIA -> true
        else -> false
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
         * Maps the pre-v3 flags; the one mapping shared by the v3 migration, provider sanitizing and
         * backup import. `MULTIMODAL` is STT + text (realtime from `streamingPartialsSupported`).
         * `gemini-*` models of a Gemini provider are always STT + text; other Gemini models keep text
         * only, since every Gemini model used to be inferred as `MULTIMODAL`. Gemini has no realtime path.
         */
        fun fromLegacy(
            kind: ModelKind?,
            isThinking: Boolean,
            streamingPartialsSupported: Boolean,
            providerType: ProviderType,
            modelId: String = "",
        ): ModelCapabilities {
            val gemini = providerType == ProviderType.GEMINI
            val geminiAudio = providerType.isGeminiAudioModel(modelId)
            val stt = kind == ModelKind.STT || (kind == ModelKind.MULTIMODAL && !gemini) || geminiAudio
            return ModelCapabilities(
                stt = stt,
                text = kind != ModelKind.STT || geminiAudio,
                realtime = stt && !gemini && (streamingPartialsSupported || providerType.isCloudRealtime),
                thinking = isThinking,
                vocabulary = stt && providerType.acceptsTranscriptionPrompt,
            )
        }

        fun stt(providerType: ProviderType, realtime: Boolean = false): ModelCapabilities = ModelCapabilities(
            stt = true,
            // Cloud realtime providers stream every STT model.
            realtime = realtime || providerType.isCloudRealtime,
            vocabulary = providerType.acceptsTranscriptionPrompt,
        )
    }
}

/**
 * OpenAI-compatible endpoints take a `prompt` field; Gemini gets it as preferred spellings; the
 * cloud realtime providers take the vocabulary as key terms (Mistral and Cartesia have none).
 */
val ProviderType.acceptsTranscriptionPrompt: Boolean
    get() = when (this) {
        ProviderType.OPENAI, ProviderType.CUSTOM, ProviderType.GEMINI,
        ProviderType.DEEPGRAM, ProviderType.ELEVENLABS, ProviderType.SONIOX, ProviderType.SPEECHMATICS,
        ProviderType.ASSEMBLYAI, ProviderType.XAI -> true
        ProviderType.WHISPER_ASR, ProviderType.MISTRAL, ProviderType.CARTESIA -> false
    }

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
