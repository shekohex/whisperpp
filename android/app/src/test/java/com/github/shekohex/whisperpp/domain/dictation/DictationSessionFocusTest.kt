package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeHistory
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeStreaming
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.sessionTest
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionFocusTest {

    @Test
    fun commitBlockedByFocus_discardsKeptAudio_withoutHistoryEntry() = sessionTest { h ->
        val history = FakeHistory(keepAudio = true)
        h.history = history
        h.recordFor(1_000)
        h.session.onStop()
        h.session.onFocusChanged(h.otherFocus)
        advanceUntilIdle()

        assertTrue(h.events.any { it is SessionEvent.CommitBlockedByFocus })
        assertTrue(history.entries.isEmpty())
        assertEquals(history.retained, history.discarded)
        assertEquals(1, history.discarded.size)
    }

    @Test
    fun windowHiddenWhileRecording_pausesAndReleasesFocus() = sessionTest { h ->
        h.recordFor(1_000, locked = true)
        h.session.onWindowHidden()
        runCurrent()

        val state = h.session.state.value as SessionState.Recording
        assertTrue(state.paused)
        assertEquals(1_000L, state.accumulatedMs)
        assertEquals(1, h.recorder.pauses)
        assertEquals(1, h.gateway.abandons)
        assertEquals(0, h.recorder.cancels)
    }

    @Test
    fun windowHiddenWhileTranscribing_commitsWhenShownAgain() = sessionTest { h ->
        val gate = CompletableDeferred<Result<String>>()
        h.transcript = { gate.await() }
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()

        h.session.onWindowHidden()
        gate.complete(Result.success("hello"))
        advanceUntilIdle()
        assertTrue(h.session.state.value is SessionState.Committing)
        assertEquals("prefix ", h.ic.getText())

        h.session.onWindowShown()
        advanceUntilIdle()
        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix hello", h.ic.getText())
    }

    @Test
    fun focusChangeWhilePaused_thenStop_holdsTranscriptForOriginalField() = sessionTest { h ->
        h.recordFor(1_000, locked = true)
        h.session.onFocusChanged(h.otherFocus)
        runCurrent()
        assertTrue((h.session.state.value as SessionState.Recording).paused)

        h.session.onStop()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(h.events.contains(SessionEvent.CommitBlockedByFocus("hello")))
        assertEquals("hello", h.session.pendingCommit.value)
        assertEquals("prefix ", h.ic.getText())

        h.session.onPendingCommitInsert()
        assertEquals("prefix hello", h.ic.getText())
        assertNull(h.session.pendingCommit.value)
    }

    @Test
    fun commitBlockedByFocus_resetsComposingOwnership_beforeInsertingElsewhere() = sessionTest { h ->
        h.streaming = FakeStreaming(committed = null)
        h.recordFor(1_000, locked = true)
        (h.streaming as FakeStreaming).events.emit(StreamEvent.Partial("hel"))
        runCurrent()
        h.session.onFocusChanged(h.otherFocus)
        h.ic.setText("B")
        h.session.onStop()
        advanceUntilIdle()
        assertTrue(h.events.contains(SessionEvent.CommitBlockedByFocus("hello")))
        assertEquals("B", h.ic.getText())

        h.session.onPendingCommitInsert()

        assertEquals("Bhello", h.ic.getText())
        // Undo uses B's real insertion offset, not the stale offset of A's partial.
        assertTrue(h.committer.undoLastInsertion(h.otherFocus))
        assertEquals("B", h.ic.getText())
    }

    @Test
    fun pressStartWithPendingCommit_isBlocked_andKeepsTranscript() = sessionTest { h ->
        h.recordFor(1_000, locked = true)
        h.session.onFocusChanged(h.otherFocus)
        h.session.onStop()
        advanceUntilIdle()
        assertEquals("hello", h.session.pendingCommit.value)
        val plannerCalls = h.plannerCalls

        h.session.onPressStart()
        advanceUntilIdle()

        assertEquals("hello", h.session.pendingCommit.value)
        assertEquals(plannerCalls, h.plannerCalls)
        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(h.events.contains(SessionEvent.Notice(com.github.shekohex.whisperpp.R.string.dictation_pending_commit_blocks_start)))
    }
}
