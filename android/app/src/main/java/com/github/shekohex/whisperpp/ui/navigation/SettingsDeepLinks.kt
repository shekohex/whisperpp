package com.github.shekohex.whisperpp.ui.navigation

import java.net.URLDecoder

/**
 * Maps the legacy `EXTRA_SETTINGS_DESTINATION` strings (sent by the IME, the blocked-app sheet
 * and old shortcuts) to a tab plus an optional destination pushed on top of it.
 */
object SettingsDeepLinks {
    const val MAIN = "main"
    const val BACKEND = "backend"
    const val POST_PROCESSING = "post_processing"
    const val KEYBOARD = "keyboard"
    const val PRIVACY_SAFETY = "privacy_safety"
    const val BACKUP_RESTORE = "backup_restore"
    const val ANALYTICS = "analytics"
    const val PROVIDER_SELECTIONS = "provider_selections"
    const val LANGUAGE_DEFAULTS = "language_defaults"
    const val PRESETS = "presets"
    const val PROMPTS_PROFILES = "prompts_profiles"
    const val APP_MAPPINGS = "app_mappings"
    const val APP_MAPPING_DETAIL = "app_mapping_detail"
    const val PROMPT_PROFILE_EDIT = "prompt_profile_edit"
    const val PROVIDER_EDIT = "provider_edit"
    const val AUDIO = "audio"
    const val ABOUT = "about"
    const val MODES = "modes"
    const val HISTORY = "history"
    const val DICTIONARY = "dictionary"
    const val MORE = "more"

    /** Home, where the setup checklist lives while something is missing. */
    const val SETUP = "setup"

    data class Target(val tab: Destination, val destination: Destination? = null)

    private val HOME = Target(Destination.Home)

    /** Resolves [raw] to a navigation target; unknown or malformed values open Home. */
    fun resolve(raw: String?): Target {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return HOME
        val path = value.substringBefore('?')
        val query = parseQuery(value.substringAfter('?', missingDelimiterValue = ""))
        return when (path) {
            MAIN, SETUP -> HOME
            MODES -> Target(Destination.Modes)
            HISTORY -> Target(Destination.History)
            DICTIONARY -> Target(Destination.Dictionary)
            MORE -> Target(Destination.More)
            BACKEND -> more(Destination.ProviderList)
            PROVIDER_SELECTIONS -> more(Destination.ProviderSelections)
            PROVIDER_EDIT -> more(
                Destination.ProviderEdit(id = query["id"], cloneFromId = query["cloneFromId"]),
            )
            KEYBOARD -> more(Destination.DictationSettings)
            AUDIO -> more(Destination.AudioSettings)
            POST_PROCESSING -> more(Destination.Enhancement)
            PROMPTS_PROFILES -> more(Destination.Prompts)
            PROMPT_PROFILE_EDIT -> more(Destination.PromptEdit(id = query["id"]))
            PRIVACY_SAFETY -> more(Destination.PrivacySafety)
            BACKUP_RESTORE -> more(Destination.BackupRestore)
            ANALYTICS -> more(Destination.Insights)
            ABOUT -> more(Destination.About)
            PRESETS -> modes(Destination.Presets)
            APP_MAPPINGS -> modes(Destination.AppMappings)
            LANGUAGE_DEFAULTS -> modes(Destination.LanguageDefaults)
            APP_MAPPING_DETAIL -> query["packageName"]
                ?.let { modes(Destination.AppMappingDetail(it)) }
                ?: modes(Destination.AppMappings)
            else -> HOME
        }
    }

    private fun more(destination: Destination) = Target(Destination.More, destination)

    private fun modes(destination: Destination) = Target(Destination.Modes, destination)

    /** Decodes `a=b&c=d`; blank values are dropped so `id=` means "no id". */
    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=').trim()
            val encoded = pair.substringAfter('=', missingDelimiterValue = "")
            val decoded = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()
                ?.trim()
            if (key.isEmpty() || decoded.isNullOrEmpty()) null else key to decoded
        }.toMap()
    }
}
