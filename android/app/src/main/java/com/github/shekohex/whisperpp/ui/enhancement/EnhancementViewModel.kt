package com.github.shekohex.whisperpp.ui.enhancement

import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_BACKEND
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_MODEL
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.ui.settings.common.PreferencesViewModel
import kotlinx.coroutines.flow.StateFlow

class EnhancementViewModel(container: AppContainer) : PreferencesViewModel(container.settingsDataStore) {
    val providers: StateFlow<List<ServiceProvider>> = container.settingsRepository.providers.stateInScreen(emptyList())

    /** Picks [provider] and resets the model to its first one. */
    fun selectProvider(provider: ServiceProvider) = edit {
        it[ENHANCEMENT_BACKEND] = provider.id
        it[ENHANCEMENT_MODEL] = provider.models.firstOrNull()?.id ?: ""
    }
}
