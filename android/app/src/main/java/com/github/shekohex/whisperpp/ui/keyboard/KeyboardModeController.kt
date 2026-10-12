package com.github.shekohex.whisperpp.ui.keyboard

import android.util.Log
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.modes.ModesRepository
import com.github.shekohex.whisperpp.domain.modes.ModeAvailability
import com.github.shekohex.whisperpp.ui.modes.ModeGlobals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Keyboard side of modes (§4.10): the chip state for the focused app and the explicit chip choice,
 * which only lasts while the keyboard stays up in the same app.
 */
class KeyboardModeController(
    private val repository: ModesRepository,
    providers: Flow<List<ServiceProvider>>,
    profiles: Flow<List<PromptProfile>>,
    prefs: Flow<Preferences>,
    hasCredential: (providerId: String) -> Boolean,
    private val scope: CoroutineScope,
) {
    private val packageName = MutableStateFlow<String?>(null)

    private val globals: Flow<ModeGlobals> = combine(providers, profiles, prefs) { providerList, profileList, preferences ->
        ModeGlobals.from(preferences, providerList, profileList, hasCredential)
    }

    /** Null until the modes and settings were read. */
    val uiState: StateFlow<KeyboardModeUiState?> = combine(
        repository.modes,
        repository.selectedModeId,
        packageName,
        globals,
    ) { modes, selected, pkg, globalsValue ->
        KeyboardModeUiState.resolve(modes, selected, pkg) {
            globalsValue.availabilityOf(it) == ModeAvailability.Available
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Focus moved to an editor; a different app drops the explicit choice. */
    fun onPackageChanged(newPackage: String?) {
        val pkg = newPackage?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val previous = packageName.value
        packageName.value = pkg
        if (previous != null && previous != pkg) clearSelection()
    }

    fun onWindowHidden() = clearSelection()

    fun select(modeId: String) = write(modeId)

    private fun clearSelection() = write(null)

    private fun write(modeId: String?) {
        scope.launch {
            try {
                repository.selectMode(modeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving the mode selection failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "KeyboardModes"
    }
}
