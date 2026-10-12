package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeStreaming
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.sessionTest
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionStreamingTest {

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
