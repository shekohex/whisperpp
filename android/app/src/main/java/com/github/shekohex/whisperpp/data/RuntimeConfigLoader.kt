package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.LANGUAGE_CODE
import com.github.shekohex.whisperpp.data.modes.readModes
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.ModeResolution
import com.github.shekohex.whisperpp.domain.modes.ModeResolver
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

/** Reads the current settings snapshot and resolves the session's mode and effective runtime config. */
class RuntimeConfigLoader(
    private val dataStore: DataStore<Preferences>,
    private val repository: SettingsRepository,
    /** Whether a provider has a stored API key; only feeds [ModeResolution.availability]. */
    private val hasCredential: (providerId: String) -> Boolean = { true },
    private val gson: Gson = Gson(),
) {
    data class Snapshot(
        val prefs: Preferences,
        val providers: List<ServiceProvider>,
        val mode: ModeResolution,
    ) {
        val effective: EffectiveRuntimeConfig
            get() = mode.runtime

        /** The mode's language, else the keyboard's. */
        val languageCode: String
            get() = mode.languageCode

        fun provider(id: String): ServiceProvider? = providers.find { it.id == id }
    }

    suspend fun prefs(): Preferences = dataStore.data.first()

    /** [modeId] pins a mode (a running session keeps its mode); otherwise selected → app → default. */
    suspend fun load(packageName: String?, modeId: String? = null): Snapshot {
        val prefs = prefs()
        val providers = repository.providers.first()
        val mode = ModeResolver.resolve(
            ModeResolver.Input(
                modes = readModes(prefs, gson),
                selectedModeId = prefs[PreferenceKeys.SELECTED_MODE_ID]?.trim()?.takeIf { it.isNotEmpty() },
                packageName = packageName,
                forcedModeId = modeId,
                legacyActive = prefs[PreferenceKeys.MODES_MIGRATED] != true,
                keyboardLanguage = prefs[LANGUAGE_CODE] ?: ModeResolver.AUTO,
                providers = providers,
                languageDefaults = repository.profiles.first(),
                appMappings = repository.appPromptMappings.first(),
                globalStt = selection(prefs[ACTIVE_STT_PROVIDER_ID], prefs[ACTIVE_STT_MODEL_ID]),
                globalText = selection(prefs[ACTIVE_TEXT_PROVIDER_ID], prefs[ACTIVE_TEXT_MODEL_ID]),
                basePrompt = repository.globalBasePrompt.first(),
                profiles = repository.promptProfiles.first(),
                globalEnhancementEnabled = prefs[PreferenceKeys.ENHANCEMENT_ENABLED] ?: false,
                globalRemoveFillers = prefs[PreferenceKeys.FILTER_FILLERS_ENABLED] ?: false,
                globalFormatParagraphs = prefs[PreferenceKeys.FORMAT_PARAGRAPHS_ENABLED] ?: false,
                globalAutoSend = prefs[PreferenceKeys.AUTO_SEND_AFTER_INSERT] ?: false,
                hasCredential = hasCredential,
            )
        )
        return Snapshot(prefs, providers, mode)
    }

    private fun selection(providerId: String?, modelId: String?): Mode.Selection? {
        val provider = providerId?.trim().orEmpty()
        val model = modelId?.trim().orEmpty()
        return if (provider.isEmpty() && model.isEmpty()) null else Mode.Selection(provider, model)
    }
}

/** User-facing summary of resolver warnings for one channel. */
fun List<RuntimeWarning>.toNoticeText(channel: RuntimeChannel): String = filter { it.channel == channel }
    .map { it.message.trim() }
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(separator = " ")
