package com.github.shekohex.whisperpp.ui.modes

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.ModeAvailability
import com.github.shekohex.whisperpp.domain.modes.ModeResolver

/** The global settings a mode inherits from; enough to label its chips and check [availabilityOf]. */
data class ModeGlobals(
    val providers: List<ServiceProvider> = emptyList(),
    val profiles: List<PromptProfile> = emptyList(),
    val stt: Mode.Selection? = null,
    val text: Mode.Selection? = null,
    val enhancementEnabled: Boolean = false,
    val hasCredential: (providerId: String) -> Boolean = { true },
) {
    fun availabilityOf(mode: Mode): ModeAvailability = ModeResolver.availability(
        mode = mode,
        providers = providers,
        profiles = profiles,
        globalStt = stt,
        globalText = text,
        enhancementEnabled = mode.enhancementEnabled ?: enhancementEnabled,
        hasCredential = hasCredential,
    )

    /** Model name of [selection] (or the inherited one); null when nothing is configured. */
    fun modelLabel(selection: Mode.Selection?, global: Mode.Selection?): String? {
        val providerId = selection?.providerId?.ifBlank { null } ?: global?.providerId
        val modelId = selection?.modelId?.ifBlank { null } ?: global?.modelId
        if (modelId.isNullOrBlank()) return null
        val provider = providers.firstOrNull { it.id == providerId }
        return provider?.models?.firstOrNull { it.id == modelId }?.name ?: modelId
    }

    fun promptName(promptId: String?): String? =
        promptId?.let { id -> profiles.firstOrNull { it.id == id }?.name ?: id }

    companion object {
        fun from(
            prefs: Preferences,
            providers: List<ServiceProvider>,
            profiles: List<PromptProfile>,
            hasCredential: (providerId: String) -> Boolean,
        ) = ModeGlobals(
            providers = providers,
            profiles = profiles,
            stt = selection(prefs[PreferenceKeys.ACTIVE_STT_PROVIDER_ID], prefs[PreferenceKeys.ACTIVE_STT_MODEL_ID]),
            text = selection(prefs[PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID], prefs[PreferenceKeys.ACTIVE_TEXT_MODEL_ID]),
            enhancementEnabled = prefs[PreferenceKeys.ENHANCEMENT_ENABLED] ?: false,
            hasCredential = hasCredential,
        )

        private fun selection(providerId: String?, modelId: String?): Mode.Selection? {
            val provider = providerId?.trim().orEmpty()
            val model = modelId?.trim().orEmpty()
            return if (provider.isEmpty() && model.isEmpty()) null else Mode.Selection(provider, model)
        }
    }
}
