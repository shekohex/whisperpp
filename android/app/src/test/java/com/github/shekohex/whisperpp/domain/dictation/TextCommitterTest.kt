package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.dictation.FakeInputConnection
import com.github.shekohex.whisperpp.dictation.FocusKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCommitterTest {
    private val focus = FocusKey(packageName = "com.example", inputType = 1, fieldId = null, focusInstanceId = 1L)
    private val otherFocus = FocusKey(packageName = "com.other", inputType = 1, fieldId = null, focusInstanceId = 2L)

    private val ic = FakeInputConnection().apply {
        setText("prefix suffix")
        setSelection("prefix ".length, "prefix ".length)
    }
    private val committer = TextCommitter { ic }

    private fun commitRaw(text: String): TextCommitter.CapturedSegment {
        val result = committer.commit(focus, text) as TextCommitter.CommitResult.Inserted
        return requireNotNull(result.segment)
    }

    @Test
    fun capturedRangeReplacement_usesOriginalBounds_andDoesNotModifySurroundingText() {
        val segment = commitRaw("RAW")

        ic.setSelection(ic.getText().length, ic.getText().length)
        ic.commitText("!", 1)

        assertTrue(committer.replaceSegment(segment, "ENHANCED", focus))
        assertEquals("prefix ENHANCEDsuffix!", ic.getText())
        val cursor = "prefix ".length + "ENHANCED".length
        assertEquals(cursor to cursor, ic.getSelection())
    }

    @Test
    fun focusMismatchDrop_replacementDoesNothing_andDoesNotCreateEnhancementUndo() {
        val segment = commitRaw("RAW")
        val before = ic.getText()

        assertFalse(committer.replaceSegment(segment, "ENH", otherFocus))
        assertEquals(before, ic.getText())
        assertFalse(committer.isEnhancementUndoAvailable(otherFocus))
        assertFalse(committer.isEnhancementUndoAvailable(focus))
    }

    @Test
    fun enhancementUndo_restoresRawWithinOriginalReplacementRange_evenAfterUserEdits() {
        val segment = commitRaw("RAW")
        assertTrue(committer.replaceSegment(segment, "ENHANCED", focus))
        assertTrue(committer.isEnhancementUndoAvailable(focus))

        val start = "prefix ".length
        ic.replaceRange(start + 1, start + 2, "x")

        assertTrue(committer.applyEnhancementUndo(focus))
        assertEquals("prefix RAWsuffix", ic.getText())
        assertFalse(committer.isEnhancementUndoAvailable(focus))
        val end = start + "RAW".length
        assertEquals(end to end, ic.getSelection())
    }

    @Test
    fun undoLastInsertion_removesInsertedText_onlyForOwningFocus() {
        commitRaw("RAW")
        assertTrue(committer.isUndoAvailable(focus))
        assertFalse(committer.isUndoAvailable(otherFocus))

        assertTrue(committer.undoLastInsertion(focus))
        assertEquals("prefix suffix", ic.getText())
        assertFalse(committer.isUndoAvailable(focus))
    }

    @Test
    fun commit_withoutEditor_reportsNoField() {
        val detached = TextCommitter { null }
        assertEquals(TextCommitter.CommitResult.NoField, detached.commit(focus, "RAW"))
        assertEquals(TextCommitter.CommitResult.NoField, committer.commit(focus.copy(inputType = 0), "RAW"))
    }
}
