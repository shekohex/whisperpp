package com.github.shekohex.whisperpp.domain.setup

/**
 * What first-run setup still needs (§4.6). [notificationsGranted] is informational only and never
 * blocks [isComplete]; [enhancementConfigured] is an optional step.
 */
data class SetupStatus(
    val imeEnabled: Boolean = false,
    val imeSelected: Boolean = false,
    val micGranted: Boolean = false,
    val micRequestedBefore: Boolean = false,
    val sttProviderConfigured: Boolean = false,
    val enhancementConfigured: Boolean = false,
    val notificationsGranted: Boolean = false,
) {
    /** Every required step is done: the Home checklist hides. */
    val isComplete: Boolean
        get() = imeEnabled && imeSelected && micGranted && sttProviderConfigured
}
