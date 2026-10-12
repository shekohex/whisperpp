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
        if (name.contains("realtime")) return ModelCapabilities.stt(providerType, realtime = true)
        if (STT_MARKERS.any { name.contains(it) }) return ModelCapabilities.stt(providerType)
        if (TEXT_PREFIXES.any { name.startsWith(it) }) {
            return ModelCapabilities(text = true, thinking = isReasoning(name))
        }
        // Vision-only checkpoints (no text family) are not useful for dictation.
        if (name.contains("vision")) return null
        return UNCLASSIFIED
    }

    /**
     * Gemini `models.list` entries: `generateContent` → text, plus STT for `gemini-*` (inline audio);
     * `bidiGenerateContent` → realtime STT.
     */
    fun classifyGemini(id: String, methods: Collection<String>, thinking: Boolean? = null): ModelCapabilities? {
        val name = id.trim().removePrefix("models/").lowercase()
        if (name.isEmpty()) return null
        if (GEMINI_IGNORED_PREFIXES.any { name.startsWith(it) }) return null
        if (GEMINI_IGNORED_MARKERS.any { name.contains(it) }) return null
        val generate = "generateContent" in methods
        val bidi = "bidiGenerateContent" in methods
        if (!generate && !bidi) return null
        val isGemini = name.startsWith("gemini")
        val stt = bidi || (generate && isGemini)
        return ModelCapabilities(
            stt = stt,
            text = generate,
            realtime = bidi,
            thinking = thinking ?: (name.contains("thinking") || isGeminiThinkingFamily(name)),
            vocabulary = stt && ProviderType.GEMINI.acceptsTranscriptionPrompt,
        )
    }

    private fun isReasoning(name: String): Boolean {
        if (REASONING_PREFIXES.any { name.startsWith(it) }) return true
        if (REASONING_MARKERS.any { name.contains(it) }) return true
        // gpt-5 family reasons by default; the `-chat` variants do not.
        return name.startsWith("gpt-5") && !name.contains("chat")
    }

    private fun isGeminiThinkingFamily(name: String): Boolean =
        name.startsWith("gemini-2.5") || name.startsWith("gemini-3")
}
