package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.dictation.FakeInputConnection
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnRevision
import com.github.shekohex.whisperpp.ime.AutoLearnCaptureStateMachine.BeginResult
import com.github.shekohex.whisperpp.ime.AutoLearnCaptureStateMachine.Evaluation
import com.github.shekohex.whisperpp.ime.AutoLearnCaptureStateMachine.Gates
import com.github.shekohex.whisperpp.ime.AutoLearnCaptureStateMachine.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLearnCaptureStateMachineTest {
    private val focus = FocusKey("com.chat", inputType = 1, fieldId = 7, focusInstanceId = 1L)
    private val open = Gates(enabled = true, secureField = false, blockedApp = false, autoSend = false)
    private val ic = FakeInputConnection()
    private val machine = AutoLearnCaptureStateMachine(clock = { 42L })

    private fun dictate(existing: String, committed: String): BeginResult {
        ic.setText(existing + committed)
        return machine.begin(focus, committed, FieldTextReader.read(ic), open)
    }

    @Test
    fun baseline_locatesCommittedTextBeforeCursor() {
        assertEquals(BeginResult.Started, dictate("Hi. ", "send it to vois ink now"))
        assertTrue(machine.isTracking(focus))
        assertFalse(machine.dirty)
    }

    @Test
    fun editInsideCommittedRegion_yieldsRevisionOfThatRegion() {
        dictate("Hi. ", "send it to vois ink now")
        ic.setText("Hi. send it to VoiceInk now")
        machine.markDirty(focus)

        val revision = machine.finish(FieldTextReader.read(ic), "com.chat")

        assertEquals(AutoLearnRevision("send it to vois ink now", "send it to VoiceInk now", 42L, "com.chat"), revision)
        assertFalse(machine.isActive)
    }

    @Test
    fun unchangedField_orNotDirty_yieldsNothing() {
        dictate("Hi. ", "hello world")
        assertNull(machine.finish(null, "com.chat"))

        dictate("Hi. ", "hello world")
        machine.markDirty(focus)
        assertNull(machine.finish(FieldTextReader.read(ic), "com.chat"))
    }

    @Test
    fun textAppendedAfterDictation_isNotACorrection() {
        dictate("", "hello world")
        ic.setText("hello world and more typed text")

        assertEquals(Evaluation.Unchanged, machine.evaluate(FieldTextReader.read(ic), null))
    }

    @Test
    fun editOutsideCommittedRegion_isIgnored_andEditAcrossItIsUnrelated() {
        dictate("Dear Bob, ", "thanks for the help")
        ic.setText("Dear Robert, thanks for the help")
        assertEquals(Evaluation.Unchanged, machine.evaluate(FieldTextReader.read(ic), null))

        ic.setText("Dear Rob thanks a lot for the help")
        assertEquals(Evaluation.Unrelated, machine.evaluate(FieldTextReader.read(ic), null))
    }

    @Test
    fun deletedRegion_isNotLearned() {
        dictate("Hi. ", "hello world")
        ic.setText("Hi. ")
        machine.markDirty(focus)

        assertNull(machine.finish(FieldTextReader.read(ic), null))
    }

    @Test
    fun windowFallback_isUsedWhenExtractedTextUnavailable() {
        ic.extractedTextAvailable = false
        assertEquals(BeginResult.Started, dictate("x ", "teh cat"))
        ic.setText("x the cat")
        machine.markDirty(focus)

        assertEquals("the cat", machine.finish(FieldTextReader.read(ic), null)?.corrected)
    }

    @Test
    fun cachedSnapshot_isUsedWhenFieldCannotBeRead() {
        dictate("", "teh cat")
        ic.setText("the cat")
        machine.markDirty(focus)
        machine.cache(focus, FieldTextReader.read(ic))

        assertEquals("the cat", machine.finish(null, null)?.corrected)
    }

    @Test
    fun gates_skipCapture() {
        ic.setText("hello")
        val snapshot = FieldTextReader.read(ic)
        fun skip(gates: Gates, text: String = "hello") =
            (machine.begin(focus, text, snapshot, gates) as BeginResult.Skipped).reason

        assertEquals(SkipReason.DISABLED, skip(open.copy(enabled = false)))
        assertEquals(SkipReason.AUTO_SEND, skip(open.copy(autoSend = true)))
        assertEquals(SkipReason.SECURE_FIELD, skip(open.copy(secureField = true)))
        assertEquals(SkipReason.BLOCKED_APP, skip(open.copy(blockedApp = true)))
        assertEquals(SkipReason.EMPTY, skip(open, text = "  "))
        assertEquals(SkipReason.TOO_LONG, skip(open, text = "a".repeat(AutoLearnCaptureStateMachine.MAX_TEXT_CHARS + 1)))
        assertEquals(SkipReason.NOT_FOUND, skip(open, text = "absent"))
        assertEquals(
            SkipReason.NO_SNAPSHOT,
            (machine.begin(focus, "hello", null, open) as BeginResult.Skipped).reason,
        )
        assertFalse(machine.isActive)
    }

    @Test
    fun resume_followsSameFieldInNewFocusInstance_only() {
        dictate("", "teh cat")
        machine.park()
        val other = focus.copy(fieldId = 8, focusInstanceId = 2L)
        val again = focus.copy(focusInstanceId = 3L)

        assertFalse(machine.isSameField(other))
        assertTrue(machine.isSameField(again))
        machine.resume(again)

        assertTrue(machine.isTracking(again))
        assertTrue(machine.dirty)
        assertFalse(machine.parked)
    }
}
