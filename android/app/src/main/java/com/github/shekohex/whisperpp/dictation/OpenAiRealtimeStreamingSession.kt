package com.github.shekohex.whisperpp.dictation

import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.Transcript
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Adapts the legacy [OpenAiRealtimeSttClient] to [StreamingTranscriptionSession] until WP-2.5
 * replaces it. [commit] returns the latest final transcript, or the best partial, after
 * waiting briefly for the server to finish the committed buffer.
 */
class OpenAiRealtimeStreamingSession(
    okHttpClient: OkHttpClient,
    request: Request,
    private val commitTimeoutMs: Long = COMMIT_TIMEOUT_MS,
) : StreamingTranscriptionSession {
    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<StreamEvent> = _events.asSharedFlow()

    @Volatile private var finalText: String? = null
    @Volatile private var bestText: String? = null
    private val finalVersion = MutableStateFlow(0)
    private val closed = MutableStateFlow(false)

    private val client = OpenAiRealtimeSttClient(
        okHttpClient = okHttpClient,
        request = request,
        listener = object : OpenAiRealtimeSttClient.Listener {
            override fun onConnected() {
                _events.tryEmit(StreamEvent.Connected)
            }

            override fun onPartialTranscript(text: String) {
                bestText = text
                _events.tryEmit(StreamEvent.Partial(text))
            }

            override fun onFinalTranscript(text: String) {
                bestText = text
                finalText = text
                finalVersion.value += 1
                _events.tryEmit(StreamEvent.Final(text))
            }

            override fun onError(message: String) {
                _events.tryEmit(StreamEvent.Error(message))
            }

            override fun onClosed() {
                closed.value = true
                _events.tryEmit(StreamEvent.Closed)
            }
        },
    )

    override suspend fun start() {
        client.connect()
    }

    override suspend fun sendAudio(pcm16: ByteArray) {
        client.sendPcm16le(pcm16)
    }

    override suspend fun commit(): Result<Transcript> {
        val before = finalVersion.value
        client.commitAudio()
        withTimeoutOrNull(commitTimeoutMs) {
            combine(finalVersion, closed) { version, isClosed -> version > before || isClosed }.first { it }
        }
        client.close()
        val text = (finalText ?: bestText)?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No realtime transcript"))
        return Result.success(Transcript(text))
    }

    override suspend fun cancel() {
        client.close()
    }

    companion object {
        const val COMMIT_TIMEOUT_MS = 1_500L
    }
}
