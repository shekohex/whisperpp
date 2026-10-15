package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.modes.OutputMode
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.Transcript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Realtime (streaming) side of a run: PCM pump, partials, commit and the fallback to batch (§4.5.1). */
internal class RealtimeStreaming(
    private val deps: DictationSession.Deps,
    private val state: MutableStateFlow<SessionState>,
    private val liveTranscript: MutableStateFlow<String?>,
    private val currentFocus: () -> FocusKey?,
    private val isCurrent: (DictationRun) -> Boolean,
    private val windowVisible: () -> Boolean,
) {
    fun start(r: DictationRun, streaming: StreamingTranscriptionSession): (ByteArray) -> Unit {
        r.streaming = streaming
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        r.pcm = channel
        r.auxJobs += deps.scope.launch {
            streaming.events.collect { onStreamEvent(r, it) }
        }
        val sender = deps.scope.launch(deps.streamingDispatcher) {
            try {
                streaming.start()
                for (chunk in channel) streaming.sendAudio(chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The WAV is complete regardless; the stop path falls back to batch.
                r.streamBroken = true
                if (isCurrent(r)) liveTranscript.value = null
                channel.cancel()
            }
        }
        r.pcmSender = sender
        r.auxJobs += sender
        return { chunk -> channel.trySend(chunk) }
    }

    /**
     * F-05: dropped PCM means the stream diverged from the file; ignore every realtime result and
     * return null so the caller falls back to batch. Otherwise returns the session to commit.
     */
    fun healthyStreamOrDiscard(r: DictationRun, droppedChunks: Int): StreamingTranscriptionSession? {
        val streaming = r.streaming?.takeIf { droppedChunks == 0 }
        if (streaming == null) {
            r.streamBroken = true
            r.lastPartial = null
            liveTranscript.value = null
            r.pcmSender?.cancel()
            r.streaming?.let(::cancel)
        }
        return streaming
    }

    /** The stream's committed text, or null when it failed or came back blank (batch takes over). */
    suspend fun committedText(r: DictationRun, streaming: StreamingTranscriptionSession): String? {
        val text = commit(r, streaming).getOrNull()?.text?.takeIf { it.isNotBlank() }
        if (text == null && isCurrent(r)) {
            r.streamBroken = true
            liveTranscript.value = null
        }
        return text
    }

    fun cancel(streaming: StreamingTranscriptionSession) {
        deps.scope.launch {
            try {
                streaming.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Closing is best effort.
            }
        }
    }

    /**
     * Waits until every captured chunk was handed to the stream, then commits it. The stream's own
     * final is the only accepted result; partials never stand in for it.
     */
    private suspend fun commit(r: DictationRun, streaming: StreamingTranscriptionSession): Result<Transcript> {
        r.pcmSender?.join()
        if (r.streamBroken) return Result.failure(IllegalStateException("stream broken"))
        return try {
            streaming.commit()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun onStreamEvent(r: DictationRun, event: StreamEvent) {
        if (!isCurrent(r) || r.streamBroken) return
        when (event) {
            is StreamEvent.Partial -> applyPartial(r, event.text, composeInEditor = true)
            is StreamEvent.Final -> applyPartial(r, event.text, composeInEditor = false)
            is StreamEvent.Error -> {
                r.streamBroken = true
                liveTranscript.value = null
            }
            else -> Unit
        }
    }

    private fun applyPartial(r: DictationRun, text: String, composeInEditor: Boolean) {
        if (text.isBlank()) {
            liveTranscript.value = null
            return
        }
        r.lastPartial = text
        val s = state.value
        if (s !is SessionState.Recording && s !is SessionState.Transcribing) return
        if (currentFocus() != r.focus || !deps.sendAllowed()) {
            liveTranscript.value = null
            return
        }
        liveTranscript.value = text.takeIf { windowVisible() }
        // Partials would overwrite the selection to replace, or touch a field that gets nothing.
        if (!composeInEditor || r.options.outputMode != OutputMode.INSERT || r.suppressPartials) return
        val now = deps.clock()
        val last = r.lastPartialAt
        if (last != null && now - last < deps.partialThrottleMs) return
        r.lastPartialAt = now
        if (deps.committer.setComposing(r.focus, text)) r.composing = true
        if (s is SessionState.Transcribing) state.value = s.copy(partial = text)
    }
}
