package com.github.shekohex.whisperpp.ui.keyboard

/** Every threshold and duration of the record gesture (§4.1) in one place. Distances are in dp. */
object GestureTokens {
    /** A press shorter than this is a tap; reaching it starts a hold-to-record. */
    const val HOLD_THRESHOLD_MS: Long = 180L

    /** How far a finger may wander during the hold delay and still count as a press (not a swipe). */
    const val PRESS_WANDER_DP: Float = 32f

    /** Horizontal travel towards the start edge that commits a cancel. */
    const val CANCEL_DISTANCE_DP: Float = 120f

    /** Upward travel that commits a lock (hands-free). */
    const val LOCK_DISTANCE_DP: Float = 80f

    /** Holds released before this much recording are discarded (mirrors the session's minimum). */
    const val MIN_RECORDING_MS: Long = 500L

    /** Blink period of the recording dot; the label itself only changes once per second. */
    const val TIMER_BLINK_PERIOD_MS: Int = 1000

    /** How far the "slide to cancel" label travels with the finger at full progress. */
    const val CANCEL_HINT_TRAVEL_DP: Float = 48f

    /** Trash icon scale at full cancel progress. */
    const val CANCEL_TRASH_MAX_SCALE: Float = 1.4f

    /** How far the lock icon rises at full lock progress. */
    const val LOCK_HINT_RISE_DP: Float = 10f

    /** Scale bump of the lock icon when it clicks shut. */
    const val LOCK_CLICK_SCALE: Float = 1.3f

    /** Duration of the trash shake / lock click feedback animations. */
    const val COMMIT_FEEDBACK_MS: Int = 240
}
