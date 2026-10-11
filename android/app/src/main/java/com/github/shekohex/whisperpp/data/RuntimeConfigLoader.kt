package com.github.shekohex.whisperpp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.LANGUAGE_CODE
import kotlinx.coroutines.flow.first

/** Reads the current settings snapshot and resolves the effective per-app runtime config. */
class RuntimeConfigLoader(
    private val dataStore: DataStore<Preferences>,
    private val repository: SettingsRepository,
) {
    data class Snapshot(
        val prefs: Preferences,
        val providers: List<ServiceProvider>,
        val effective: EffectiveRuntimeConfig,
    ) {
        val languageCode: String
            get() = prefs[LANGUAGE_CODE] ?: "auto"

        fun provider(id: String): ServiceProvider? = providers.find { it.id == id }
    }

    suspend fun prefs(): Preferences = dataStore.data.first()

    suspend fun load(packageName: String?): Snapshot {
        val prefs = prefs()
        val providers = repository.providers.first()
        val effective = RuntimeSelectionResolver.resolve(
            packageName = packageName,
            languageCode = prefs[LANGUAGE_CODE] ?: "auto",
            providers = providers,
            languageDefaults = repository.profiles.first(),
            appMappings = repository.appPromptMappings.first(),
            globalSttProviderId = prefs[ACTIVE_STT_PROVIDER_ID].orEmpty(),
            globalSttModelId = prefs[ACTIVE_STT_MODEL_ID].orEmpty(),
            globalTextProviderId = prefs[ACTIVE_TEXT_PROVIDER_ID].orEmpty(),
            globalTextModelId = prefs[ACTIVE_TEXT_MODEL_ID].orEmpty(),
            basePrompt = repository.globalBasePrompt.first(),
            profiles = repository.promptProfiles.first(),
        )
        return Snapshot(prefs, providers, effective)
    }
}

/** User-facing summary of resolver warnings for one channel. */
fun List<RuntimeWarning>.toNoticeText(channel: RuntimeChannel): String = filter { it.channel == channel }
    .map { it.message.trim() }
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(separator = " ")
