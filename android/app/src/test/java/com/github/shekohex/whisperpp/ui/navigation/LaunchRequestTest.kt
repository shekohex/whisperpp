package com.github.shekohex.whisperpp.ui.navigation

import com.github.shekohex.whisperpp.ui.navigation.SettingsDeepLinks.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchRequestTest {
    @Test
    fun plainLaunchHasNoRequest() {
        assertNull(LaunchRequest.from(deepLink = null, showUpdate = false))
        assertNull(LaunchRequest.from(deepLink = "  ", showUpdate = false))
    }

    @Test
    fun deepLinkResolvesThroughSettingsDeepLinks() {
        assertEquals(
            LaunchRequest(Target(Destination.More, Destination.PrivacySafety)),
            LaunchRequest.from(deepLink = "privacy_safety", showUpdate = false),
        )
    }

    @Test
    fun updateNotificationOpensAboutAndChecks() {
        assertEquals(
            LaunchRequest(Target(Destination.More, Destination.About), checkForUpdate = true),
            LaunchRequest.from(deepLink = "backend", showUpdate = true),
        )
    }
}
