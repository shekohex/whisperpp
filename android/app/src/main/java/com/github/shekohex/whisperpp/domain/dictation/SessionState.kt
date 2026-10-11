package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import java.io.File

/** State of the single dictation run owned by [DictationSession] (§4.2). */
sealed interface SessionState {
    data object Idle : SessionState

    /** Config resolution and recorder start are in flight; a release cancels them (B-01). */
    data class Starting(val focus: FocusKey, val locked: Boolean) : SessionState

    /**
     * [startedAt] is the clock value when the current (unpaused) capture segment began;
     * [accumulatedMs] is the recorded time before it.
     */
    data class Recording(
        val focus: FocusKey,
        val startedAt: Long,
        val locked: Boolean,
        val paused: Boolean,
        val accumulatedMs: Long = 0L,
    ) : SessionState {
        fun elapsedMs(now: Long): Long = if (paused) accumulatedMs else accumulatedMs + (now - startedAt).coerceAtLeast(0L)
    }

    data class Transcribing(val focus: FocusKey, val audio: File, val partial: String?) : SessionState

    data class Enhancing(val focus: FocusKey, val raw: String) : SessionState

    data class Committing(val focus: FocusKey, val text: String) : SessionState

    data class Error(val kind: ErrorKind, val recoverable: Boolean) : SessionState

    /** Transient: emitted on a user/system cancel and immediately followed by [Idle]. */
    data object Cancelled : SessionState

    enum class ErrorKind {
        PERMISSION_DENIED,
        MIC_UNAVAILABLE,
        RECORDING_FAILED,
        TRANSCRIPTION_FAILED,
    }
}

/** UI projection; errors surface as a notice event, so the keyboard returns to [KeyboardState.Ready]. */
fun SessionState.toKeyboardState(): KeyboardState = when (this) {
    SessionState.Idle,
    SessionState.Cancelled,
    is SessionState.Starting,
    is SessionState.Error,
    -> KeyboardState.Ready
    is SessionState.Recording -> when {
        paused && locked -> KeyboardState.PausedLocked
        paused -> KeyboardState.Paused
        locked -> KeyboardState.RecordingLocked
        else -> KeyboardState.Recording
    }
    is SessionState.Transcribing,
    is SessionState.Committing,
    -> KeyboardState.Transcribing
    is SessionState.Enhancing -> KeyboardState.SmartFixing
}
