package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.dictation.FakeInputConnection
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnRevision
import com.github.shekohex.whisperpp.domain.dictation.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLearnObserverTest {
    private val field = FocusKey("com.chat", inputType = 1, fieldId = 7, focusInstanceId = 1L)
    private val ic = FakeInputConnection()
    private var focus: FocusKey? = field
    private var gates = AutoLearnCaptureStateMachine.Gates(enabled = true, secureField = false, blockedApp = false, autoSend = false)
    private val revisions = mutableListOf<AutoLearnRevision>()

    private fun observer(scope: CoroutineScope) = AutoLearnObserver(
        scope = scope,
        inputConnection = { ic },
        currentFocus = { focus },
        gates = { gates },
        onRevision = { revisions += it },
        clock = { 1L },
    )

    private fun completed(text: String, autoSend: Boolean? = null) = SessionEvent.Completed(
        rawText = text,
        finalText = text,
        durationMs = 1_000L,
        focus = field,
        runId = 1L,
        autoSend = autoSend,
    )

    private fun TestScope.dictate(observer: AutoLearnObserver, text: String) {
        ic.setText(text)
        observer.onCompleted(completed(text))
        runCurrent()
    }

    @Test
    fun deadline_submitsEditedRegion() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat sat")
        ic.setText("the cat sat")
        observer.onFieldUpdated()

        advanceTimeBy(AutoLearnObserver.DEADLINE_MS + 1)

        assertEquals(listOf(AutoLearnRevision("teh cat sat", "the cat sat", 1L, "com.chat")), revisions)
        assertFalse(observer.isCapturing)
    }

    @Test
    fun deadline_withoutEdits_submitsNothing() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat sat")

        advanceTimeBy(AutoLearnObserver.DEADLINE_MS + 1)

        assertTrue(revisions.isEmpty())
        assertFalse(observer.isCapturing)
    }

    @Test
    fun autoSend_cancelsCapture() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        observer.onAutoSend(field)
        ic.setText("the cat")
        observer.onFieldLeft()

        advanceTimeBy(AutoLearnObserver.DEADLINE_MS + 1)
        assertTrue(revisions.isEmpty())
        assertFalse(observer.isCapturing)
    }

    @Test
    fun modeAutoSend_andSecureField_neverStart() = runTest {
        val observer = observer(backgroundScope)
        ic.setText("teh cat")
        observer.onCompleted(completed("teh cat", autoSend = true).also { gates = gates.copy(autoSend = it.autoSend == true) })
        assertFalse(observer.isCapturing)

        gates = gates.copy(autoSend = false, secureField = true)
        observer.onCompleted(completed("teh cat"))
        assertFalse(observer.isCapturing)
    }

    @Test
    fun leavingEditedField_submitsImmediately() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        ic.setText("the cat")
        observer.onFieldUpdated()

        observer.onFieldLeft()

        assertEquals("the cat", revisions.single().corrected)
        assertFalse(observer.isCapturing)
    }

    @Test
    fun leavingUnchangedField_parks_andReturningAfterKeyboardSwitchStillLearns() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        observer.onFieldLeft()
        assertTrue(observer.isCapturing)

        // Fixed with another keyboard, then back in the same field (new focus instance).
        ic.setText("the cat")
        focus = field.copy(focusInstanceId = 2L)
        observer.onFocusChanged(focus)
        observer.onFieldLeft()

        assertEquals("the cat", revisions.single().corrected)
    }

    @Test
    fun returningAfterKeyboardSwitch_submitsCorrectionWithoutAnotherFieldUpdate() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        observer.onFieldLeft()
        ic.setText("the cat")
        focus = field.copy(focusInstanceId = 2L)

        observer.onFocusChanged(focus)

        assertEquals("the cat", revisions.single().corrected)
        assertFalse(observer.isCapturing)
    }

    @Test
    fun serviceRecreated_resumesCaptureEvenWhenFocusInstanceIdRestarts() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        observer.detach()
        assertTrue(observer.isCapturing)
        ic.setText("the cat")
        observer.attach(inputConnection = { ic }, currentFocus = { field }, gates = { gates })

        observer.onFocusChanged(field)

        assertEquals("the cat", revisions.single().corrected)
        assertFalse(observer.isCapturing)
    }

    @Test
    fun serviceDestroyed_readsLastCorrectionBeforeDetaching() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        ic.setText("the cat")

        observer.detach()

        assertEquals("the cat", revisions.single().corrected)
        assertFalse(observer.isCapturing)
    }

    @Test
    fun privacyGatesChangedWhileAway_discardCaptureWithoutLearning() = runTest {
        val deniedGates = listOf(
            gates.copy(enabled = false),
            gates.copy(secureField = true),
            gates.copy(blockedApp = true),
        )
        for (denied in deniedGates) {
            gates = AutoLearnCaptureStateMachine.Gates(enabled = true, secureField = false, blockedApp = false, autoSend = false)
            val observer = observer(backgroundScope)
            dictate(observer, "teh cat")
            observer.onFieldLeft()
            ic.setText("the cat")
            gates = denied

            observer.onFocusChanged(field)

            assertTrue(revisions.isEmpty())
            assertFalse(observer.isCapturing)
        }
    }

    @Test
    fun disabledDuringActiveCapture_discardsCorrectionAtDeadline() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        ic.setText("the cat")
        observer.onFieldUpdated()
        gates = gates.copy(enabled = false)

        advanceTimeBy(AutoLearnObserver.DEADLINE_MS + 1)

        assertTrue(revisions.isEmpty())
        assertFalse(observer.isCapturing)
    }

    @Test
    fun blockedBeforeNextDictation_doesNotSubmitCachedCorrection() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        ic.setText("the cat")
        observer.onFieldUpdated()
        advanceTimeBy(AutoLearnObserver.REFRESH_DEBOUNCE_MS + 1)
        gates = gates.copy(blockedApp = true)

        observer.onCompleted(completed("next dictation"))

        assertTrue(revisions.isEmpty())
        assertFalse(observer.isCapturing)
    }

    @Test
    fun detachedCapture_expiresAtOriginalDeadline() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        advanceTimeBy(AutoLearnObserver.DEADLINE_MS - 1)
        observer.detach()

        advanceTimeBy(2)
        ic.setText("the cat")
        observer.attach(inputConnection = { ic }, currentFocus = { field }, gates = { gates })
        observer.onFocusChanged(field)

        assertTrue(revisions.isEmpty())
        assertFalse(observer.isCapturing)
    }

    @Test
    fun focusOnOtherField_endsCaptureWithCachedText() = runTest {
        val observer = observer(backgroundScope)
        dictate(observer, "teh cat")
        ic.setText("the cat")
        observer.onFieldUpdated()
        advanceTimeBy(AutoLearnObserver.REFRESH_DEBOUNCE_MS + 1)

        ic.setText("unrelated other field")
        focus = FocusKey("com.chat", inputType = 1, fieldId = 9, focusInstanceId = 2L)
        observer.onFocusChanged(focus)

        assertEquals("the cat", revisions.single().corrected)
        assertFalse(observer.isCapturing)
    }
}
