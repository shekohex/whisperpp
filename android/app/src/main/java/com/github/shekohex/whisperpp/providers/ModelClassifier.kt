package com.github.shekohex.whisperpp.providers

import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.acceptsTranscriptionPrompt

/**
 * Maps listed model ids to [ModelCapabilities] (§4.5.2). `null` means "not useful for dictation"
 * (TTS, embeddings, images, moderation); an all-false result means "unknown family" and is left
 * for the user to classify.
 */
object ModelClassifier {
    val UNCLASSIFIED = ModelCapabilities()

    private val IGNORED_MARKERS = listOf(
        "tts", "embed", "rerank", "dall-e", "moderation", "image", "audio-preview", "search-preview",
    )
    private val STT_MARKERS = listOf("whisper", "transcribe", "stt", "asr", "voxtral")
    private val TEXT_PREFIXES = listOf(
        "gpt", "o1", "o3", "o4", "chatgpt", "llama", "meta-llama", "mixtral", "mistral", "ministral",
        "codestral", "qwen", "qwq", "deepseek", "claude", "gemma", "gemini", "phi", "grok", "kimi",
        "glm", "command", "nemotron", "granite",
    )
    private val REASONING_PREFIXES = listOf("o1", "o3", "o4")
    private val REASONING_MARKERS = listOf("reasoning", "thinking", "-r1", "qwq")
    private val GEMINI_IGNORED_PREFIXES = listOf("embedding", "text-embedding", "imagen", "veo", "aqa")
    private val GEMINI_IGNORED_MARKERS = listOf("embed", "image", "tts")

    /** OpenAI-style `/v1/models` ids; vendor prefixes (`openai/gpt-4o`) are ignored. */
    fun classifyOpenAiCompatible(id: String, providerType: ProviderType): ModelCapabilities? {
        val name = id.trim().substringAfterLast('/').lowercase()
        if (name.isEmpty()) return null
        if (IGNORED_MARKERS.any { name.contains(it) }) return null
        // `*-realtime-*` ids are voice-conversation models, not transcription models.
        if (name.contains("realtime")) return null
        if (STT_MARKERS.any { name.contains(it) }) {
            return ModelCapabilities.stt(providerType, realtime = isRealtimeTranscription(name, providerType))
        }
        if (TEXT_PREFIXES.any { name.startsWith(it) }) {
            return ModelCapabilities(text = true, thinking = isReasoning(name))
        }
        // Vision-only checkpoints (no text family) are not useful for dictation.
        if (name.contains("vision")) return null
        return UNCLASSIFIED
    }

    /**
     * Gemini `models.list` entries: `generateContent` → text, plus STT for `gemini-*` (inline audio).
     * There is no Live (`bidiGenerateContent`) implementation yet, so nothing is realtime and
     * bidi-only models are ignored.
     */
    fun classifyGemini(id: String, methods: Collection<String>, thinking: Boolean? = null): ModelCapabilities? {
        val name = id.trim().removePrefix("models/").lowercase()
        if (name.isEmpty()) return null
        if (GEMINI_IGNORED_PREFIXES.any { name.startsWith(it) }) return null
        if (GEMINI_IGNORED_MARKERS.any { name.contains(it) }) return null
        if ("generateContent" !in methods) return null
        val stt = name.startsWith("gemini")
        return ModelCapabilities(
            stt = stt,
            text = true,
            realtime = false,
            thinking = thinking ?: (name.contains("thinking") || isGeminiThinkingFamily(name)),
            vocabulary = stt && ProviderType.GEMINI.acceptsTranscriptionPrompt,
        )
    }

    /**
     * Models accepted by the Realtime API's `input_audio_transcription.model` (OpenAI only):
     * `whisper-1` and the `*-transcribe*` family (`gpt-4o-transcribe`, `gpt-4o-mini-transcribe`, …).
     */
    private fun isRealtimeTranscription(name: String, providerType: ProviderType): Boolean {
        if (providerType != ProviderType.OPENAI) return false
        return name == "whisper-1" || name.contains("-transcribe")
    }

    /** [isRealtimeTranscription] for a configured model id, so models added by hand stream without a flag. */
    fun supportsRealtimeTranscription(id: String, providerType: ProviderType): Boolean =
        isRealtimeTranscription(id.trim().substringAfterLast('/').lowercase(), providerType)

    private fun isReasoning(name: String): Boolean {
        if (REASONING_PREFIXES.any { name.startsWith(it) }) return true
        if (REASONING_MARKERS.any { name.contains(it) }) return true
        // gpt-5 family reasons by default; the `-chat` variants do not.
        return name.startsWith("gpt-5") && !name.contains("chat")
    }

    private fun isGeminiThinkingFamily(name: String): Boolean =
        name.startsWith("gemini-2.5") || name.startsWith("gemini-3")
}
