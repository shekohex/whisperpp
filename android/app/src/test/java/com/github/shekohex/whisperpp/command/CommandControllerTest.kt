package com.github.shekohex.whisperpp.command

import com.github.shekohex.whisperpp.dictation.FocusKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandControllerTest {

    @Test
    fun shouldShowUndo_expiresAt30sBoundary() {
        val controller = CommandController()
        val focusKey = FocusKey(
            packageName = "com.example",
            inputType = 1,
            fieldId = null,
            focusInstanceId = 1L,
        )
        val now = 1_000L

        controller.startRun(focusKey)
        controller.recordUndoAfterApply(
            focusKey = focusKey,
            snapshot = SelectionSnapshot(start = 1, end = 3),
            originalText = "ORIG",
            appliedText = "APPLIED",
            nowMs = now,
        )

        assertTrue(controller.shouldShowUndo(now))
        assertTrue(controller.shouldShowUndo(now + 29_999L))
        assertFalse(controller.shouldShowUndo(now + 30_000L))
        assertNull(controller.getUndoEntry(now + 30_000L))
    }

    @Test
    fun startRun_clearsPreviousUndoEntry() {
        val controller = CommandController()
        val focusKey = FocusKey(
            packageName = "com.example",
            inputType = 1,
            fieldId = null,
            focusInstanceId = 1L,
        )

        controller.startRun(focusKey)
        controller.recordUndoAfterApply(
            focusKey = focusKey,
            snapshot = SelectionSnapshot(start = 1, end = 3),
            originalText = "ORIG",
            appliedText = "APPLIED",
            nowMs = 1_000L,
        )
        assertNotNull(controller.getUndoEntry(1_000L))

        controller.startRun(focusKey)
        assertFalse(controller.shouldShowUndo(1_000L))
        assertNull(controller.getUndoEntry(1_000L))
    }
}
