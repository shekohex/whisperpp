package com.github.shekohex.whisperpp.domain.analytics

/** Home greeting sub-line (§4.9) picked from the last 7 days; the UI maps [Variant] to strings. */
object MomentumCopy {
    enum class Variant {
        /** Nothing dictated yet: "Dictate something to unlock insights." */
        GET_STARTED,

        /** History exists, but nothing in the last 7 days: "Welcome back." */
        WELCOME_BACK,

        /** Used on one or two of the last 7 days. */
        KEEP_GOING,

        /** Used on [ON_A_ROLL_DAYS]+ of the last 7 days: "You're on a roll this week." */
        ON_A_ROLL,
    }

    const val ON_A_ROLL_DAYS = 3

    /** [last7DaysSessions]: completed sessions per day for the last 7 days (any order). */
    fun pick(lifetimeSessions: Long, last7DaysSessions: List<Int>): Variant {
        val activeDays = last7DaysSessions.takeLast(7).count { it > 0 }
        return when {
            activeDays >= ON_A_ROLL_DAYS -> Variant.ON_A_ROLL
            activeDays > 0 -> Variant.KEEP_GOING
            lifetimeSessions > 0 -> Variant.WELCOME_BACK
            else -> Variant.GET_STARTED
        }
    }
}
