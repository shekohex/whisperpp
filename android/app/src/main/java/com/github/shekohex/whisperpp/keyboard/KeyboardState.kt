package com.github.shekohex.whisperpp.keyboard

enum class KeyboardState {
    Ready,
    Recording,
    RecordingLocked,
    Paused,
    PausedLocked,
    Transcribing,
    SmartFixing,
}

val KeyboardState.isRecording: Boolean
    get() = this == KeyboardState.Recording || this == KeyboardState.RecordingLocked

val KeyboardState.isPaused: Boolean
    get() = this == KeyboardState.Paused || this == KeyboardState.PausedLocked

val KeyboardState.isLocked: Boolean
    get() = this == KeyboardState.RecordingLocked || this == KeyboardState.PausedLocked
