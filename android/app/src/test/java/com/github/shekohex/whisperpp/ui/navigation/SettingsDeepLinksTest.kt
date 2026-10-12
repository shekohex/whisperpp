package com.github.shekohex.whisperpp.ui.navigation

import com.github.shekohex.whisperpp.ui.navigation.SettingsDeepLinks.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDeepLinksTest {

    @Test
    fun blankOrUnknownDestinationsOpenHome() {
        val home = Target(Destination.Home)

        assertEquals(home, SettingsDeepLinks.resolve(null))
        assertEquals(home, SettingsDeepLinks.resolve(""))
        assertEquals(home, SettingsDeepLinks.resolve("   "))
        assertEquals(home, SettingsDeepLinks.resolve("main"))
        assertEquals(home, SettingsDeepLinks.resolve("does_not_exist"))
    }

    @Test
    fun legacyRoutesMapToTabAndDestination() {
        val expected = mapOf(
            "backend" to Target(Destination.More, Destination.ProviderList),
            "provider_selections" to Target(Destination.More, Destination.ProviderSelections),
            "keyboard" to Target(Destination.More, Destination.DictationSettings),
            "post_processing" to Target(Destination.More, Destination.Enhancement),
            "prompts_profiles" to Target(Destination.More, Destination.Prompts),
            "privacy_safety" to Target(Destination.More, Destination.PrivacySafety),
            "backup_restore" to Target(Destination.More, Destination.BackupRestore),
            "analytics" to Target(Destination.More, Destination.Insights),
            "presets" to Target(Destination.More, Destination.Presets),
            "app_mappings" to Target(Destination.Modes),
            "language_defaults" to Target(Destination.Modes),
        )

        expected.forEach { (raw, target) ->
            assertEquals(raw, target, SettingsDeepLinks.resolve(raw))
        }
    }

    @Test
    fun newTopLevelNamesOpenTheirTab() {
        assertEquals(Target(Destination.Modes), SettingsDeepLinks.resolve("modes"))
        assertEquals(Target(Destination.History), SettingsDeepLinks.resolve("history"))
        assertEquals(Target(Destination.Dictionary), SettingsDeepLinks.resolve("dictionary"))
        assertEquals(Target(Destination.More), SettingsDeepLinks.resolve("more"))
        assertEquals(Target(Destination.More, Destination.AudioSettings), SettingsDeepLinks.resolve("audio"))
        assertEquals(Target(Destination.More, Destination.About), SettingsDeepLinks.resolve("about"))
    }

    @Test
    fun privacyDestinationUsedByImeAndBlockedSheetIsStable() {
        assertEquals(
            Target(Destination.More, Destination.PrivacySafety),
            SettingsDeepLinks.resolve(SettingsDeepLinks.PRIVACY_SAFETY),
        )
        assertEquals("privacy_safety", SettingsDeepLinks.PRIVACY_SAFETY)
    }

    @Test
    fun parameterizedRoutesDecodeArguments() {
        assertEquals(Target(Destination.Modes), SettingsDeepLinks.resolve("app_mapping_detail?packageName=com.example.chat"))
        assertEquals(
            Target(Destination.More, Destination.PromptEdit("profile 1")),
            SettingsDeepLinks.resolve("prompt_profile_edit?id=profile%201"),
        )
        assertEquals(
            Target(Destination.More, Destination.ProviderEdit(id = "p1", cloneFromId = null)),
            SettingsDeepLinks.resolve("provider_edit?id=p1&cloneFromId="),
        )
        assertEquals(
            Target(Destination.More, Destination.ProviderEdit(id = null, cloneFromId = "p2")),
            SettingsDeepLinks.resolve("provider_edit?id=&cloneFromId=p2"),
        )
    }

    @Test
    fun missingRequiredArgumentFallsBackToParentScreen() {
        assertEquals(Target(Destination.Modes), SettingsDeepLinks.resolve("app_mapping_detail?packageName="))
        assertEquals(
            Target(Destination.More, Destination.PromptEdit(id = null)),
            SettingsDeepLinks.resolve("prompt_profile_edit"),
        )
    }
}
