package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal const val OPENAI_TEST_COMMIT_TIMEOUT_MS = 2_000L

// 200 ms of 24 kHz PCM16 (the input rate is 24 kHz in these tests, so there is no resampling).
internal const val OPENAI_SPEECH_BYTES = 9_600

internal fun openAiSession(
    factory: FakeWsFactory,
    endpoint: String = "https://litellm.example.com/v1",
    type: ProviderType = ProviderType.CUSTOM,
    modelId: String = "gpt-4o-transcribe",
    language: String = "ar",
    prompt: String = "Vocab",
    inputSampleRate: Int = 24_000,
    vad: Vad = Vad.ServerVad(),
    schema: RealtimeSchema = RealtimeSchema.GA,
    betaFallback: Boolean = true,
) = OpenAiRealtimeSession(
    sockets = factory,
    provider = ServiceProvider(id = "p", name = "Gateway", type = type, endpoint = endpoint, models = emptyList()),
    model = ModelConfig(modelId, modelId, ModelCapabilities(stt = true, realtime = true)),
    credentials = Credentials("sk-test-key"),
    request = TranscriptionRequest(
        provider = ServiceProvider(name = "unused", type = type, endpoint = endpoint, models = emptyList()),
        modelId = modelId,
        languageCode = language,
        prompt = prompt,
    ),
    commitTimeoutMs = OPENAI_TEST_COMMIT_TIMEOUT_MS,
    inputSampleRate = inputSampleRate,
    vad = vad,
    initialSchema = schema,
    betaFallback = betaFallback,
)

internal fun FakeWsSocket.types(): List<String> = json.map { it.getString("type") }

internal fun gaSessionUpdated(vadOn: Boolean = true): String {
    val vad = if (vadOn) """{"type":"server_vad","silence_duration_ms":400}""" else "null"
    return """{"type":"session.updated","session":{"type":"transcription","audio":{"input":{"turn_detection":$vad}}}}"""
}

internal fun betaSessionUpdated(vadOn: Boolean = true): String {
    val vad = if (vadOn) """{"type":"server_vad"}""" else "null"
    return """{"type":"transcription_session.updated","session":{"turn_detection":$vad}}"""
}

internal fun completedEvent(itemId: String, text: String) =
    """{"type":"conversation.item.input_audio_transcription.completed","item_id":"$itemId","transcript":"$text"}"""

internal fun committedEvent(itemId: String) = """{"type":"input_audio_buffer.committed","item_id":"$itemId"}"""

internal fun deltaEvent(itemId: String, text: String) =
    """{"type":"conversation.item.input_audio_transcription.delta","item_id":"$itemId","delta":"$text"}"""

internal fun speechStartedEvent(itemId: String) = """{"type":"input_audio_buffer.speech_started","item_id":"$itemId"}"""

internal fun speechStoppedEvent(itemId: String) = """{"type":"input_audio_buffer.speech_stopped","item_id":"$itemId"}"""

internal const val CLEARED_EVENT = """{"type":"input_audio_buffer.cleared"}"""

internal const val EMPTY_COMMIT_ERROR =
    """{"type":"error","error":{"type":"invalid_request_error","code":"input_audio_buffer_commit_empty","message":"buffer too small"}}"""

internal const val UNKNOWN_PARAMETER_ERROR =
    """{"type":"error","error":{"type":"invalid_request_error","code":"unknown_parameter","message":"Unknown parameter: session.type"}}"""

/** One server-VAD utterance: speech, auto-commit, deltas and completion. */
internal fun FakeWsSocket.utterance(itemId: String, vararg deltas: String) {
    receive(speechStartedEvent(itemId))
    receive(speechStoppedEvent(itemId))
    receive(committedEvent(itemId))
    deltas.forEach { receive(deltaEvent(itemId, it)) }
    receive(completedEvent(itemId, deltas.joinToString("")))
}

/** Collects [flow] into [into] for the lifetime of the receiving (test background) scope. */
internal fun <T> CoroutineScope.collectInto(flow: Flow<T>, into: MutableList<T>) {
    launch { flow.collect { into += it } }
}
