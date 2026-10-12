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

    /** Create (no id) or edit a mode (§4.10). */
    @Serializable
    data class ModeEdit(val id: String? = null) : Destination

    /** The History tab; [failedOnly] opens it filtered to takes that need attention (Home's banner). */
    @Serializable
    data class History(val failedOnly: Boolean = false) : Destination

    /** One transcript (raw vs enhanced, audio, re-enhance). */
    @Serializable
    data class HistoryDetail(val id: Long) : Destination

    @Serializable
    data object Dictionary : Destination

    /** Dictionary settings as a page (Auto-Learn, export / import), e.g. from Privacy & Safety. */
    @Serializable
    data object DictionarySettings : Destination

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

    // Pre-modes per-app and per-language rules; only reachable from Modes until the migration ran.
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
