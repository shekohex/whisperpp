package com.github.shekohex.whisperpp.providers.realtime

import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import okhttp3.HttpUrl.Companion.toHttpUrl

internal const val TEST_COMMIT_TIMEOUT_MS = 2_000L

internal fun streamingConfig(
    base: String,
    modelId: String,
    language: String? = null,
    vocabulary: List<String> = emptyList(),
    apiKey: String = "test-key",
) = StreamingConfig(base.toHttpUrl(), apiKey, modelId, language, vocabulary)

internal fun testSession(
    factory: FakeWsFactory,
    type: ProviderType,
    dialect: () -> StreamingDialect,
    commitTimeoutMs: Long = TEST_COMMIT_TIMEOUT_MS,
): WebSocketStreamingSession {
    val provider = ServiceProvider(name = "p", type = type, endpoint = "https://example.com", models = emptyList())
    val request = TranscriptionRequest(provider = provider, modelId = "m")
    return WebSocketStreamingSession(factory, request, dialect, commitTimeoutMs = commitTimeoutMs)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.collectEvents(session: WebSocketStreamingSession): MutableList<StreamEvent> {
    val events = mutableListOf<StreamEvent>()
    backgroundScope.launch { session.events.collect { events += it } }
    runCurrent()
    return events
}

/** The text of every [StreamEvent.Partial], in order. */
internal fun List<StreamEvent>.partials(): List<String> = filterIsInstance<StreamEvent.Partial>().map { it.text }
