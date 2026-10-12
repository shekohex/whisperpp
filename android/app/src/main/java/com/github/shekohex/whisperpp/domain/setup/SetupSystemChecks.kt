package com.github.shekohex.whisperpp.domain.setup

import kotlinx.coroutines.flow.Flow

/** Device-level facts the setup flow depends on; Android-backed in the app, faked in tests. */
interface SetupSystemChecks {
    fun isImeEnabled(): Boolean

    fun isImeSelected(): Boolean

    fun isMicGranted(): Boolean

    fun areNotificationsGranted(): Boolean

    /** Emits when the enabled/selected keyboard changes while the app is in front (e.g. IME picker). */
    val changes: Flow<Unit>
}
