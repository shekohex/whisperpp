package com.github.shekohex.whisperpp.ui.keyboard

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Telegram-style record gesture (§4.1) as a pure reducer: pointer input + session kind in,
 * session intents + UI progress out. One instance per pointer-input scope; every [GestureInput.Down]
 * starts a fresh gesture.
 *
 * @param density px per dp, used to convert the dp thresholds in [GestureTokens].
 * @param touchSlopPx movement below this is still a tap and does not lock an axis.
 * @param tapToRecord read on each tap: a tap on an idle button starts a locked recording when true.
 * @param startIsLeft false in RTL layouts, where "slide to cancel" goes right.
 */
class RecordGestureStateMachine(
    density: Float,
    private val touchSlopPx: Float,
    private val tapToRecord: () -> Boolean,
    private val startIsLeft: Boolean = true,
) {
    private val cancelDistancePx = GestureTokens.CANCEL_DISTANCE_DP * density
    private val lockDistancePx = GestureTokens.LOCK_DISTANCE_DP * density

    private var phase = GesturePhase.Idle
    private var axis = GestureAxis.None
    private var downAt = 0L

    /** Start of the hold-to-record started by this gesture; null when it re-grabbed an existing one. */
    private var recordStartedAt: Long? = null
    private var cancelProgress = 0f
    private var lockProgress = 0f
    private val firedHaptics = mutableSetOf<HapticKind>()

    val ui: GestureUi
        get() = GestureUi(phase, axis, cancelProgress, lockProgress)

    /** True while the caller should run the hold timer and deliver [GestureInput.HoldTimerFired]. */
    val awaitingHold: Boolean
        get() = phase == GesturePhase.Pressing

    fun reduce(input: GestureInput, session: RecordSession): GestureOutput {
        val intents = mutableListOf<RecordIntent>()
        when (input) {
            is GestureInput.Down -> onDown(input, session)
            is GestureInput.Move -> onMove(input, intents)
            is GestureInput.Up -> onUp(input, intents)
            GestureInput.Cancel -> onCancel(intents)
            GestureInput.HoldTimerFired -> onHoldTimer(intents)
        }
        val gestureEnded = input is GestureInput.Up || input == GestureInput.Cancel
        if (gestureEnded) reset()
        return GestureOutput(intents.toList(), if (gestureEnded) GestureUi.Idle else ui)
    }

    private fun onDown(input: GestureInput.Down, session: RecordSession) {
        reset()
        downAt = input.t
        phase = when (session) {
            RecordSession.Idle -> GesturePhase.Pressing
            RecordSession.Starting, RecordSession.Busy -> GesturePhase.Ignored
            // An unlocked, unpaused recording is a hold whose gesture was lost: pick it up again.
            is RecordSession.Recording ->
                if (session.locked || session.paused) GesturePhase.Armed else GesturePhase.Holding
        }
    }

    private fun onHoldTimer(intents: MutableList<RecordIntent>) {
        when (phase) {
            GesturePhase.Pressing -> {
                phase = GesturePhase.Holding
                recordStartedAt = downAt + GestureTokens.HOLD_THRESHOLD_MS
                intents += RecordIntent.PressStart(locked = false)
                intents.haptic(HapticKind.LongPress)
            }
            GesturePhase.Idle,
            GesturePhase.Holding,
            GesturePhase.Locked,
            GesturePhase.Cancelled,
            GesturePhase.Armed,
            GesturePhase.Ignored,
            -> Unit
        }
    }

    private fun onMove(input: GestureInput.Move, intents: MutableList<RecordIntent>) {
        when (phase) {
            // Past slop before the hold fires (or on the send button) it is a swipe, not a press.
            GesturePhase.Pressing,
            GesturePhase.Armed,
            -> if (hypot(input.dx, input.dy) > touchSlopPx) phase = GesturePhase.Ignored
            GesturePhase.Holding -> track(input, intents)
            GesturePhase.Idle,
            GesturePhase.Locked,
            GesturePhase.Cancelled,
            GesturePhase.Ignored,
            -> Unit
        }
    }

    private fun track(input: GestureInput.Move, intents: MutableList<RecordIntent>) {
        if (axis == GestureAxis.None) axis = lockAxis(input.dx, input.dy)
        val towardStart = if (startIsLeft) -input.dx else input.dx
        cancelProgress = if (axis == GestureAxis.Horizontal) progress(towardStart, cancelDistancePx) else 0f
        lockProgress = if (axis == GestureAxis.Vertical) progress(-input.dy, lockDistancePx) else 0f
        when {
            cancelProgress >= 1f -> {
                phase = GesturePhase.Cancelled
                intents += RecordIntent.Cancel
                intents.haptic(HapticKind.Reject)
            }
            lockProgress >= 1f -> {
                phase = GesturePhase.Locked
                intents += RecordIntent.Lock
                intents.haptic(HapticKind.Confirm)
            }
        }
    }

    private fun lockAxis(dx: Float, dy: Float): GestureAxis {
        val ax = abs(dx)
        val ay = abs(dy)
        if (maxOf(ax, ay) <= touchSlopPx) return GestureAxis.None
        val lead = 1f + GestureTokens.HYSTERESIS
        return when {
            ax > ay * lead -> GestureAxis.Horizontal
            ay > ax * lead -> GestureAxis.Vertical
            else -> GestureAxis.None
        }
    }

    private fun onUp(input: GestureInput.Up, intents: MutableList<RecordIntent>) {
        when (phase) {
            GesturePhase.Pressing -> if (tapToRecord()) {
                intents += RecordIntent.PressStart(locked = true)
                intents.haptic(HapticKind.LongPress)
            } else {
                intents += RecordIntent.HoldHint
            }
            GesturePhase.Holding -> {
                val startedAt = recordStartedAt
                val tooShort = startedAt != null && input.t - startedAt < GestureTokens.MIN_RECORDING_MS
                intents += RecordIntent.PressRelease
                intents.haptic(if (tooShort) HapticKind.Reject else HapticKind.Confirm)
            }
            GesturePhase.Armed -> {
                intents += RecordIntent.Stop
                intents.haptic(HapticKind.Confirm)
            }
            GesturePhase.Idle,
            GesturePhase.Locked,
            GesturePhase.Cancelled,
            GesturePhase.Ignored,
            -> Unit
        }
    }

    private fun onCancel(intents: MutableList<RecordIntent>) {
        when (phase) {
            GesturePhase.Holding -> intents += RecordIntent.Cancel
            GesturePhase.Idle,
            GesturePhase.Pressing,
            GesturePhase.Locked,
            GesturePhase.Cancelled,
            GesturePhase.Armed,
            GesturePhase.Ignored,
            -> Unit
        }
    }

    private fun MutableList<RecordIntent>.haptic(kind: HapticKind) {
        if (firedHaptics.add(kind)) this += RecordIntent.Haptic(kind)
    }

    private fun reset() {
        phase = GesturePhase.Idle
        axis = GestureAxis.None
        recordStartedAt = null
        cancelProgress = 0f
        lockProgress = 0f
        firedHaptics.clear()
    }

    private fun progress(distance: Float, threshold: Float): Float = (distance / threshold).coerceIn(0f, 1f)
}
