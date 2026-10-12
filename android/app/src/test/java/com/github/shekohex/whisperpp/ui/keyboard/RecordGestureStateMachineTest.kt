package com.github.shekohex.whisperpp.ui.keyboard

import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.ui.keyboard.GestureInput.Down
import com.github.shekohex.whisperpp.ui.keyboard.GestureInput.HoldTimerFired
import com.github.shekohex.whisperpp.ui.keyboard.GestureInput.Move
import com.github.shekohex.whisperpp.ui.keyboard.GestureInput.Up
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent.Cancel
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent.Haptic
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent.HoldHint
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent.Lock
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent.PressRelease
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent.PressStart
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordGestureStateMachineTest {
    // density 2 → cancel at 240 px, lock at 160 px.
    private val density = 2f
    private val touchSlop = 16f

    private enum class Gesture(val inputs: List<GestureInput>) {
        TAP(listOf(Down(0), Up(100))),
        HOLD_RELEASE(listOf(Down(0), HoldTimerFired, Up(1_500))),
        HOLD_SLIDE_LEFT(
            listOf(
                Down(0), HoldTimerFired,
                Move(-20f, 0f, 300), Move(-120f, 2f, 400), Move(-250f, 2f, 500), Move(-300f, -200f, 550),
                Up(700),
            )
        ),
        HOLD_SLIDE_UP(
            listOf(
                Down(0), HoldTimerFired,
                Move(0f, -20f, 300), Move(2f, -100f, 400), Move(2f, -170f, 500), Move(-300f, -200f, 550),
                Up(700),
            )
        ),
        HOLD_SLIDE_LEFT_THEN_BACK(
            listOf(
                Down(0), HoldTimerFired,
                Move(-20f, 0f, 300), Move(-230f, 0f, 400), Move(-236f, 0f, 450), Move(-228f, 0f, 500),
                Move(-100f, 0f, 600), Move(0f, 0f, 700),
                Up(1_500),
            )
        ),

        /** Horizontal wins at slop; the later upward travel must not lock. */
        DIAGONAL(
            listOf(
                Down(0), HoldTimerFired,
                Move(-30f, -10f, 300), Move(-40f, -200f, 400), Move(-40f, -400f, 500),
                Up(1_500),
            )
        ),
        POINTER_CANCEL_MID_HOLD(listOf(Down(0), HoldTimerFired, Move(-50f, 0f, 300), GestureInput.Cancel)),
        SHORT_RECORDING(listOf(Down(0), HoldTimerFired, Up(GestureTokens.HOLD_THRESHOLD_MS + 300))),
    }

    private val lp = Haptic(HapticKind.LongPress)
    private val confirm = Haptic(HapticKind.Confirm)
    private val reject = Haptic(HapticKind.Reject)

    private val idleExpected: Map<Gesture, List<RecordIntent>> = mapOf(
        Gesture.TAP to listOf(PressStart(locked = true), lp),
        Gesture.HOLD_RELEASE to listOf(PressStart(locked = false), lp, PressRelease, confirm),
        Gesture.HOLD_SLIDE_LEFT to listOf(PressStart(locked = false), lp, Cancel, reject),
        Gesture.HOLD_SLIDE_UP to listOf(PressStart(locked = false), lp, Lock, confirm),
        Gesture.HOLD_SLIDE_LEFT_THEN_BACK to listOf(PressStart(locked = false), lp, PressRelease, confirm),
        Gesture.DIAGONAL to listOf(PressStart(locked = false), lp, PressRelease, confirm),
        Gesture.POINTER_CANCEL_MID_HOLD to listOf(PressStart(locked = false), lp, Cancel),
        Gesture.SHORT_RECORDING to listOf(PressStart(locked = false), lp, PressRelease, reject),
    )

    /** An unlocked recording whose gesture was lost is picked up as a hold (no new start). */
    private val regrabExpected: Map<Gesture, List<RecordIntent>> = mapOf(
        Gesture.TAP to listOf(PressRelease, confirm),
        Gesture.HOLD_RELEASE to listOf(PressRelease, confirm),
        Gesture.HOLD_SLIDE_LEFT to listOf(Cancel, reject),
        Gesture.HOLD_SLIDE_UP to listOf(Lock, confirm),
        Gesture.HOLD_SLIDE_LEFT_THEN_BACK to listOf(PressRelease, confirm),
        Gesture.DIAGONAL to listOf(PressRelease, confirm),
        Gesture.POINTER_CANCEL_MID_HOLD to listOf(Cancel),
        Gesture.SHORT_RECORDING to listOf(PressRelease, confirm),
    )

    /** Locked or paused: the main button is "send"; a drag off it is not a tap. */
    private val armedExpected: Map<Gesture, List<RecordIntent>> = mapOf(
        Gesture.TAP to listOf(Stop, confirm),
        Gesture.HOLD_RELEASE to listOf(Stop, confirm),
        Gesture.HOLD_SLIDE_LEFT to emptyList(),
        Gesture.HOLD_SLIDE_UP to emptyList(),
        Gesture.HOLD_SLIDE_LEFT_THEN_BACK to emptyList(),
        Gesture.DIAGONAL to emptyList(),
        Gesture.POINTER_CANCEL_MID_HOLD to emptyList(),
        Gesture.SHORT_RECORDING to listOf(Stop, confirm),
    )

    private val table: Map<RecordSession, Map<Gesture, List<RecordIntent>>> = mapOf(
        RecordSession.Idle to idleExpected,
        RecordSession.Starting to Gesture.entries.associateWith { emptyList() },
        RecordSession.Recording(locked = false, paused = false) to regrabExpected,
        RecordSession.Recording(locked = true, paused = false) to armedExpected,
        RecordSession.Recording(locked = true, paused = true) to armedExpected,
        RecordSession.Recording(locked = false, paused = true) to armedExpected,
        RecordSession.Busy to Gesture.entries.associateWith { emptyList() },
    )

    private fun machine(tapToRecord: Boolean = true, startIsLeft: Boolean = true) =
        RecordGestureStateMachine(density, touchSlop, { tapToRecord }, startIsLeft)

    private fun RecordGestureStateMachine.run(
        session: RecordSession,
        inputs: List<GestureInput>,
    ): List<GestureOutput> = inputs.map { reduce(it, session) }

    private fun List<GestureOutput>.intents(): List<RecordIntent> = flatMap { it.intents }

    @Test
    fun table_everySessionTimesEveryGesture() {
        for ((session, expectations) in table) {
            for (gesture in Gesture.entries) {
                val intents = machine().run(session, gesture.inputs).intents()

                assertEquals("$session × $gesture", expectations.getValue(gesture), intents)
            }
        }
    }

    @Test
    fun table_hapticsNeverRepeatWithinAGesture() {
        val sameMachine = machine()
        for (session in table.keys) {
            for (gesture in Gesture.entries) {
                val haptics = sameMachine.run(session, gesture.inputs).intents().filterIsInstance<Haptic>()

                assertEquals("$session × $gesture: $haptics", haptics.distinct(), haptics)
                assertTrue("$session × $gesture: $haptics", haptics.size <= 2)
            }
        }
    }

    @Test
    fun table_coversEveryKeyboardState() {
        for (state in KeyboardState.entries) {
            assertTrue("$state", table.containsKey(state.toRecordSession()))
        }
    }

    @Test
    fun tap_withTapToRecordOff_onlyHints() {
        val intents = machine(tapToRecord = false).run(RecordSession.Idle, Gesture.TAP.inputs).intents()

        assertEquals(listOf(HoldHint), intents)
    }

    @Test
    fun hold_withTapToRecordOff_stillRecords() {
        val intents = machine(tapToRecord = false).run(RecordSession.Idle, Gesture.HOLD_RELEASE.inputs).intents()

        assertEquals(idleExpected.getValue(Gesture.HOLD_RELEASE), intents)
    }

    @Test
    fun pressing_dragPastSlopBeforeHold_isIgnored() {
        val inputs = listOf(Down(0), Move(-40f, 0f, 50), HoldTimerFired, Move(-300f, 0f, 300), Up(400))

        val intents = machine().run(RecordSession.Idle, inputs).intents()

        assertEquals(emptyList<RecordIntent>(), intents)
    }

    @Test
    fun slideLeft_progressIsClampedAndCommitsExactlyAtThreshold() {
        val m = machine()
        m.reduce(Down(0), RecordSession.Idle)
        m.reduce(HoldTimerFired, RecordSession.Idle)

        val half = m.reduce(Move(-120f, 0f, 300), RecordSession.Idle)
        val almost = m.reduce(Move(-239f, 0f, 350), RecordSession.Idle)
        val past = m.reduce(Move(-500f, 0f, 400), RecordSession.Idle)

        assertEquals(0.5f, half.ui.cancelProgress, 1e-4f)
        assertEquals(GestureAxis.Horizontal, half.ui.axis)
        assertEquals(emptyList<RecordIntent>(), almost.intents)
        assertEquals(listOf(Cancel, reject), past.intents)
        assertEquals(1f, past.ui.cancelProgress, 0f)
        assertEquals(GesturePhase.Cancelled, past.ui.phase)
    }

    @Test
    fun slideLeftThenBack_progressFollowsFingerWithoutCommitting() {
        val outputs = machine().run(RecordSession.Idle, Gesture.HOLD_SLIDE_LEFT_THEN_BACK.inputs)

        val progress = outputs.map { it.ui.cancelProgress }
        assertTrue(progress.all { it in 0f..1f && it < 1f })
        assertEquals(0f, outputs[outputs.size - 2].ui.cancelProgress, 0f)
        assertEquals(GesturePhase.Holding, outputs[outputs.size - 2].ui.phase)
    }

    @Test
    fun diagonal_otherAxisReportsZero() {
        val outputs = machine().run(RecordSession.Idle, Gesture.DIAGONAL.inputs)

        val moves = outputs.subList(2, 5)
        assertTrue(moves.all { it.ui.axis == GestureAxis.Horizontal && it.ui.lockProgress == 0f })
    }

    @Test
    fun axisLock_waitsWhileNeitherAxisLeadsByHysteresis() {
        val m = machine()
        m.reduce(Down(0), RecordSession.Idle)
        m.reduce(HoldTimerFired, RecordSession.Idle)

        val tie = m.reduce(Move(-30f, -29f, 300), RecordSession.Idle)
        val decided = m.reduce(Move(-30f, -60f, 350), RecordSession.Idle)

        assertEquals(GestureAxis.None, tie.ui.axis)
        assertEquals(GestureAxis.Vertical, decided.ui.axis)
        assertEquals(0.375f, decided.ui.lockProgress, 1e-4f)
    }

    @Test
    fun slideUp_lockedPhaseIgnoresRestOfGesture() {
        val m = machine()
        m.reduce(Down(0), RecordSession.Idle)
        m.reduce(HoldTimerFired, RecordSession.Idle)
        val lock = m.reduce(Move(0f, -160f, 300), RecordSession.Idle)

        val later = listOf(Move(-400f, -160f, 400), Up(500)).map { m.reduce(it, RecordSession.Idle) }

        assertEquals(listOf(Lock, confirm), lock.intents)
        assertEquals(GesturePhase.Locked, lock.ui.phase)
        assertTrue(later.all { it.intents.isEmpty() })
        assertEquals(GestureUi.Idle, later.last().ui)
    }

    @Test
    fun rtl_cancelSlidesRight() {
        val m = machine(startIsLeft = false)
        val leftward = listOf(Down(0), HoldTimerFired, Move(-300f, 0f, 300), Up(1_500))
        val rightward = listOf(Down(0), HoldTimerFired, Move(300f, 0f, 300), Up(1_500))

        assertEquals(listOf(PressStart(false), lp, PressRelease, confirm), m.run(RecordSession.Idle, leftward).intents())
        assertEquals(listOf(PressStart(false), lp, Cancel, reject), m.run(RecordSession.Idle, rightward).intents())
    }

    @Test
    fun cancelBeforeHold_emitsNothing() {
        val intents = machine().run(RecordSession.Idle, listOf(Down(0), GestureInput.Cancel)).intents()

        assertEquals(emptyList<RecordIntent>(), intents)
    }

    @Test
    fun uiResetsToIdleWhenGestureEnds() {
        for (gesture in Gesture.entries) {
            val outputs = machine().run(RecordSession.Idle, gesture.inputs)

            assertEquals("$gesture", GestureUi.Idle, outputs.last().ui)
        }
    }
}
