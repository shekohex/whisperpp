package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.keyboard.KeyboardState

/** The screen stays on only while audio is being captured; paused and processing takes may let it sleep. */
internal fun shouldKeepScreenOn(state: KeyboardState, enabled: Boolean): Boolean =
    enabled && (state == KeyboardState.Recording || state == KeyboardState.RecordingLocked)
