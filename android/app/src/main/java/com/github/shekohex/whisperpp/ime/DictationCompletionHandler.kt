package com.github.shekohex.whisperpp.ime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_SEND_AFTER_INSERT
import com.github.shekohex.whisperpp.data.PreferenceKeys.AUTO_SWITCH_BACK
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.dictation.AutoSend
import com.github.shekohex.whisperpp.domain.dictation.DictationSession
import com.github.shekohex.whisperpp.domain.dictation.SessionEvent
import com.github.shekohex.whisperpp.domain.dictation.TextCommitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What happens after a dictation landed: auto-send (WP-2.4), then the optional switch back to the previous keyboard. */
class DictationCompletionHandler(private val deps: Deps) {
    class Deps(
        val scope: CoroutineScope,
        val dataStore: DataStore<Preferences>,
        val repository: SettingsRepository,
        val session: DictationSession,
        val textCommitter: TextCommitter,
        val autoLearn: AutoLearnObserver,
        val lastFocus: () -> FocusKey?,
        val imeOptions: () -> Int,
        val shouldBlockExternalSend: () -> Boolean,
        val switchIme: () -> Unit,
    )

    /**
     * Auto-send for the field the run inserted into, then the optional switch back. Sends only
     * if that field is still focused, sending is still allowed and no newer run started meanwhile.
     */
    fun onCompleted(event: SessionEvent.Completed) {
        val focus = event.focus
        deps.scope.launch {
            val autoSend = event.inserted && (event.autoSend ?: shouldAutoSend(focus.packageName))
            if (deps.lastFocus() == focus && autoSend) {
                deps.autoLearn.onAutoSend(focus)
                delay(AutoSend.DELAY_MS)
                val stillTarget = deps.lastFocus() == focus && deps.session.latestRunId == event.runId && !deps.session.isBusy
                if (stillTarget && !deps.shouldBlockExternalSend()) {
                    deps.textCommitter.performAutoSend(focus, deps.imeOptions())
                }
            }
            switchBackIfEnabled()
        }
    }

    /** Fallback when the run carries no mode decision: the per-app override wins over the global switch. */
    private suspend fun shouldAutoSend(packageName: String): Boolean {
        val override = deps.repository.appPromptMappings.first().find { it.packageName == packageName }?.autoSend
        return override ?: (deps.dataStore.data.first()[AUTO_SEND_AFTER_INSERT] ?: false)
    }

    private suspend fun switchBackIfEnabled() {
        if (deps.dataStore.data.first()[AUTO_SWITCH_BACK] != true) return
        delay(SWITCH_BACK_DELAY_MS)
        if (!deps.session.isBusy) deps.switchIme()
    }

    private companion object {
        const val SWITCH_BACK_DELAY_MS = 150L
    }
}
