package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeHistory
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeStreaming
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.STATS
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.sessionTest
import com.github.shekohex.whisperpp.domain.providers.StreamEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionCommitTest {

    @Test
    fun completion_recordsHistoryEntry_withStatsAndKeptAudio() = sessionTest { h ->
        val history = FakeHistory(keepAudio = true)
        h.history = history
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val entry = history.entries.single()
        assertEquals("hello", entry.rawText)
        assertNull(entry.enhancedText)
        assertEquals(STATS, entry.stats)
        assertEquals("com.example", entry.appPackage)
        assertEquals(1_000L, entry.durationMs)
        assertEquals(history.retained.single(), entry.audioPath)
        assertTrue(history.discarded.isEmpty())
    }

    @Test
    fun enhancedCompletion_recordsRawAndEnhancedText_andEnhancementModel() = sessionTest { h ->
        val history = FakeHistory(keepAudio = false)
        h.history = history
        h.enhancer = object : DictationEnhancer {
            override suspend fun isEnabled(request: DictationEnhancer.Request) = true
            override suspend fun enhance(request: DictationEnhancer.Request): DictationEnhancer.Result {
                delay(250)
                return DictationEnhancer.Result.Enhanced("Hello.", "P (gpt)", providerId = "p2", modelId = "gpt")
            }
        }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val entry = history.entries.single()
        assertEquals("hello", entry.rawText)
        assertEquals("Hello.", entry.enhancedText)
        assertNull(entry.audioPath)
        assertEquals("p2", entry.stats.enhancementProviderId)
        assertEquals("gpt", entry.stats.enhancementModelId)
        assertEquals(250L, entry.stats.enhancementLatencyMs)
        val completed = h.events.filterIsInstance<SessionEvent.Completed>().single()
        assertEquals(entry.stats, completed.stats)
    }

    @Test
    fun failedTranscription_recordsNoHistory_andKeepsNoAudio() = sessionTest { h ->
        val history = FakeHistory(keepAudio = true)
        h.history = history
        h.transcript = { Result.failure(IllegalStateException("boom")) }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertTrue(history.entries.isEmpty())
        assertTrue(history.retained.isEmpty())
    }

    @Test
    fun blankAfterPostProcess_isEmptyTranscription_notTheRawText() = sessionTest { h ->
        val streaming = FakeStreaming(committed = "um")
        h.streaming = streaming
        h.postProcess = { "" }
        h.recordFor(1_000)
        streaming.events.emit(StreamEvent.Partial("um"))
        runCurrent()
        assertEquals("prefix um", h.ic.getText())

        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix ", h.ic.getText())
        assertTrue(h.events.contains(SessionEvent.TranscriptionEmpty))
        assertFalse(h.events.any { it is SessionEvent.Committed || it is SessionEvent.Completed })
    }

    @Test
    fun trailingSpace_isAddedOnce_forStreamedAndBatchTranscripts() = sessionTest { h ->
        h.addTrailingSpace = true
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        assertEquals("prefix hello ", h.ic.getText())

        h.ic.setText("prefix ")
        h.streaming = FakeStreaming(committed = "streamed ")
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        assertEquals("prefix streamed ", h.ic.getText())
    }

    @Test
    fun completed_carriesTheRunsFocusAndId() = sessionTest { h ->
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val completed = h.events.filterIsInstance<SessionEvent.Completed>()
        assertEquals(listOf(1L, 2L), completed.map { it.runId })
        assertTrue(completed.all { it.focus == h.focus })
        assertEquals(2L, h.session.latestRunId)
    }

    @Test
    fun transcriberFailure_isDistinctFromEmptyText() = sessionTest { h ->
        h.transcript = { Result.failure(java.io.IOException("HTTP 500")) }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        assertTrue(h.events.contains(SessionEvent.TranscriptionFailed("HTTP 500")))
        assertFalse(h.events.contains(SessionEvent.TranscriptionEmpty))
        assertEquals(
            SessionState.Error(SessionState.ErrorKind.TRANSCRIPTION_FAILED, recoverable = true),
            h.session.state.value,
        )

        h.events.clear()
        h.transcript = { Result.success("  ") }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        assertTrue(h.events.contains(SessionEvent.TranscriptionEmpty))
        assertFalse(h.events.any { it is SessionEvent.TranscriptionFailed })
        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun cancelDuringEnhancing_keepsRawText() = sessionTest { h ->
        val gate = CompletableDeferred<DictationEnhancer.Result>()
        h.enhancer = object : DictationEnhancer {
            override suspend fun isEnabled(request: DictationEnhancer.Request) = true
            override suspend fun enhance(request: DictationEnhancer.Request) = gate.await()
        }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()
        assertEquals(SessionState.Enhancing(h.focus, "hello"), h.session.state.value)

        h.session.onCancel()
        gate.complete(DictationEnhancer.Result.Enhanced("HELLO", "P (m)"))
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals("prefix hello", h.ic.getText())
        assertTrue(h.events.contains(SessionEvent.Completed("hello", "hello", 1_000L, h.focus, 1L, STATS)))
        assertEquals(1, h.gateway.abandons)
    }

    @Test
    fun contextPrompt_isCapturedAtStop_beforeFocusMoves() = sessionTest { h ->
        h.useContext = true
        h.recordFor(1_000, locked = true)

        h.session.onStop()
        // Focus moves to another field before the stop coroutine runs.
        h.session.onFocusChanged(h.otherFocus)
        h.ic.setText("secret from field B ")
        advanceUntilIdle()

        assertEquals(listOf("prefix "), h.requests.map { it.contextPrompt })
    }

    @Test
    fun contextPrompt_isOmittedWhenFocusAlreadyMoved() = sessionTest { h ->
        h.useContext = true
        h.recordFor(1_000, locked = true)
        h.session.onFocusChanged(h.otherFocus)
        h.ic.setText("secret from field B ")

        h.session.onStop()
        advanceUntilIdle()

        assertEquals(listOf<String?>(null), h.requests.map { it.contextPrompt })
    }

    @Test
    fun sendBlockedWhileRecorderStops_dispatchesNothing() = sessionTest { h ->
        h.recordFor(1_000, locked = true)

        h.session.onStop()
        h.sendAllowed = false
        advanceUntilIdle()

        assertTrue(h.requests.isEmpty())
        assertTrue(h.events.contains(SessionEvent.SendBlocked))
        assertTrue(h.events.contains(SessionEvent.Discarded(1_000L)))
        assertEquals(SessionState.Idle, h.session.state.value)
        assertFalse(h.audioFile.exists())
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun rejectedInsert_keepsTranscriptPending_insteadOfReportingSuccess() = sessionTest { h ->
        h.ic.rejectInserts = true
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertTrue(h.events.contains(SessionEvent.CommitFailed("hello", SessionEvent.CommitFailed.Reason.INSERT_FAILED)))
        assertFalse(h.events.any { it is SessionEvent.Committed || it is SessionEvent.Completed })
        assertEquals("hello", h.session.pendingCommit.value)

        h.ic.rejectInserts = false
        h.session.onPendingCommitInsert()

        assertEquals("prefix hello", h.ic.getText())
        assertNull(h.session.pendingCommit.value)
    }
}
