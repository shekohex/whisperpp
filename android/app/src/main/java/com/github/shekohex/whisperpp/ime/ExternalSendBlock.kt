package com.github.shekohex.whisperpp.ime

import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN
import com.github.shekohex.whisperpp.privacy.SecureFieldDetector
import com.github.shekohex.whisperpp.privacy.SendPolicyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether sending dictation data out is blocked for the focused field: a secure field, or an app the
 * user blocked. Caches the per-app rules (null until DataStore delivered the first value) and exposes
 * the projection the keyboard shows.
 */
class ExternalSendBlock(
    private val scope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
    sendPolicyRepository: SendPolicyRepository,
    private val currentEditorInfo: () -> EditorInfo?,
) {
    val rules: StateFlow<Map<String, Boolean>?> = sendPolicyRepository.getAllRulesFlow()
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _reason = mutableStateOf<SecureFieldDetector.Reason?>(null)
    private val _blockedByAppPolicy = mutableStateOf(false)
    private val _blockedPackageName = mutableStateOf<String?>(null)

    private val _explanationDontShowAgain = mutableStateOf(false)

    val reason: State<SecureFieldDetector.Reason?> = _reason
    val blockedByAppPolicy: State<Boolean> = _blockedByAppPolicy
    val blockedPackageName: State<String?> = _blockedPackageName

    /** Whether the user chose to hide the "why sending stopped" explanation in secure fields. */
    val explanationDontShowAgain: State<Boolean> = _explanationDontShowAgain

    val isBlocked: Boolean
        get() = _reason.value != null || _blockedByAppPolicy.value

    /** Re-evaluates the focused editor; the result is also readable through [isBlocked]. */
    fun refresh(editorInfo: EditorInfo?) {
        val detection = SecureFieldDetector.detect(editorInfo)
        _reason.value = if (detection.isSecure) detection.reason else null
        val packageName = editorInfo?.packageName?.trim().orEmpty().ifBlank { null }
        val blockedByPolicy = packageName != null && rules.value?.get(packageName) == true
        _blockedByAppPolicy.value = blockedByPolicy
        _blockedPackageName.value = if (blockedByPolicy) packageName else null
    }

    fun shouldBlock(): Boolean {
        refresh(currentEditorInfo())
        return isBlocked
    }

    @StringRes
    fun noticeRes(): Int =
        if (_blockedByAppPolicy.value) R.string.keyboard_sending_blocked_app else R.string.secure_field_sending_stopped

    suspend fun loadExplanationPreference() {
        _explanationDontShowAgain.value = dataStore.data
            .map { it[SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN] ?: false }
            .first()
    }

    fun saveExplanationDontShowAgain() {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN] = true
            }
            _explanationDontShowAgain.value = true
        }
    }
}
