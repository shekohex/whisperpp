package com.github.shekohex.whisperpp.domain.setup

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderSelection
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.validateSelections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Computes [SetupStatus] from settings plus [SetupSystemChecks]. Settings changes re-emit on their
 * own; system state (permissions, IME) is re-read on [refresh] — call it on `ON_RESUME`.
 */
class SetupStatusChecker(
    private val systemChecks: SetupSystemChecks,
    providers: Flow<List<ServiceProvider>>,
    preferences: Flow<Preferences>,
    private val hasCredential: (providerId: String) -> Boolean,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val refreshes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val triggers: Flow<Unit> = merge(refreshes, systemChecks.changes).onStart { emit(Unit) }

    val status: Flow<SetupStatus> = combine(providers, preferences, triggers) { providerList, prefs, _ ->
        compute(providerList, prefs)
    }
        .flowOn(dispatcher)
        .distinctUntilChanged()

    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    internal fun compute(providers: List<ServiceProvider>, prefs: Preferences): SetupStatus {
        val validation = validateSelections(
            providers = providers,
            sttProviderId = prefs[PreferenceKeys.ACTIVE_STT_PROVIDER_ID].orEmpty(),
            sttModelId = prefs[PreferenceKeys.ACTIVE_STT_MODEL_ID].orEmpty(),
            textProviderId = prefs[PreferenceKeys.ACTIVE_TEXT_PROVIDER_ID].orEmpty(),
            textModelId = prefs[PreferenceKeys.ACTIVE_TEXT_MODEL_ID].orEmpty(),
            commandProviderId = prefs[PreferenceKeys.COMMAND_TEXT_PROVIDER_ID].orEmpty(),
            commandModelId = prefs[PreferenceKeys.COMMAND_TEXT_MODEL_ID].orEmpty(),
        )
        return SetupStatus(
            imeEnabled = systemChecks.isImeEnabled(),
            imeSelected = systemChecks.isImeSelected(),
            micGranted = systemChecks.isMicGranted(),
            micRequestedBefore = prefs[PreferenceKeys.MIC_PERMISSION_REQUESTED] ?: false,
            sttProviderConfigured = validation.isSttValid && isUsable(providers, validation.effective.stt),
            enhancementConfigured = validation.isTextValid && isUsable(providers, validation.effective.text),
            notificationsGranted = systemChecks.areNotificationsGranted(),
        )
    }

    private fun isUsable(providers: List<ServiceProvider>, selection: ProviderSelection): Boolean {
        val provider = providers.firstOrNull { it.id == selection.providerId } ?: return false
        return provider.authMode == ProviderAuthMode.NO_AUTH || hasCredential(provider.id)
    }
}
