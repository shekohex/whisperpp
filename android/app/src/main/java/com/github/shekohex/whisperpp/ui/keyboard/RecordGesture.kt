package com.github.shekohex.whisperpp.ui.keyboard

import com.github.shekohex.whisperpp.keyboard.KeyboardState

/** Pointer input for [RecordGestureStateMachine]. Times share one clock (pointer uptime); offsets are px from the down position. */
sealed interface GestureInput {
    data class Down(val t: Long) : GestureInput
    data class Move(val dx: Float, val dy: Float, val t: Long) : GestureInput
    data class Up(val t: Long) : GestureInput

    /** Pointer cancelled by the system or the window went away mid-gesture. */
    data object Cancel : GestureInput

    /** [GestureTokens.HOLD_THRESHOLD_MS] elapsed since [Down] without an [Up]. */
    data object HoldTimerFired : GestureInput
}

/** What the gesture needs to know about the dictation session when the finger goes down. */
sealed interface RecordSession {
    data object Idle : RecordSession
    data object Starting : RecordSession
    data class Recording(val locked: Boolean, val paused: Boolean) : RecordSession

    /** Transcribing / enhancing / committing: the main button does nothing. */
    data object Busy : RecordSession
}

/** [isStarting]: the session is still opening the recorder; the projected state is still [KeyboardState.Ready]. */
fun KeyboardState.toRecordSession(isStarting: Boolean = false): RecordSession = when (this) {
    KeyboardState.Ready -> if (isStarting) RecordSession.Starting else RecordSession.Idle
    KeyboardState.Recording -> RecordSession.Recording(locked = false, paused = false)
    KeyboardState.RecordingLocked -> RecordSession.Recording(locked = true, paused = false)
    KeyboardState.Paused -> RecordSession.Recording(locked = false, paused = true)
    KeyboardState.PausedLocked -> RecordSession.Recording(locked = true, paused = true)
    KeyboardState.Transcribing,
    KeyboardState.SmartFixing,
    -> RecordSession.Busy
}

enum class HapticKind {
    LongPress,
    Confirm,
    Reject,
}

/** Session intents emitted by the gesture (and by the accessibility actions / side buttons). */
sealed interface RecordIntent {
    data class PressStart(val locked: Boolean) : RecordIntent
    data object PressRelease : RecordIntent
    data object Lock : RecordIntent
    data object Cancel : RecordIntent
    data object Stop : RecordIntent
    data object Pause : RecordIntent
    data object Resume : RecordIntent

    /** A tap while "tap to record" is off: tell the user to hold instead. */
    data object HoldHint : RecordIntent
    data class Haptic(val kind: HapticKind) : RecordIntent
}

enum class GestureAxis {
    None,
    Horizontal,
    Vertical,
}

enum class GesturePhase {
    Idle,

    /** Finger down on an idle button, hold timer running. */
    Pressing,

    /** Hold-to-record in progress; slide left cancels, slide up locks, release sends. */
    Holding,

    /** Slid up past the lock threshold; the rest of the gesture is ignored. */
    Locked,

    /** Slid left past the cancel threshold; the rest of the gesture is ignored. */
    Cancelled,

    /** Finger down on a locked or paused recording; release stops and sends. */
    Armed,

    /** Finger down while the session is starting or busy. */
    Ignored,
}

data class GestureUi(
    val phase: GesturePhase = GesturePhase.Idle,
    val axis: GestureAxis = GestureAxis.None,
    val cancelProgress: Float = 0f,
    val lockProgress: Float = 0f,
) {
    companion object {
        val Idle = GestureUi()
    }
}

data class GestureOutput(
    val intents: List<RecordIntent>,
    val ui: GestureUi,
)
