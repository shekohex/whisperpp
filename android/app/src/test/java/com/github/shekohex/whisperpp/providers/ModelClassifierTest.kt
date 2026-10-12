package com.github.shekohex.whisperpp.providers

import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.acceptsTranscriptionPrompt
import com.github.shekohex.whisperpp.data.isCloudRealtime
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelClassifierTest {
    private val stt = ModelCapabilities.stt(ProviderType.OPENAI)
    private val realtime = ModelCapabilities.stt(ProviderType.OPENAI, realtime = true)
    private val text = ModelCapabilities(text = true)
    private val thinking = ModelCapabilities(text = true, thinking = true)
    private val unclassified = ModelClassifier.UNCLASSIFIED

    @Test
    fun openAiCompatibleIds() {
        val table: List<Pair<String, ModelCapabilities?>> = listOf(
            "whisper-1" to realtime,
            "whisper-large-v3-turbo" to stt,
            "distil-whisper-large-v3-en" to stt,
            "gpt-4o-transcribe" to realtime,
            "gpt-4o-mini-transcribe" to realtime,
            "gpt-4o-transcribe-diarize" to realtime,
            "nvidia/parakeet-asr" to stt,
            "voxtral-mini-latest" to stt,
            // Voice-conversation models; transcription sessions do not accept them.
            "gpt-4o-realtime-preview" to null,
            "gpt-4o-mini-realtime-preview-2024-12-17" to null,
            "gpt-realtime" to null,
            "gpt-4o" to text,
            "gpt-4.1-mini" to text,
            "chatgpt-4o-latest" to text,
            "llama-3.3-70b-versatile" to text,
            "meta-llama/llama-3.1-8b-instruct" to text,
            "mixtral-8x7b-32768" to text,
            "mistral-large-latest" to text,
            "qwen2.5-72b-instruct" to text,
            "anthropic/claude-3.5-sonnet" to text,
            "gemma2-9b-it" to text,
            "deepseek-chat" to text,
            "o1" to thinking,
            "o3-mini" to thinking,
            "o4-mini" to thinking,
            "deepseek-r1-distill-llama-70b" to thinking,
            "qwen3-thinking" to thinking,
            "gpt-5" to thinking,
            "gpt-5-chat-latest" to text,
            "tts-1" to null,
            "gpt-4o-mini-tts" to null,
            "playai-tts" to null,
            "text-embedding-3-small" to null,
            "mistral-embed" to null,
            "dall-e-3" to null,
            "gpt-image-1" to null,
            "omni-moderation-latest" to null,
            "gpt-4o-audio-preview" to null,
            "some-vision-model" to null,
            "babbage-002" to unclassified,
            "" to null,
        )

        table.forEach { (id, expected) ->
            assertEquals("id=$id", expected, ModelClassifier.classifyOpenAiCompatible(id, ProviderType.OPENAI))
        }
    }

    @Test
    fun realtimeTranscription_isOpenAiOnly() {
        val groq = ModelClassifier.classifyOpenAiCompatible("whisper-1", ProviderType.CUSTOM)
        val asr = ModelClassifier.classifyOpenAiCompatible("gpt-4o-transcribe", ProviderType.WHISPER_ASR)

        assertEquals(false, groq?.realtime)
        assertEquals(false, asr?.realtime)
    }

    @Test
    fun vocabularyFollowsProviderType() {
        val custom = ModelClassifier.classifyOpenAiCompatible("whisper-1", ProviderType.CUSTOM)
        val asr = ModelClassifier.classifyOpenAiCompatible("whisper-1", ProviderType.WHISPER_ASR)

        assertEquals(true, custom?.vocabulary)
        assertEquals(false, asr?.vocabulary)
    }

    @Test
    fun geminiModels() {
        val generate = listOf("generateContent", "countTokens")
        val vocabulary = ProviderType.GEMINI.acceptsTranscriptionPrompt
        val geminiStt = ModelCapabilities(stt = true, text = true, vocabulary = vocabulary)
        val table: List<Triple<String, List<String>, ModelCapabilities?>> = listOf(
            Triple("models/gemini-2.0-flash", generate, geminiStt),
            Triple("models/gemini-2.5-flash", generate, geminiStt.copy(thinking = true)),
            Triple("models/gemini-2.5-pro", generate, geminiStt.copy(thinking = true)),
            Triple("models/gemini-2.0-flash-thinking-exp", generate, geminiStt.copy(thinking = true)),
            Triple("models/gemma-3-27b-it", generate, ModelCapabilities(text = true)),
            // No Live implementation: bidi-only models have no execution path, and nothing is realtime.
            Triple("models/gemini-2.0-flash-live-001", listOf("bidiGenerateContent"), null),
            Triple("models/gemini-2.5-flash-live", listOf("generateContent", "bidiGenerateContent"), geminiStt.copy(thinking = true)),
            Triple("models/embedding-001", listOf("embedContent"), null),
            Triple("models/text-embedding-004", listOf("embedContent"), null),
            Triple("models/gemini-embedding-001", listOf("embedContent"), null),
            Triple("models/imagen-3.0-generate-002", listOf("predict"), null),
            Triple("models/veo-2.0-generate-001", listOf("predictLongRunning"), null),
            Triple("models/aqa", listOf("generateAnswer"), null),
            Triple("models/gemini-2.5-flash-preview-tts", generate, null),
            Triple("models/gemini-2.5-flash-image", generate, null),
            Triple("models/learnlm-2.0-flash", listOf("countTokens"), null),
        )

        table.forEach { (id, methods, expected) ->
            assertEquals("id=$id", expected, ModelClassifier.classifyGemini(id, methods))
        }
    }

    @Test
    fun geminiExplicitThinkingFlagWins() {
        val caps = ModelClassifier.classifyGemini("gemini-2.5-flash-lite", listOf("generateContent"), thinking = false)

        assertEquals(false, caps?.thinking)
    }

    @Test
    fun cloudRealtimeProviders_streamEveryModelEnteredByHand() {
        for (type in ProviderType.entries.filter { it.isCloudRealtime }) {
            assertEquals(type.name, true, ModelClassifier.supportsRealtimeTranscription("any-model", type))
        }
        assertEquals(false, ModelClassifier.supportsRealtimeTranscription("whisper-large-v3", ProviderType.CUSTOM))
    }
}
