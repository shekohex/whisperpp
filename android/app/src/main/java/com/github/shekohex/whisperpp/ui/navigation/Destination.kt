package com.github.shekohex.whisperpp.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose routes for the app (§4.8). */
@Serializable
sealed interface Destination {
    /** First-run flow (§4.6); the start destination until completed or skipped. */
    @Serializable
    data object Onboarding : Destination

    // Top-level tabs.
    @Serializable
    data object Home : Destination

    @Serializable
    data object Modes : Destination

    @Serializable
    data object History : Destination

    @Serializable
    data object Dictionary : Destination

    @Serializable
    data object More : Destination

    // AI Models.
    @Serializable
    data object ProviderList : Destination

    @Serializable
    data object ProviderSelections : Destination

    @Serializable
    data class ProviderEdit(val id: String? = null, val cloneFromId: String? = null) : Destination

    // Dictation & audio.
    @Serializable
    data object DictationSettings : Destination

    @Serializable
    data object AudioSettings : Destination

    // Enhancement.
    @Serializable
    data object Enhancement : Destination

    @Serializable
    data object Prompts : Destination

    @Serializable
    data class PromptEdit(val id: String? = null) : Destination

    @Serializable
    data object Presets : Destination

    // Per-app and per-language rules.
    @Serializable
    data object AppMappings : Destination

    @Serializable
    data class AppMappingDetail(val packageName: String) : Destination

    @Serializable
    data object LanguageDefaults : Destination

    // Settings.
    @Serializable
    data object PrivacySafety : Destination

    @Serializable
    data object BackupRestore : Destination

    @Serializable
    data object Insights : Destination

    @Serializable
    data object About : Destination
}
