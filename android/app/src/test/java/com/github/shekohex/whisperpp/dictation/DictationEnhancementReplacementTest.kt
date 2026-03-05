package com.github.shekohex.whisperpp.dictation

import com.github.shekohex.whisperpp.keyboard.KeyboardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationEnhancementReplacementTest {

    @Test
    fun capturedRangeReplacement_usesOriginalBounds_andDoesNotModifySurroundingText() {
        val deps = TestDeps(
            initialText = "prefix suffix",
            initialSelection = "prefix ".length to "prefix ".length,
        )
        val controller = DictationController(deps.deps)
        val focusKey = FocusKey(
            packageName = "com.example",
            inputType = 1,
            fieldId = null,
            focusInstanceId = 1L,
        )

        controller.onFocusChanged(focusKey)
        val token = controller.onHoldStart(streamingPartialsEnabled = false)
        assertNotNull(token)

        val rawText = "RAW"
        val captured = controller.insertRawAndCaptureSegment(token!!, rawText)
        assertNotNull(captured)

        deps.ic.setSelection(deps.ic.getText().length, deps.ic.getText().length)
        deps.ic.commitText("!", 1)

        val enhancedText = "ENHANCED"
        val replaced = controller.replaceCapturedSegment(captured!!, enhancedText)
        assertTrue(replaced)
        assertEquals("prefix ${enhancedText}suffix!", deps.ic.getText())

        val expectedCursor = "prefix ".length + enhancedText.length
        assertEquals(expectedCursor to expectedCursor, deps.ic.getSelection())
    }

    @Test
    fun focusMismatchDrop_replacementDoesNothing_andDoesNotCreateEnhancementUndo() {
        val deps = TestDeps(
            initialText = "prefix suffix",
            initialSelection = "prefix ".length to "prefix ".length,
        )
        val controller = DictationController(deps.deps)
        val focusKey = FocusKey(
            packageName = "com.example",
            inputType = 1,
            fieldId = null,
            focusInstanceId = 1L,
        )
        controller.onFocusChanged(focusKey)
        val token = controller.onHoldStart(streamingPartialsEnabled = false)
        val captured = controller.insertRawAndCaptureSegment(token!!, "RAW")
        assertNotNull(captured)

        val before = deps.ic.getText()
        controller.onFocusChanged(
            FocusKey(
                packageName = "com.other",
                inputType = 1,
                fieldId = null,
                focusInstanceId = 2L,
            )
        )

        val replaced = controller.replaceCapturedSegment(captured!!, "ENH")
        assertFalse(replaced)
        assertEquals(before, deps.ic.getText())
        assertFalse(controller.isEnhancementUndoAvailable())
    }

    @Test
    fun enhancementUndo_restoresRawWithinOriginalReplacementRange_evenAfterUserEdits() {
        val deps = TestDeps(
            initialText = "prefix suffix",
            initialSelection = "prefix ".length to "prefix ".length,
        )
        val controller = DictationController(deps.deps)
        val focusKey = FocusKey(
            packageName = "com.example",
            inputType = 1,
            fieldId = null,
            focusInstanceId = 1L,
        )
        controller.onFocusChanged(focusKey)

        val token = controller.onHoldStart(streamingPartialsEnabled = false)
        val rawText = "RAW"
        val captured = controller.insertRawAndCaptureSegment(token!!, rawText)
        assertNotNull(captured)

        val enhancedText = "ENHANCED"
        assertTrue(controller.replaceCapturedSegment(captured!!, enhancedText))
        assertTrue(controller.isEnhancementUndoAvailable())

        val start = "prefix ".length
        deps.ic.replaceRange(start + 1, start + 2, "x")

        val undone = controller.applyEnhancementUndo()
        assertTrue(undone)
        assertEquals("prefix ${rawText}suffix", deps.ic.getText())
        assertFalse(controller.isEnhancementUndoAvailable())

        val end = "prefix ".length + rawText.length
        assertEquals(end to end, deps.ic.getSelection())
    }

    private class TestDeps(
        initialText: String,
        initialSelection: Pair<Int, Int>,
    ) {
        val ic = FakeInputConnection().apply {
            setText(initialText)
            setSelection(initialSelection.first, initialSelection.second)
        }

        private var state: KeyboardState = KeyboardState.Ready

        val deps = DictationController.Deps(
            getKeyboardState = { state },
            setKeyboardState = { state = it },
            toast = { _ -> },
            copyToClipboard = { _ -> },
            clearComposing = { },
            getInputConnection = { ic },
            isInsertAllowed = { true },
            startRecording = { _ -> state = KeyboardState.Recording },
            pauseRecording = { state = it },
            resumeRecording = { state = it },
            stopRecording = { state = KeyboardState.Ready },
            cancelTranscription = { },
        )
    }
}
