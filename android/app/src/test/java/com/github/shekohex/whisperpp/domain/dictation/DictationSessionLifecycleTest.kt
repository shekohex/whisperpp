package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.Harness
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.STATS
import com.github.shekohex.whisperpp.domain.dictation.DictationSessionTestSupport.sessionTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionLifecycleTest {

    @Test
    fun releaseBeforePlannerCompletes_endsIdle_withoutRecording() = sessionTest { h ->
        h.plannerGate = CompletableDeferred()
        h.session.onPressStart()
        runCurrent()
        assertTrue(h.session.state.value is SessionState.Starting)

        h.session.onPressRelease()
        h.plannerGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals(0, h.recorder.starts)
        assertEquals(0, h.gateway.requests)
    }

    @Test
    fun releaseWhileRecorderStarting_cancelsStart_andReleasesFocusOnce() = sessionTest { h ->
        h.recorder.startGate = CompletableDeferred()
        h.session.onPressStart()
        runCurrent()
        assertEquals(1, h.recorder.starts)

        h.session.onPressRelease()
        h.recorder.startGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals(1, h.recorder.cancels)
        assertEquals(1, h.gateway.abandons)
        assertFalse(h.audioFile.exists())
    }

    @Test
    fun secondPressWhileStarting_isSwallowed() = sessionTest { h ->
        h.plannerGate = CompletableDeferred()
        h.session.onPressStart()
        h.session.onPressStart()
        runCurrent()
        h.plannerGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, h.plannerCalls)
        assertEquals(1, h.recorder.starts)
    }

    @Test
    fun holdAndRelease_recordsTranscribesAndCommits() = sessionTest { h ->
        val states = mutableListOf<SessionState>()
        val session = h.session
        h.scope.launch { session.state.collect { states += it } }
        h.recordFor(1_000)
        assertEquals(SessionState.Recording(h.focus, startedAt = 0L, locked = false, paused = false), h.session.state.value)

        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(states.any { it is SessionState.Transcribing })
        assertEquals("prefix hello", h.ic.getText())
        assertTrue(h.events.contains(SessionEvent.Committed("hello")))
        assertTrue(h.events.contains(SessionEvent.Completed("hello", "hello", 1_000L, h.focus, 1L, STATS)))
        assertFalse(h.audioFile.exists())
        assertEquals(1, h.recorder.stops)
        assertEquals(0, h.recorder.cancels)
        assertEquals(1, h.gateway.abandons)
    }

    @Test
    fun shortRelease_discardsWithHint() = sessionTest { h ->
        h.recordFor(200)
        h.session.onPressRelease()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(h.events.contains(SessionEvent.ShortPressHint))
        assertEquals(0, h.recorder.stops)
        assertEquals(1, h.recorder.cancels)
        assertEquals("prefix ", h.ic.getText())
    }

    @Test
    fun cancelDuringTranscribing_doesNotCommit_deletesFile_releasesFocus() = sessionTest { h ->
        val gate = CompletableDeferred<Result<String>>()
        h.transcript = { gate.await() }
        h.recordFor(1_000, locked = true)
        h.session.onStop()
        runCurrent()
        assertTrue(h.session.state.value is SessionState.Transcribing)

        h.session.onCancel()
        gate.complete(Result.success("late"))
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertTrue(h.transcriberCancelled)
        assertEquals("prefix ", h.ic.getText())
        assertFalse(h.audioFile.exists())
        assertEquals(1, h.gateway.abandons)
        assertTrue(h.events.contains(SessionEvent.Discarded(1_000L)))
        assertFalse(h.events.any { it is SessionEvent.Committed })
    }

    @Test
    fun recorderStartFailure_entersError_andReleasesFocus() = sessionTest { h ->
        h.recorder.startFailure = AudioRecorder.ErrorKind.MIC_UNAVAILABLE
        h.session.onPressStart()
        advanceUntilIdle()

        assertEquals(
            SessionState.Error(SessionState.ErrorKind.MIC_UNAVAILABLE, recoverable = true),
            h.session.state.value,
        )
        assertTrue(h.events.contains(SessionEvent.RecordingFailed(AudioRecorder.ErrorKind.MIC_UNAVAILABLE)))
        assertEquals(1, h.gateway.requests)
        assertEquals(1, h.gateway.abandons)
        assertFalse(h.session.isBusy)
    }

    @Test
    fun commandModeActive_ignoresMicStart() = sessionTest { h ->
        h.commandMode = true
        h.session.onPressStart()
        advanceUntilIdle()

        assertEquals(SessionState.Idle, h.session.state.value)
        assertEquals(0, h.plannerCalls)
    }

    @Test
    fun everyTerminalPath_releasesRecorderAndFocusExactlyOnce() {
        val paths: Map<String, suspend TestScope.(Harness) -> Unit> = mapOf(
            "send" to { h ->
                h.recordFor(1_000)
                h.session.onPressRelease()
            },
            "cancel while recording" to { h ->
                h.recordFor(1_000, locked = true)
                h.session.onCancel()
            },
            "cancel while paused" to { h ->
                h.recordFor(1_000, locked = true)
                h.session.onPause()
                h.session.onCancel()
            },
            "short press" to { h ->
                h.recordFor(100)
                h.session.onPressRelease()
            },
            "capture error" to { h ->
                h.recordFor(1_000)
                h.recorder.errors.emit(AudioRecorder.Event.Error(AudioRecorder.ErrorKind.CAPTURE_FAILED, "boom"))
            },
            "transcriber failure" to { h ->
                h.transcript = { Result.failure(java.io.IOException("x")) }
                h.recordFor(1_000)
                h.session.onPressRelease()
            },
            "send blocked" to { h ->
                h.recordFor(1_000, locked = true)
                h.sendAllowed = false
                h.session.onStop()
            },
            "window hidden then cancel" to { h ->
                h.recordFor(1_000, locked = true)
                h.session.onWindowHidden()
                h.session.onCancel()
            },
        )
        for ((name, path) in paths) {
            sessionTest { h ->
                path(h)
                advanceUntilIdle()
                // Late intents after the terminal transition must not release anything again.
                h.session.onCancel()
                h.session.onPressRelease()
                advanceUntilIdle()

                assertFalse(name, h.session.isBusy)
                assertEquals("$name: recorder releases", 1, h.recorder.releases)
                assertEquals("$name: focus abandons", 1, h.gateway.abandons)
                assertFalse("$name: temp file", h.audioFile.exists())
            }
        }
    }

    @Test
    fun cancelThenImmediateRestart_keepsTheNewTakesOwnFile() = sessionTest { h ->
        val files = List(2) { File.createTempFile("take$it", ".wav").apply { writeText("pcm") } }
        var next = 0
        h.nextAudioFile = { files[next++] }
        h.recordFor(1_000, locked = true)
        h.session.onPause()
        h.session.onCancel()
        h.recordFor(1_000, locked = true)

        assertEquals(files, h.recorder.startedFiles)
        assertFalse(files[0].exists())
        assertTrue(files[1].exists())

        h.session.onStop()
        advanceUntilIdle()
        assertTrue(h.events.contains(SessionEvent.Committed("hello")))
        assertFalse(files[1].exists())
    }
}
