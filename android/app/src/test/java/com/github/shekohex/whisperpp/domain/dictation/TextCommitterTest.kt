package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.dictation.FakeInputConnection
import com.github.shekohex.whisperpp.dictation.FocusKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun partials_withoutSelectionSnapshot_replaceTheirComposition_andCommitReplacesIt() {
        ic.extractedTextAvailable = false

        assertTrue(committer.setComposing(focus, "he"))
        assertTrue(committer.setComposing(focus, "hello"))
        assertEquals("prefix hellosuffix", ic.getText())

        val result = committer.commit(focus, "hello world")

        assertEquals(TextCommitter.CommitResult.Inserted(null), result)
        assertEquals("prefix hello worldsuffix", ic.getText())
        assertNull(ic.composingRegion())
        assertFalse(committer.isUndoAvailable(focus))
    }

    @Test
    fun clearComposing_withoutSelectionSnapshot_stillRemovesPartial() {
        ic.extractedTextAvailable = false
        committer.setComposing(focus, "partial")

        committer.clearComposing(focus)

        assertEquals("prefix suffix", ic.getText())
        assertNull(ic.composingRegion())
    }

    @Test
    fun clearComposing_afterFocusMovedToAnotherField_leavesThatFieldAlone() {
        committer.setComposing(focus, "partial")
        // The connection now belongs to field B, which has its own composing text.
        ic.setText("field B")
        ic.setComposingText("ime", 1)

        committer.clearComposing(otherFocus)

        assertEquals("field Bime", ic.getText())
        assertEquals(7 to 10, ic.composingRegion())
        // Local state is gone: a later clear for A's focus does not touch B either.
        committer.clearComposing(focus)
        assertEquals("field Bime", ic.getText())
    }

    @Test
    fun commit_rejectedByEditor_reportsFailed_withoutUndo() {
        ic.rejectInserts = true

        val result = committer.commit(focus, "RAW")

        assertTrue(result is TextCommitter.CommitResult.Failed)
        assertEquals("prefix suffix", ic.getText())
        assertFalse(committer.isUndoAvailable(focus))
    }

    // region G-03: composing regions never corrupt the captured segment

    @Test
    fun partialThenFinal_capturedSegmentAndUndoTargetExactlyTheCommittedText() {
        assertTrue(committer.setComposing(focus, "hel"))
        assertTrue(committer.setComposing(focus, "hello wor"))
        assertEquals("hello wor", ic.composingText())

        val segment = commitRaw("hello world")

        assertEquals("prefix hello worldsuffix", ic.getText())
        assertNull(ic.composingRegion())
        val start = "prefix ".length
        assertEquals(TextCommitter.CapturedSegment(focus, start, start + "hello world".length, "hello world"), segment)

        assertTrue(committer.replaceSegment(segment, "Hello, world.", focus))
        assertEquals("prefix Hello, world.suffix", ic.getText())
        // The undo entry starts where the partial started, not after it.
        assertTrue(committer.undoLastInsertion(focus))
        assertEquals("prefix suffix", ic.getText())
    }

    @Test
    fun foreignComposingRegion_isFinishedBeforeCapture_andKeptOutOfTheSegment() {
        // Another composer (e.g. a spell checker) marked "suf" as composing.
        ic.setComposingRegion("prefix ".length, "prefix suf".length)

        val segment = commitRaw("RAW")

        assertEquals("prefix RAWsuffix", ic.getText())
        assertEquals("RAW", ic.getText().substring(segment.start, segment.endAtInsert))
    }

    @Test
    fun replaceSegment_withForeignComposingRegion_replacesOnlyTheSegment() {
        val segment = commitRaw("RAW")
        val end = ic.getText().length
        ic.setComposingRegion(end - "fix".length, end)

        assertTrue(committer.replaceSegment(segment, "ENHANCED", focus))

        assertEquals("prefix ENHANCEDsuffix", ic.getText())
    }

    // endregion
}
