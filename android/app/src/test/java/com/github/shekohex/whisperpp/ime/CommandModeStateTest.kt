package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.command.CommandStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandModeStateTest {
    @Test
    fun setStage_replacesTheErrorMessage() {
        val state = CommandModeState()

        state.setStage(CommandStage.ERROR, "boom")
        assertEquals("boom", state.errorMessage)

        state.setStage(CommandStage.WAITING)
        assertNull(state.errorMessage)
    }

    @Test
    fun showClipboard_clearsTheUnavailableReason() {
        val state = CommandModeState()
        state.clearClipboard(unavailableReason = "empty")

        state.showClipboard(CommandClipboardPreview.of("abc"))

        assertEquals("abc", state.clipboardPreview)
        assertEquals(3, state.clipboardCharCount)
        assertNull(state.clipboardUnavailableReason)
    }

    @Test
    fun clearClipboard_resetsPreviewAndKeepsTheReason() {
        val state = CommandModeState()
        state.showClipboard(CommandClipboardPreview.of("a".repeat(CommandClipboardPreview.LARGE_THRESHOLD)))
        assertTrue(state.clipboardIsLarge)

        state.clearClipboard(unavailableReason = "empty")

        assertEquals("", state.clipboardPreview)
        assertEquals(0, state.clipboardCharCount)
        assertFalse(state.clipboardIsLarge)
        assertEquals("empty", state.clipboardUnavailableReason)
    }

    @Test
    fun newState_startsWithFullClipboardAttempts() {
        assertEquals(CommandModeState.CLIPBOARD_ATTEMPTS, CommandModeState().clipboardAttemptsRemaining)
    }
}
