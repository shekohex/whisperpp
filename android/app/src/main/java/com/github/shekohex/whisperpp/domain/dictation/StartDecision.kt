package com.github.shekohex.whisperpp.domain.dictation

/** Outcome of the platform-side pre-start checks (migrations, policy, provider, permission). */
sealed interface StartDecision {
    data class Proceed(val plan: StartPlan) : StartDecision

    /** Start refused; the planner already told the user why. */
    data object Abort : StartDecision

    /** Mic permission missing; the session retries after [DictationSession.onPermissionResult]. */
    data object PermissionRequired : StartDecision
}
