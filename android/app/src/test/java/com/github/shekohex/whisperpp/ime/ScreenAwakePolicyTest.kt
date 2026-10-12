package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.keyboard.KeyboardState
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenAwakePolicyTest {
    @Test
    fun keepsScreenOn_onlyWhileCapturingAudio() {
        val awake = KeyboardState.entries.filter { shouldKeepScreenOn(it, enabled = true) }

        assertEquals(listOf(KeyboardState.Recording, KeyboardState.RecordingLocked), awake)
    }

    @Test
    fun neverKeepsScreenOn_whenSettingIsOff() {
        assertEquals(emptyList<KeyboardState>(), KeyboardState.entries.filter { shouldKeepScreenOn(it, enabled = false) })
    }
}
