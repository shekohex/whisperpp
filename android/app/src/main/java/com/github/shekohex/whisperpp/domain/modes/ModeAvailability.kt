package com.github.shekohex.whisperpp.domain.modes

import com.github.shekohex.whisperpp.data.RuntimeChannel

/** Whether everything a mode references (or inherits) exists; the UI shows a red "Unavailable" chip otherwise. */
sealed interface ModeAvailability {
    data object Available : ModeAvailability

    /** The first problem found; [reference] is the missing provider/model/prompt id. */
    data class Unavailable(
        val reason: Reason,
        val channel: RuntimeChannel?,
        val reference: String,
    ) : ModeAvailability

    enum class Reason {
        MISSING_PROVIDER,

        /** Not found under its provider, or lacks the needed capability (STT / text). */
        MISSING_MODEL,
        MISSING_CREDENTIAL,
        MISSING_PROMPT,
    }
}
