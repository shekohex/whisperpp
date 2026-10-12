package com.github.shekohex.whisperpp.ui.providers

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_STT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.ui.settings.common.PreferencesViewModel
import kotlinx.coroutines.flow.StateFlow

/** Active STT / text / command provider+model selections. */
class ProviderSelectionsViewModel(container: AppContainer) : PreferencesViewModel(container.settingsDataStore) {

    enum class Slot(
        val providerKey: Preferences.Key<String>,
        val modelKey: Preferences.Key<String>,
    ) {
        STT(ACTIVE_STT_PROVIDER_ID, ACTIVE_STT_MODEL_ID),
        TEXT(ACTIVE_TEXT_PROVIDER_ID, ACTIVE_TEXT_MODEL_ID),
        COMMAND(COMMAND_TEXT_PROVIDER_ID, COMMAND_TEXT_MODEL_ID),
    }

    val providers: StateFlow<List<ServiceProvider>> = container.settingsRepository.providers.stateInScreen(emptyList())

    /** Clears both provider and model; for [Slot.COMMAND] this means "inherit enhancement". */
    fun clear(slot: Slot) = edit {
        it.remove(slot.providerKey)
        it.remove(slot.modelKey)
    }

    /** Selects [provider] and resets the model, which must be chosen from the new provider. */
    fun selectProvider(slot: Slot, provider: ServiceProvider) = edit {
        it[slot.providerKey] = provider.id
        it.remove(slot.modelKey)
    }

    fun selectModel(slot: Slot, modelId: String) = set(slot.modelKey, modelId)
}
