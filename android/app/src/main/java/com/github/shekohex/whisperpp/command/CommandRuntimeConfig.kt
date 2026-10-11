package com.github.shekohex.whisperpp.command

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.EffectiveRuntimeConfig
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.ServiceProvider

/** STT + text providers for one command-mode run; the command text override wins over the app mapping. */
data class CommandRuntimeConfig(
    val prefs: Preferences,
    val effective: EffectiveRuntimeConfig,
    val languageCode: String,
    val useContext: Boolean,
    val sttProvider: ServiceProvider,
    val sttModelId: String,
    val textProvider: ServiceProvider,
    val textModelId: String,
) {
    sealed interface Resolution {
        data class Ready(val config: CommandRuntimeConfig) : Resolution
        data object MissingStt : Resolution
        data object MissingText : Resolution
    }

    companion object {
        fun resolve(snapshot: RuntimeConfigLoader.Snapshot): Resolution {
            val prefs = snapshot.prefs
            val effective = snapshot.effective
            val sttModelId = effective.sttModelId
            val sttProvider = snapshot.provider(effective.sttProviderId)
            if (sttProvider == null || sttModelId.isBlank() || sttProvider.models.none { it.id == sttModelId }) {
                return Resolution.MissingStt
            }

            val overrideProviderId = prefs[COMMAND_TEXT_PROVIDER_ID]?.trim().orEmpty()
            val overrideModelId = prefs[COMMAND_TEXT_MODEL_ID]?.trim().orEmpty()
            val overridePresent = overrideProviderId.isNotBlank() || overrideModelId.isNotBlank()
            val textProviderId = if (overridePresent) overrideProviderId else effective.textProviderId
            val textModelId = if (overridePresent) overrideModelId else effective.textModelId
            val textProvider = snapshot.provider(textProviderId)
            if (textProvider == null || textModelId.isBlank() || textProvider.models.none { it.id == textModelId }) {
                return Resolution.MissingText
            }

            return Resolution.Ready(
                CommandRuntimeConfig(
                    prefs = prefs,
                    effective = effective,
                    languageCode = snapshot.languageCode,
                    useContext = prefs[USE_CONTEXT] ?: false,
                    sttProvider = sttProvider,
                    sttModelId = sttModelId,
                    textProvider = textProvider,
                    textModelId = textModelId,
                )
            )
        }
    }
}
