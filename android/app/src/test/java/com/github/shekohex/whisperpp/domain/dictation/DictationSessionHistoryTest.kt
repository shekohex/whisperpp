package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeHistory
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.FakeStreaming
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.Harness
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.sessionTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * A take is never lost (§4.8): once the recording stops with audio it is saved, every failure at
 * every stage keeps the audio and marks the take, and only a cancel while recording discards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionHistoryTest {

    private fun Harness.withHistory(): FakeHistory = FakeHistory().also { history = it }

    /** Enough audio for a failed capture to be worth keeping. */
    private fun Harness.withRealAudio() {
        audioFile.writeBytes(ByteArray(10_000))
    }

    private fun Harness.slowEnhancer(gate: CompletableDeferred<DictationEnhancer.Result>) {
        enhancer = object : DictationEnhancer {
            override suspend fun isEnabled(request: DictationEnhancer.Request) = true
            override suspend fun enhance(request: DictationEnhancer.Request) = gate.await()
        }
    }

    // region Failure at each stage keeps the take

    @Test
    fun recorderErrorWhileRecording_keepsThePartialAudio_asFailed() = sessionTest { h ->
        val history = h.withHistory()
        h.withRealAudio()
        h.recordFor(2_000, locked = true)

        h.recorder.errors.emit(AudioRecorder.Event.Error(AudioRecorder.ErrorKind.CAPTURE_FAILED, "mic died"))
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.RECORDER_ERROR, record.reason)
        assertEquals(2_000L, record.take.durationMs)
        assertTrue(h.audioFile.exists())
        assertTrue(h.events.any { it is SessionEvent.RecordingFailed })
        assertTrue(h.events.contains(SessionEvent.RecordingSaved(HistoryFailureReason.RECORDER_ERROR)))
    }

    @Test
    fun recorderErrorWithNoRealAudio_savesNothing() = sessionTest { h ->
        val history = h.withHistory()
        h.recordFor(2_000, locked = true)

        h.recorder.errors.emit(AudioRecorder.Event.Error(AudioRecorder.ErrorKind.WRITE_FAILED, "disk full"))
        advanceUntilIdle()

        assertTrue(history.records.isEmpty())
        assertFalse(h.events.any { it is SessionEvent.RecordingSaved })
        assertFalse(h.audioFile.exists())
    }

    @Test
    fun recorderStopFailing_keepsWhatWasCaptured() = sessionTest { h ->
        val history = h.withHistory()
        h.withRealAudio()
        h.recorder.stopFailure = AudioRecorder.ErrorKind.STOP_TIMEOUT
        h.recordFor(1_500, locked = true)

        h.session.onStop()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.RECORDER_ERROR, record.reason)
        assertTrue(h.audioFile.exists())
        assertTrue(h.events.contains(SessionEvent.RecordingSaved(HistoryFailureReason.RECORDER_ERROR)))
    }

    @Test
    fun transcriptionFailure_keepsTheAudio_andSaysSo() = sessionTest { h ->
        val history = h.withHistory()
        h.transcript = { Result.failure(IllegalStateException("HTTP 500")) }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(
            listOf(HistoryStatus.RECORDED, HistoryStatus.TRANSCRIBING, HistoryStatus.FAILED),
            record.statuses,
        )
        assertEquals(HistoryFailureReason.TRANSCRIPTION_FAILED, record.reason)
        assertTrue(h.audioFile.exists())
        assertTrue(h.events.any { it is SessionEvent.TranscriptionFailed })
        assertTrue(h.events.contains(SessionEvent.RecordingSaved(HistoryFailureReason.TRANSCRIPTION_FAILED)))
    }

    @Test
    fun networkFailure_isTypedAsNetworkError() = sessionTest { h ->
        val history = h.withHistory()
        h.transcript = { Result.failure(IOException("Network unavailable", UnknownHostException("api.example.com"))) }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(HistoryFailureReason.NETWORK_ERROR, history.records.single().reason)
        assertTrue(h.audioFile.exists())
    }

    @Test
    fun providerServiceError_isTypedAsNetworkError() = sessionTest { h ->
        val history = h.withHistory()
        h.transcript = { Result.failure(IOException("wrapped", NetworkError.Timeout())) }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(HistoryFailureReason.NETWORK_ERROR, history.records.single().reason)
    }

    @Test
    fun emptyTranscript_keepsTheTake() = sessionTest { h ->
        val history = h.withHistory()
        h.transcript = { Result.success("  ") }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.EMPTY_TRANSCRIPT, record.reason)
        assertTrue(h.audioFile.exists())
        assertTrue(h.events.contains(SessionEvent.TranscriptionEmpty))
    }

    @Test
    fun transcriptFilteredToNothing_keepsTheRawText() = sessionTest { h ->
        val history = h.withHistory()
        h.postProcess = { "" }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryFailureReason.EMPTY_TRANSCRIPT, record.reason)
        assertEquals("hello", record.rawText)
    }

    @Test
    fun failedStreamAndBatch_keepsTheTake() = sessionTest { h ->
        val history = h.withHistory()
        h.streaming = FakeStreaming(committed = null)
        h.transcript = { Result.failure(IllegalStateException("offline")) }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(HistoryStatus.FAILED, history.records.single().status)
        assertTrue(h.audioFile.exists())
    }

    @Test
    fun enhancementFailure_completesWithRawText_andFlagsIt() = sessionTest { h ->
        val history = h.withHistory()
        h.enhancer = object : DictationEnhancer {
            override suspend fun isEnabled(request: DictationEnhancer.Request) = true
            override suspend fun enhance(request: DictationEnhancer.Request) =
                DictationEnhancer.Result.Kept("provider offline", isError = true)
        }
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.COMPLETED, record.status)
        assertTrue(record.enhancementFailed)
        assertEquals("hello", record.rawText)
        assertNull(record.enhancedText)
        assertEquals("prefix hello", h.ic.getText())
    }

    @Test
    fun editorRejectingTheInsert_keepsTheTake_andResolvesItWhenInsertedLater() = sessionTest { h ->
        val history = h.withHistory()
        h.ic.rejectInserts = true
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.INSERT_FAILED, record.reason)
        assertEquals("hello", record.rawText)
        assertEquals("hello", h.session.pendingCommit.value)

        h.ic.rejectInserts = false
        h.session.onPendingCommitInsert()
        assertEquals(HistoryStatus.COMPLETED, record.status)
    }

    @Test
    fun dismissingTheHeldTranscript_resolvesTheTake() = sessionTest { h ->
        val history = h.withHistory()
        h.recordFor(1_000)
        h.session.onStop()
        h.session.onFocusChanged(h.otherFocus)
        advanceUntilIdle()
        assertEquals(HistoryStatus.FAILED, history.records.single().status)

        h.session.onPendingCommitDiscard()

        assertEquals(HistoryStatus.COMPLETED, history.records.single().status)
    }

    @Test
    fun blockedEditorAtStop_savesTheTake_andNeverSendsIt() = sessionTest { h ->
        val history = h.withHistory()
        h.recordFor(1_000, locked = true)
        h.sendAllowed = false

        h.session.onStop()
        advanceUntilIdle()

        assertTrue(h.requests.isEmpty())
        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.INSERT_BLOCKED, record.reason)
        assertTrue(h.audioFile.exists())
        assertTrue(h.events.contains(SessionEvent.SendBlocked))
        assertTrue(h.events.contains(SessionEvent.RecordingSaved(HistoryFailureReason.INSERT_BLOCKED)))
    }

    // endregion

    // region Cancel and abort

    @Test
    fun cancelWhileRecording_discardsTheTake() = sessionTest { h ->
        val history = h.withHistory()
        h.recordFor(2_000, locked = true)

        h.session.onCancel()
        advanceUntilIdle()

        assertTrue(history.records.isEmpty())
        assertFalse(h.audioFile.exists())
        assertTrue(h.events.any { it is SessionEvent.Discarded })
        assertFalse(h.events.any { it is SessionEvent.RecordingSaved })
    }

    @Test
    fun cancelWhileTranscribing_keepsTheAudio() = sessionTest { h ->
        val history = h.withHistory()
        val gate = CompletableDeferred<Result<String>>()
        h.transcript = { gate.await() }
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()
        assertTrue(h.session.state.value is SessionState.Transcribing)

        h.session.onCancel()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.CANCELLED, record.reason)
        assertTrue(h.audioFile.exists())
        assertTrue(h.transcriberCancelled)
        assertTrue(h.events.contains(SessionEvent.RecordingSaved(HistoryFailureReason.CANCELLED)))
    }

    @Test
    fun cancelWhileEnhancing_insertsRaw_andCompletesTheTake() = sessionTest { h ->
        val history = h.withHistory()
        h.slowEnhancer(CompletableDeferred())
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()
        assertTrue(h.session.state.value is SessionState.Enhancing)

        h.session.onCancel()
        advanceUntilIdle()

        assertEquals("prefix hello", h.ic.getText())
        assertEquals(HistoryStatus.COMPLETED, history.records.single().status)
    }

    @Test
    fun cancelWhileEnhancing_whenTheFieldWasLeft_keepsTheTakeAsBlocked() = sessionTest { h ->
        val history = h.withHistory()
        h.slowEnhancer(CompletableDeferred())
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()
        h.session.onFocusChanged(h.otherFocus)

        h.session.onCancel()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.INSERT_BLOCKED, record.reason)
        assertEquals("hello", record.rawText)
        assertEquals("hello", h.session.pendingCommit.value)
        assertTrue(h.audioFile.exists())
    }

    @Test
    fun externalSendBlockWhileTranscribing_savesTheTake() = sessionTest { h ->
        val history = h.withHistory()
        val gate = CompletableDeferred<Result<String>>()
        h.transcript = { gate.await() }
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()

        h.sendAllowed = false
        h.session.onExternalSendBlocked()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryFailureReason.INSERT_BLOCKED, record.reason)
        assertTrue(h.audioFile.exists())
    }

    @Test
    fun serviceTeardownWhileTranscribing_keepsTheTake_asInterrupted() = sessionTest { h ->
        val history = h.withHistory()
        val gate = CompletableDeferred<Result<String>>()
        h.transcript = { gate.await() }
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()

        h.session.release()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.INTERRUPTED, record.reason)
        assertTrue(h.audioFile.exists())
    }

    @Test
    fun serviceTeardownWhileRecording_finalizesAndKeepsTheCapture() = sessionTest { h ->
        val history = h.withHistory()
        h.withRealAudio()
        h.recordFor(2_000, locked = true)

        h.session.release()
        advanceUntilIdle()

        assertEquals(1, h.recorder.keptCancels)
        val record = history.records.single()
        assertEquals(HistoryStatus.FAILED, record.status)
        assertEquals(HistoryFailureReason.INTERRUPTED, record.reason)
        assertEquals(2_000L, record.take.durationMs)
        assertTrue(h.audioFile.exists())
    }

    @Test
    fun serviceTeardownRightAfterStart_discardsTheTooShortTake() = sessionTest { h ->
        val history = h.withHistory()
        h.withRealAudio()
        h.recordFor(100, locked = true)

        h.session.release()
        advanceUntilIdle()

        assertTrue(history.records.isEmpty())
        assertEquals(0, h.recorder.keptCancels)
        assertFalse(h.audioFile.exists())
    }

    // endregion

    @Test
    fun theTakeIsSavedBeforeTranscriptionStarts() = sessionTest { h ->
        val history = h.withHistory()
        val gate = CompletableDeferred<Result<String>>()
        h.transcript = { gate.await() }
        h.recordFor(1_500, locked = true)

        h.session.onStop()
        runCurrent()

        val record = history.records.single()
        assertEquals(HistoryStatus.TRANSCRIBING, record.status)
        assertEquals(h.audioFile, record.take.file)
        assertEquals(1_500L, record.take.durationMs)
        assertEquals("com.example", record.take.appPackage)
        assertEquals("whisper-1", record.take.stats.sttModelId)
        gate.complete(Result.success("hello"))
        advanceUntilIdle()
        assertEquals(HistoryStatus.COMPLETED, record.status)
    }

    @Test
    fun successfulStreamedRun_completesTheTake() = sessionTest { h ->
        val history = h.withHistory()
        h.streaming = FakeStreaming(committed = "streamed words")
        h.recordFor(1_000)
        h.session.onPressRelease()
        advanceUntilIdle()

        val record = history.records.single()
        assertEquals(HistoryStatus.COMPLETED, record.status)
        assertEquals("streamed words", record.rawText)
    }
}
