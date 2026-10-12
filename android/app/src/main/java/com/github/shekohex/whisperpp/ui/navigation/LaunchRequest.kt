package com.github.shekohex.whisperpp.ui.navigation

/** What the launching intent asked for: a deep-link target and/or the update screen. */
data class LaunchRequest(
    val target: SettingsDeepLinks.Target,
    val checkForUpdate: Boolean = false,
) {
    companion object {
        /** `null` when the intent carries nothing to navigate to (plain launcher start). */
        fun from(deepLink: String?, showUpdate: Boolean): LaunchRequest? = when {
            showUpdate -> LaunchRequest(SettingsDeepLinks.Target(Destination.More, Destination.About), checkForUpdate = true)
            deepLink.isNullOrBlank() -> null
            else -> LaunchRequest(SettingsDeepLinks.resolve(deepLink))
        }
    }
}
