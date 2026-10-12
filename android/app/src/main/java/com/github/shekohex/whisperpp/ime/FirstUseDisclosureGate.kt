package com.github.shekohex.whisperpp.ime

import android.content.res.Resources
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.PreferenceKey
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import com.github.shekohex.whisperpp.ui.keyboard.FirstUseDisclosureUiState
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * One-time "what is sent where" sheet per mode. [await] suspends until the user answers (or the
 * caller is cancelled, which hides the sheet). Only one sheet is pending at a time.
 */
class FirstUseDisclosureGate(
    private val resources: Resources,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    enum class Mode(val shownKey: PreferenceKey<Boolean>) {
        DICTATION_AUDIO(PreferenceKeys.DISCLOSURE_SHOWN_DICTATION_AUDIO),
        ENHANCEMENT_TEXT(PreferenceKeys.DISCLOSURE_SHOWN_ENHANCEMENT_TEXT),
        COMMAND_TEXT(PreferenceKeys.DISCLOSURE_SHOWN_COMMAND_TEXT),
    }

    enum class Decision {
        CONTINUE,
        CANCEL,
        OPEN_SETTINGS,
    }

    private val _uiState = mutableStateOf<FirstUseDisclosureUiState?>(null)
    val uiState: State<FirstUseDisclosureUiState?> = _uiState

    private var pendingMode: Mode? = null
    private var pending: CancellableContinuation<Decision>? = null

    suspend fun await(mode: Mode, disclosure: PrivacyDisclosureFormatter.ModeDisclosure): Decision {
        if (dataStore.data.first()[mode.shownKey] == true) return Decision.CONTINUE

        return suspendCancellableCoroutine { continuation ->
            pending?.let { if (it.isActive) it.resume(Decision.CANCEL) }
            pendingMode = mode
            pending = continuation
            _uiState.value = disclosure.toUiState()
            continuation.invokeOnCancellation {
                if (pending === continuation) clearPending()
            }
        }
    }

    /** Persists "shown" for the pending mode, then continues. */
    fun onContinue() {
        val mode = pendingMode ?: return
        scope.launch {
            dataStore.edit { it[mode.shownKey] = true }
            resolve(Decision.CONTINUE)
        }
    }

    fun onOpenSettings() = resolve(Decision.OPEN_SETTINGS)

    fun cancel() = resolve(Decision.CANCEL)

    private fun resolve(decision: Decision) {
        val continuation = pending
        clearPending()
        if (continuation != null && continuation.isActive) continuation.resume(decision)
    }

    private fun clearPending() {
        pending = null
        pendingMode = null
        _uiState.value = null
    }

    private fun PrivacyDisclosureFormatter.ModeDisclosure.toUiState() = FirstUseDisclosureUiState(
        title = resources.getString(title),
        dataSent = resources.getString(dataSent),
        endpointLines = endpoints.map { PrivacyDisclosureFormatter.endpointLine(resources, it) },
        contextLine = resources.getString(contextLine),
    )
}
