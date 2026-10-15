package com.github.shekohex.whisperpp.ime

import android.content.Intent
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.snapshotFlow
import com.github.shekohex.whisperpp.EXTRA_SETTINGS_DESTINATION
import com.github.shekohex.whisperpp.MainActivity
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.data.PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "ImeController"

/**
 * Reacts to the platform events the IME service forwards (editor focus, window visibility, Back, destroy)
 * by driving the components of [ImeComponents]. Owns no state of its own.
 */
class ImeController(
    private val host: ImeHost,
    private val scope: CoroutineScope,
    private val c: ImeComponents,
) {
    private val context = host.context

    private val viewFactory = KeyboardViewFactory(
        KeyboardViewFactory.Deps(
            ui = c.ui,
            language = c.language,
            undo = c.undo,
            notices = c.notices,
            sendBlock = c.sendBlock,
            disclosures = c.disclosureGate,
            modes = c.keyboardModes,
            session = c.session,
            router = c.router,
            commands = c.commands,
            commandState = c.commandState,
            onDeleteText = ::deleteText,
            onOpenSettings = ::launchMainActivity,
            onAllowMicPermission = ::launchMicPermissionRequest,
            onBlockedAction = { c.feedback.perform() },
        )
    )

    /** Starts the long-lived observers; call once from the service's `onCreate`. */
    fun start() {
        val projector = KeyboardStateProjector(scope, c.ui)
        projector.observeRecorder(c.audioRecorder, c.commands::onCaptureError)
        projector.observeSession(c.session, c.sessionEvents::onEvent)
        c.undo.refresh()

        scope.launch { c.repository.tapToRecord.collect { c.ui.tapToRecord.value = it } }
        scope.launch { c.repository.commandModeEnabled.collect { c.ui.commandModeEnabled.value = it } }
        scope.launch { c.repository.showModeOnKeyboard.collect { c.ui.showModeOnKeyboard.value = it } }
        scope.launch { c.repository.showLiveTranscript.collect { c.ui.showLiveTranscript.value = it } }
        scope.launch {
            combine(snapshotFlow { c.ui.keyboardState.value }, c.repository.keepScreenAwake, ::shouldKeepScreenOn)
                .distinctUntilChanged()
                .collect(host::setKeepScreenOn)
        }
        scope.launch(Dispatchers.IO) { c.recordingFiles.deleteStale() }
        context.appContainer.pruneHistoryIfDue()

        scope.launch {
            // The retry runs from onStartInputView once the host app regains focus.
            MicPermissionBus.results.collect {
                c.micPermissionGate.onResult(it.granted, it.retryAction)
            }
        }
        scope.launch {
            c.sendBlock.rules.collect { rules ->
                if (rules != null) onExternalSendBlockMayHaveChanged(host.editorInfo)
            }
        }
        scope.launch {
            context.appContainer.settingsDataStore.data.map { it[VERBOSE_NETWORK_LOGS_ENABLED] ?: false }.collect { enabled ->
                context.appContainer.httpLoggingInterceptor.setEnabled(enabled)
            }
        }
    }

    fun createInputView(owners: ImeViewLifecycle): View {
        return viewFactory.create(context, owners, host.decorView)
    }

    fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        // The single place a focus identity is minted: once per editor, not per restart or view start.
        val focusKey = FocusKey.from(attribute, c.focusIdentity.onStartInput(restarting))
        c.keyboardModes.onPackageChanged(attribute?.packageName)
        if (focusKey != c.lastFocusKey) {
            c.lastFocusKey = focusKey
            c.session.onFocusChanged(focusKey)
            c.autoLearnObserver.onFocusChanged(focusKey)
            c.commands.onFocusChanged()
        }
        c.undo.showQuickActionIfAvailable()
        onExternalSendBlockMayHaveChanged(attribute)
    }

    fun onStartInputView(info: EditorInfo?) {
        c.autoLearnObserver.onFocusChanged(c.currentFocusKey())
        c.undo.showQuickActionIfAvailable()
        onExternalSendBlockMayHaveChanged(info)
        c.micPermissionGate.onInputViewStarted()
    }

    fun onFinishInputView() {
        // Transient overlays belong to the view that is going away.
        c.disclosureGate.cancel()
        c.ui.showLongPressHint.value = false
    }

    fun onFieldUpdated() = c.autoLearnObserver.onFieldUpdated()

    fun onFieldLeft() = c.autoLearnObserver.onFieldLeft()

    /** True when a Back key down is ours to consume; the matching key up is handled by [onBackUp]. */
    fun onBackDown(): Boolean {
        if (c.ui.overlayBack != null) return true
        if (c.ui.keyboardState.value != KeyboardState.Ready) {
            Log.d(TAG, "Intercepting Back Key Down: ${c.ui.keyboardState.value}")
            return true
        }
        return false
    }

    /** Closes the topmost sheet, else cancels the running dictation, else hides the keyboard. */
    fun onBackUp() {
        val closeOverlay = c.ui.overlayBack
        if (closeOverlay != null) {
            closeOverlay()
        } else if (c.ui.keyboardState.value != KeyboardState.Ready) {
            Log.d(TAG, "Intercepting Back Key Up: ${c.ui.keyboardState.value}")
            c.router.onCancelAction()
        } else {
            host.hideKeyboard()
        }
    }

    fun onWindowShown() {
        c.session.onWindowShown()
        scope.launch {
            c.language.refresh()
            c.sendBlock.loadExplanationPreference()
            c.sendBlock.refresh(host.editorInfo)
        }
    }

    fun onWindowHidden() {
        c.ui.pendingCancelConfirm.value = null
        c.keyboardModes.onWindowHidden()
        c.disclosureGate.cancel()
        c.router.cancelPendingDisclosure()
        c.session.onWindowHidden()
        c.autoLearnObserver.onFieldLeft()
        c.commands.onWindowHidden()
    }

    /** Releases what the service's `onDestroy` does not own itself. */
    fun onDestroy() {
        c.session.release()
        c.autoLearnObserver.detach()
        c.commands.release()
        c.audioRecorder.release()
        c.audioFocus.release()
        c.soundCues.release()
    }

    private fun onExternalSendBlockMayHaveChanged(editorInfo: EditorInfo?) {
        c.sendBlock.refresh(editorInfo)
        if (c.sendBlock.isBlocked && c.session.isBusy) {
            c.session.onExternalSendBlocked()
        }
    }

    private fun deleteText() {
        c.feedback.perform(isDelete = true)
        if (c.ui.keyboardState.value.isRecording) return
        val ic = host.inputConnection ?: return
        if (TextUtils.isEmpty(ic.getSelectedText(0))) host.deleteBackward()
        else ic.commitText("", 1)
    }

    private fun launchMicPermissionRequest() {
        context.startActivity(PermissionRequestActivity.intent(context, c.micPermissionGate.pendingAction))
    }

    private fun launchMainActivity(destination: String?) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!destination.isNullOrBlank()) {
            intent.putExtra(EXTRA_SETTINGS_DESTINATION, destination)
        }
        context.startActivity(intent)
    }
}
