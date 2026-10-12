package com.github.shekohex.whisperpp.ime

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.audio.SoundCues
import com.github.shekohex.whisperpp.data.PreferenceKeys.CANCEL_CONFIRMATION
import com.github.shekohex.whisperpp.data.PreferenceKeys.DISCLOSURE_SHOWN_DICTATION_AUDIO
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.dictation.DictationSession
import com.github.shekohex.whisperpp.domain.dictation.TextCommitter
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import com.github.shekohex.whisperpp.ui.keyboard.EnhancementNoticeStyle
import com.github.shekohex.whisperpp.ui.keyboard.RecordIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Keyboard actions that drive a dictation run: thin forwarders to [DictationSession] plus the checks
 * that must happen first (send policy, first-use disclosure, cancel confirmation, undo).
 */
class DictationIntentRouter(private val deps: Deps) {
    class Deps(
        val context: Context,
        val scope: CoroutineScope,
        val dataStore: DataStore<Preferences>,
        val session: DictationSession,
        val textCommitter: TextCommitter,
        val startPlanner: DictationStartPlanner,
        val disclosures: FirstUseDisclosureGate,
        val ui: KeyboardUiState,
        val undo: KeyboardUndoState,
        val notices: EnhancementNoticeState,
        val feedback: KeyboardFeedback,
        val soundCues: SoundCues,
        val clipboard: ClipboardAccess,
        val shouldBlockExternalSend: () -> Boolean,
        val isCommandModeActive: () -> Boolean,
        val awaitMigrations: suspend () -> Boolean,
        val currentPackageName: () -> String?,
        val currentFocus: () -> FocusKey?,
    )

    // First-use dictation disclosure shown before a start; null until DataStore delivered.
    private val dictationDisclosureShown: StateFlow<Boolean?> = deps.dataStore.data
        .map { it[DISCLOSURE_SHOWN_DICTATION_AUDIO] ?: false }
        .stateIn(deps.scope, SharingStarted.Eagerly, null)
    private var dictationDisclosureJob: Job? = null

    private val session = deps.session
    private val context = deps.context

    val isDisclosureActive: Boolean
        get() = dictationDisclosureJob?.isActive == true

    fun onRecordIntent(intent: RecordIntent) {
        when (intent) {
            is RecordIntent.PressStart -> onPressStart(intent.locked)
            RecordIntent.PressRelease -> session.onPressRelease()
            RecordIntent.Lock -> session.onLock()
            RecordIntent.Cancel -> discardRecording()
            RecordIntent.Stop -> onSendAction()
            RecordIntent.Pause -> session.onPause()
            RecordIntent.Resume -> session.onResume()
            RecordIntent.HoldHint -> deps.ui.showLongPressHint.value = true
            // Haptics are played by the button itself.
            is RecordIntent.Haptic -> Unit
        }
    }

    fun onPressStart(locked: Boolean = false) {
        if (deps.isCommandModeActive()) {
            Log.i(TAG, "press ignored: command mode active")
            return
        }
        if (deps.shouldBlockExternalSend()) {
            deps.feedback.perform()
            return
        }
        deps.undo.clearQuickAction()
        if (dictationDisclosureShown.value == true) {
            session.onPressStart(locked)
            return
        }
        if (dictationDisclosureJob?.isActive == true) {
            Log.i(TAG, "press ignored: disclosure already open")
            return
        }
        dictationDisclosureJob = deps.scope.launch {
            // The finger leaves the button to answer the disclosure, so a confirmed start is hands-free.
            if (confirmDictationDisclosure()) session.onPressStart(locked = true)
        }
    }

    fun onCancelAction() {
        deps.undo.clearQuickAction()
        deps.feedback.perform(cue = SoundCues.Cue.PAUSE)
        if (session.isBusy) confirmCancelAction { session.onCancel() }
    }

    fun onUndoAction() {
        deps.feedback.perform(isDelete = true)
        if (!deps.textCommitter.undoLastInsertion(deps.currentFocus())) {
            context.showShortToast(context.getString(R.string.dictation_undo_unavailable))
        }
        deps.undo.refresh()
    }

    fun onPendingCommitCopy() {
        val text = session.pendingCommit.value ?: return
        deps.clipboard.copy(text)
        session.onPendingCommitDiscard()
        context.showShortToast(context.getString(R.string.dictation_pending_copied))
    }

    fun onCancelConfirmed() {
        val confirm = deps.ui.pendingCancelConfirm.value
        deps.ui.pendingCancelConfirm.value = null
        confirm?.invoke()
    }

    /** A start waiting on the first-use disclosure is abandoned together with the sheet. */
    fun cancelPendingDisclosure() {
        dictationDisclosureJob?.cancel()
    }

    private fun onSendAction() {
        if (deps.shouldBlockExternalSend()) {
            deps.feedback.perform()
            return
        }
        deps.undo.clearQuickAction()
        session.onStop()
    }

    private fun discardRecording() {
        deps.soundCues.play(SoundCues.Cue.PAUSE)
        deps.undo.clearQuickAction()
        session.onCancel()
    }

    private suspend fun confirmDictationDisclosure(): Boolean {
        if (!deps.awaitMigrations()) {
            context.showSetupRequiredToast()
            return false
        }
        val selection = deps.startPlanner.resolveStt(deps.currentPackageName())
        if (selection == null) {
            context.showSetupRequiredToast()
            return false
        }
        val disclosure = PrivacyDisclosureFormatter.disclosureForDictation(
            provider = selection.provider,
            selectedModelId = selection.modelId,
            useContext = selection.prefs[USE_CONTEXT] ?: false,
        )
        return deps.disclosures.await(FirstUseDisclosureGate.Mode.DICTATION_AUDIO, disclosure) ==
            FirstUseDisclosureGate.Decision.CONTINUE
    }

    private fun confirmCancelAction(onConfirm: () -> Unit) {
        deps.scope.launch {
            if (deps.dataStore.data.first()[CANCEL_CONFIRMATION] == false) {
                onConfirm()
            } else {
                deps.ui.pendingCancelConfirm.value = onConfirm
            }
        }
    }

    private companion object {
        const val TAG = "WhisperDictation"
    }
}
