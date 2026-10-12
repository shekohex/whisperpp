package com.github.shekohex.whisperpp.ui.settings.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Base for settings screens that read and write individual preference keys. `null` in [prefs]
 * means "not loaded yet" so screens never mistake the first frame for real (default) values.
 */
abstract class PreferencesViewModel(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    val prefs: StateFlow<Preferences?> = dataStore.data.stateInScreen(null)

    fun <T> set(key: Preferences.Key<T>, value: T) = edit { it[key] = value }

    fun edit(block: (MutablePreferences) -> Unit) {
        viewModelScope.launch { dataStore.edit(block) }
    }

    protected fun <T> Flow<T>.stateInScreen(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
