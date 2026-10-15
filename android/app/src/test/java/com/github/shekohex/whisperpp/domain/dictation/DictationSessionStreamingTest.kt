package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeStreaming
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.sessionTest
import com.github.shekohex.whisperpp.domain.modes.OutputMode
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionStreamingTest {

    @Test
    fun liveTranscript_replacesOlderWordsWithoutWaitingForEditorThrottle() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "we met on Tuesday")
        h.streaming = streaming
        h.recordFor(1_000, locked = true)
        streaming.events.emit(StreamEvent.Partial("we met on Monday"))
        runCurrent()
        assertEquals("we met on Monday", h.session.liveTranscript.value)

        streaming.events.emit(StreamEvent.Partial("we met on Tuesday"))
        runCurrent()

        assertEquals("we met on Tuesday", h.session.liveTranscript.value)
        assertEquals("prefix we met on Monday", h.ic.getText())
    }

    @Test
    fun liveTranscript_worksWhileEnhancementSuppressesEditorPartials() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "hello")
        h.streaming = streaming
        h.enhancer = object : DictationEnhancer {
            override suspend fun isEnabled(request: DictationEnhancer.Request) = true
            override suspend fun enhance(request: DictationEnhancer.Request) = DictationEnhancer.Result.Kept()
        }
        h.recordFor(1_000, locked = true)

        streaming.events.emit(StreamEvent.Partial("hello"))
        runCurrent()

        assertEquals("hello", h.session.liveTranscript.value)
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun liveTranscript_worksInClipboardModeWithoutTouchingField() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "hello")
        h.streaming = streaming
        h.options = DictationOptions(outputMode = OutputMode.CLIPBOARD)
        h.recordFor(1_000, locked = true)

        streaming.events.emit(StreamEvent.Partial("hello"))
        runCurrent()

        assertEquals("hello", h.session.liveTranscript.value)
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun liveTranscript_finalCorrectionUpdatesPreview_thenCompletionClearsIt() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "turn right")
        h.streaming = streaming
        h.recordFor(1_000, locked = true)
        streaming.events.emit(StreamEvent.Partial("turn left"))
        runCurrent()

        streaming.events.emit(StreamEvent.Final("turn right"))
        runCurrent()
        assertEquals("turn right", h.session.liveTranscript.value)

        h.session.onStop()
        advanceUntilIdle()

        assertNull(h.session.liveTranscript.value)
        assertEquals("prefix turn right", h.ic.getText())
    }

    @Test
    fun liveTranscript_cancelClearsIt_andNextTakeStartsEmpty() = sessionTest { h ->
        val streaming = FakeStreaming(committed = null)
        h.streaming = streaming
        h.recordFor(1_000, locked = true)
        streaming.events.emit(StreamEvent.Partial("discard me"))
        runCurrent()

        h.session.onCancel()
        runCurrent()
        assertNull(h.session.liveTranscript.value)
        h.recordFor(1_000, locked = true)

        assertNull(h.session.liveTranscript.value)
    }

    @Test
    fun liveTranscript_focusChangeAndHiddenKeyboardPreventLateCaptions() = sessionTest { h ->
        val streaming = FakeStreaming(committed = null)
        h.streaming = streaming
        h.recordFor(1_000, locked = true)
        streaming.events.emit(StreamEvent.Partial("private caption"))
        runCurrent()

        h.session.onFocusChanged(h.otherFocus)
        assertNull(h.session.liveTranscript.value)
        streaming.events.emit(StreamEvent.Partial("late caption"))
        runCurrent()
        assertNull(h.session.liveTranscript.value)

        h.session.onFocusChanged(h.focus)
        h.session.onWindowHidden()
        streaming.events.emit(StreamEvent.Partial("hidden caption"))
        runCurrent()
        assertNull(h.session.liveTranscript.value)
    }

    @Test
    fun liveTranscript_newPrivacyBlockClearsIt_andRejectsLateCaptions() = sessionTest { h ->
        val streaming = FakeStreaming(committed = null)
        h.streaming = streaming
        h.recordFor(1_000, locked = true)
        streaming.events.emit(StreamEvent.Partial("private caption"))
        runCurrent()

        h.sendAllowed = false
        h.session.onExternalSendBlocked()
        streaming.events.emit(StreamEvent.Partial("late caption"))
        runCurrent()

        assertNull(h.session.liveTranscript.value)
    }

    @Test
    fun liveTranscript_batchFallbackClearsIt_andRejectsLateStreamingResults() = sessionTest { h ->
        val streaming = FakeStreaming(committed = null)
        val batch = CompletableDeferred<String>()
        h.streaming = streaming
        h.transcript = { Result.success(batch.await()) }
        h.recordFor(1_000, locked = true)
        streaming.events.emit(StreamEvent.Partial("unreliable"))
        runCurrent()

        h.session.onStop()
        runCurrent()
        assertNull(h.session.liveTranscript.value)
        streaming.events.emit(StreamEvent.Partial("stale"))
        runCurrent()
        assertNull(h.session.liveTranscript.value)
        batch.complete("batch result")
        advanceUntilIdle()

        assertEquals("prefix batch result", h.ic.getText())
    }

    @Test
    fun droppedPcm_ignoresRealtimeResult() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "streamed")
        h.streaming = streaming
        h.recorder.droppedChunks = 3
        h.transcript = { Result.failure(java.io.IOException("offline")) }
        h.recordFor(1_000)
        streaming.events.emit(StreamEvent.Partial("stream"))
        runCurrent()
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(0, streaming.commits)
        assertTrue(streaming.cancels >= 1)
        assertTrue(h.events.contains(SessionEvent.TranscriptionFailed("offline")))
        assertFalse(h.events.any { it is SessionEvent.Committed })
    }

    @Test
    fun healthyStream_isPrimary_andBatchIsNotSent() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "streamed")
        h.streaming = streaming
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(1, streaming.commits)
        assertTrue(h.requests.isEmpty())
        assertTrue(h.events.contains(SessionEvent.Committed("streamed")))
        assertEquals("prefix streamed", h.ic.getText())
        assertFalse(h.audioFile.exists())
    }

    @Test
    fun streamCommitFailure_fallsBackToBatchWithTheSameFile() = sessionTest { h ->
        val streaming = FakeStreaming(committed = null)
        h.streaming = streaming
        h.recordFor(1_000)
        streaming.events.emit(StreamEvent.Partial("hel"))
        runCurrent()
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(1, streaming.commits)
        assertEquals(1, h.requests.size)
        // The partial is replaced, never kept next to the batch text.
        assertEquals("prefix hello", h.ic.getText())
        assertTrue(h.events.contains(SessionEvent.Committed("hello")))
    }

    @Test
    fun streamAndBatchFailure_reportsBatchError_withoutUsingPartials() = sessionTest { h ->
        val streaming = FakeStreaming(committed = null)
        h.streaming = streaming
        h.transcript = { Result.failure(java.io.IOException("offline")) }
        h.recordFor(1_000)
        streaming.events.emit(StreamEvent.Partial("stale partial"))
        runCurrent()
        h.session.onPressRelease()
        advanceUntilIdle()

        assertTrue(h.events.contains(SessionEvent.TranscriptionFailed("offline")))
        assertFalse(h.events.any { it is SessionEvent.Committed })
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun brokenStreamStart_skipsCommit_andUsesBatch() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "streamed").apply { startFailure = java.io.IOException("refused") }
        h.streaming = streaming
        h.recordFor(1_000)
        h.recorder.onPcm!!(byteArrayOf(1, 2))
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(0, streaming.commits)
        assertTrue(h.events.contains(SessionEvent.Committed("hello")))
    }

    @Test
    fun queuedPcm_isDrainedIntoTheStream_beforeCommit() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "streamed")
        h.streaming = streaming
        h.recordFor(1_000)
        val onPcm = h.recorder.onPcm!!
        // Captured right before the release; the sender has not run yet.
        repeat(5) { onPcm(byteArrayOf(it.toByte(), 0)) }
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(5, streaming.sent.size)
        assertEquals(5, streaming.sentAtCommit)
        assertEquals(listOf(0, 1, 2, 3, 4), streaming.sent.map { it[0].toInt() })
    }

    @Test
    fun droppedPcm_usesBatchOnly() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "streamed")
        h.streaming = streaming
        h.recorder.droppedChunks = 1
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(0, streaming.commits)
        assertEquals(1, h.requests.size)
        assertTrue(h.events.contains(SessionEvent.Committed("hello")))
    }

    @Test
    fun focusChangeDuringStreaming_cancelLeavesOtherFieldUntouched() = sessionTest { h ->
        h.streaming = FakeStreaming(committed = null)
        h.recordFor(1_000, locked = true)
        (h.streaming as FakeStreaming).events.emit(StreamEvent.Partial("hel"))
        runCurrent()
        assertEquals("prefix hel", h.ic.getText())

        // Field B takes over the connection with its own composing text.
        h.session.onFocusChanged(h.otherFocus)
        h.ic.setText("B")
        h.ic.setComposingText("x", 1)
        h.session.onCancel()
        advanceUntilIdle()

        assertEquals("Bx", h.ic.getText())
        assertEquals(1 to 2, h.ic.composingRegion())
    }

    @Test
    fun emptyTranscript_clearsStreamedPartial() = sessionTest { h ->
        h.streaming = FakeStreaming(committed = null)
        // Dropped PCM disables the stream fallback, so the take really ends empty.
        h.recorder.droppedChunks = 1
        h.transcript = { Result.success("  ") }
        h.recordFor(1_000, locked = true)
        (h.streaming as FakeStreaming).events.emit(StreamEvent.Partial("hel"))
        runCurrent()
        assertEquals("prefix hel", h.ic.getText())

        h.session.onStop()
        advanceUntilIdle()

        assertTrue(h.events.contains(SessionEvent.TranscriptionEmpty))
        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun partialsWithoutSelectionSnapshot_replaceInsteadOfAppending() = sessionTest { h ->
        h.ic.extractedTextAvailable = false
        h.streaming = FakeStreaming(committed = null)
        h.recordFor(1_000, locked = true)
        val streaming = h.streaming as FakeStreaming
        streaming.events.emit(StreamEvent.Partial("he"))
        runCurrent()
        advanceTimeBy(DictationSession.PARTIAL_THROTTLE_MS + 1)
        streaming.events.emit(StreamEvent.Partial("hell"))
        runCurrent()
        assertEquals("prefix hell", h.ic.getText())

        h.session.onStop()
        advanceUntilIdle()

        assertEquals("prefix hello", h.ic.getText())
    }
}
