package com.github.shekohex.whisperpp.ime

import android.os.SystemClock
import android.util.Log
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.domain.dictation.DictationSession
import com.github.shekohex.whisperpp.domain.dictation.SessionEvent
import com.github.shekohex.whisperpp.domain.dictation.SessionState
import com.github.shekohex.whisperpp.domain.dictation.toKeyboardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Projects recorder and [DictationSession] output (§4.2: `SessionState → KeyboardUiState`) onto [KeyboardUiState]. */
class KeyboardStateProjector(
    private val scope: CoroutineScope,
    private val ui: KeyboardUiState,
) {
    fun observeRecorder(recorder: AudioRecorder, onCaptureError: (AudioRecorder.ErrorKind) -> Unit) {
        scope.launch {
            recorder.level.collect { level ->
                ui.micLevel.floatValue = level
            }
        }
        scope.launch {
            recorder.events.collect { event ->
                if (event is AudioRecorder.Event.Error) onCaptureError(event.kind)
            }
        }
    }

    fun observeSession(session: DictationSession, onEvent: (SessionEvent) -> Unit) {
        scope.launch {
            session.state.collectLatest { state ->
                ui.keyboardState.value = state.toKeyboardState()
                Log.i(TAG, "keyboard=${ui.keyboardState.value} starting=${state is SessionState.Starting}")
                ui.sessionStarting.value = state is SessionState.Starting
                when (state) {
                    is SessionState.Recording -> {
                        ui.showLongPressHint.value = false
                        // Re-collected on every transition, so pausing stops the tick (B-07).
                        while (true) {
                            ui.recordingTimeMs.longValue = state.elapsedMs(SystemClock.elapsedRealtime())
                            if (state.paused) break
                            delay(RECORDING_TICK_MS)
                        }
                    }
                    is SessionState.Transcribing, is SessionState.Committing, is SessionState.Enhancing -> Unit
                    else -> ui.recordingTimeMs.longValue = 0L
                }
            }
        }
        scope.launch { session.events.collect(onEvent) }
        scope.launch { session.pendingCommit.collect { ui.pendingCommitText.value = it } }
        scope.launch { session.liveTranscript.collect { ui.liveTranscript.value = it } }
    }

    private companion object {
        const val TAG = "WhisperDictation"
        const val RECORDING_TICK_MS = 100L
    }
}
