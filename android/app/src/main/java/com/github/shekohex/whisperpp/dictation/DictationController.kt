package com.github.shekohex.whisperpp.dictation

import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isLocked
import com.github.shekohex.whisperpp.keyboard.isPaused
import com.github.shekohex.whisperpp.keyboard.isRecording
import java.util.ArrayDeque

class DictationController(
    private val deps: Deps,
) {
    data class Deps(
        val getKeyboardState: () -> KeyboardState,
        val setKeyboardState: (KeyboardState) -> Unit,
        val toast: (String) -> Unit,
        val copyToClipboard: (String) -> Unit,
        val clearComposing: () -> Unit,
        val getInputConnection: () -> InputConnection?,
        val isInsertAllowed: () -> Boolean,
        val startRecording: (SendToken) -> Unit,
        val pauseRecording: (KeyboardState) -> Unit,
        val resumeRecording: (KeyboardState) -> Unit,
        val stopRecording: () -> Unit,
        val cancelTranscription: () -> Unit,
    )

    data class SendToken(
        val sessionId: Long,
        val focusKey: FocusKey?,
        val streamingPartialsEnabled: Boolean,
    )

    private data class Session(
        val sessionId: Long,
        val focusKeyAtStart: FocusKey?,
        val streamingPartialsEnabled: Boolean,
        var focusKeyAtSend: FocusKey? = null,
        var pendingTranscript: String? = null,
        var cancelled: Boolean = false,
    )

    private var nextSessionId: Long = 1L
    private var currentFocusKey: FocusKey? = null
    private var activeSession: Session? = null

    private val undoStacks = mutableMapOf<FocusKey, ArrayDeque<DictationUndoEntry>>()

    fun onFocusChanged(newFocusKey: FocusKey?) {
        currentFocusKey = newFocusKey
        if (activeSession == null) return

        val state = deps.getKeyboardState()
        if (state != KeyboardState.Ready && !state.isPaused) {
            autoPause(showToast = false)
        }
    }

    fun isUndoAvailable(): Boolean {
        val key = currentFocusKey ?: return false
        return undoStacks[key]?.isNotEmpty() == true
    }

    fun undoLastInsertion() {
        val focusKey = currentFocusKey
        if (focusKey == null) {
            deps.toast("Can't undo here")
            return
        }

        val stack = undoStacks[focusKey]
        val entry = stack?.peekLast()
        if (entry == null) return

        val ic = deps.getInputConnection()
        if (ic == null) {
            undoStacks.remove(focusKey)
            deps.toast("Can't undo here")
            return
        }

        val applied = applyUndoSafely(ic, focusKey, entry)
        if (!applied) {
            undoStacks.remove(focusKey)
            deps.toast("Can't undo here")
            return
        }

        stack.removeLast()
        if (stack.isEmpty()) {
            undoStacks.remove(focusKey)
        }
    }

    fun onWindowShown() {
    }

    fun onWindowHidden(toastMessage: String? = null) {
        if (activeSession == null) return
        autoPause(showToast = toastMessage != null, toastMessage = toastMessage)
    }

    fun onHoldStart(streamingPartialsEnabled: Boolean): SendToken? {
        if (deps.getKeyboardState() != KeyboardState.Ready) return null
        val sessionId = nextSessionId++
        val token = SendToken(
            sessionId = sessionId,
            focusKey = currentFocusKey,
            streamingPartialsEnabled = streamingPartialsEnabled,
        )
        activeSession = Session(
            sessionId = sessionId,
            focusKeyAtStart = currentFocusKey,
            streamingPartialsEnabled = streamingPartialsEnabled,
        )
        deps.startRecording(token)
        return token
    }

    fun onHoldRelease() {
        val state = deps.getKeyboardState()
        if (!state.isRecording) return
        deps.pauseRecording(if (state.isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
    }

    fun onResume() {
        val state = deps.getKeyboardState()
        if (!state.isPaused) return
        deps.resumeRecording(if (state.isLocked) KeyboardState.RecordingLocked else KeyboardState.Recording)
    }

    fun onLock() {
        if (deps.getKeyboardState() == KeyboardState.Recording) {
            deps.setKeyboardState(KeyboardState.RecordingLocked)
        }
    }

    fun onUnlock() {
        if (deps.getKeyboardState() == KeyboardState.RecordingLocked) {
            deps.pauseRecording(KeyboardState.PausedLocked)
        }
    }

    fun onSendRequested(): SendToken? {
        val session = activeSession ?: return null
        if (session.cancelled) return null
        val state = deps.getKeyboardState()
        if (!state.isPaused) return null

        val cached = session.pendingTranscript
        val token = SendToken(
            sessionId = session.sessionId,
            focusKey = currentFocusKey,
            streamingPartialsEnabled = session.streamingPartialsEnabled,
        )
        session.focusKeyAtSend = token.focusKey
        if (!cached.isNullOrBlank()) {
            onFinalTranscript(token, cached)
            return null
        }

        deps.stopRecording()
        deps.setKeyboardState(KeyboardState.Transcribing)
        return token
    }

    fun onCancelConfirmed() {
        val session = activeSession ?: return
        session.cancelled = true
        deps.cancelTranscription()
        deps.stopRecording()
        deps.clearComposing()
        deps.setKeyboardState(KeyboardState.Ready)
        activeSession = null
    }

    fun onPartialTranscript(token: SendToken, text: String) {
        val session = activeSession ?: return
        if (!shouldAcceptCallback(session, token)) return
        if (!session.streamingPartialsEnabled) return
        if (!isSafeToInsert(token)) return
        val ic = deps.getInputConnection() ?: return
        ic.setComposingText(text, 1)
    }

    fun onFinalTranscript(token: SendToken, text: String) {
        val session = activeSession ?: return
        if (!shouldAcceptCallback(session, token)) return
        session.pendingTranscript = text

        if (!isSafeToInsert(token)) {
            deps.copyToClipboard(text)
            deps.toast("Focus changed; transcript copied")
            deps.setKeyboardState(if (deps.getKeyboardState().isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
            return
        }

        if (token.focusKey?.inputType == 0) {
            deps.copyToClipboard(text)
            deps.toast("No field focused; copied to clipboard")
            deps.setKeyboardState(if (deps.getKeyboardState().isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
            return
        }

        val ic = deps.getInputConnection()
        if (ic == null) {
            deps.copyToClipboard(text)
            deps.toast("No field focused; copied to clipboard")
            deps.setKeyboardState(if (deps.getKeyboardState().isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
            return
        }

        val focusKey = token.focusKey ?: currentFocusKey
        val selectionBefore = ic.selectionSnapshot()
        val insertionStart = selectionBefore?.let { minOf(it.start, it.end) }
        val committed = ic.commitText(text, 1)
        val selectionAfter = ic.selectionSnapshot()

        if (committed && focusKey != null && selectionBefore != null && insertionStart != null) {
            val fallbackAfter = DictationUndoEntry.SelectionSnapshot(
                start = insertionStart + text.length,
                end = insertionStart + text.length,
            )
            val entry = DictationUndoEntry(
                focusKey = focusKey,
                insertedText = text,
                selectionBeforeInsert = selectionBefore,
                selectionAfterInsert = selectionAfter ?: fallbackAfter,
            )
            val stack = undoStacks.getOrPut(focusKey) { ArrayDeque() }
            stack.addLast(entry)
        }

        deps.clearComposing()
        deps.setKeyboardState(KeyboardState.Ready)
        activeSession = null
    }

    fun onTranscriptionError(token: SendToken, message: String) {
        val session = activeSession ?: return
        if (!shouldAcceptCallback(session, token)) return
        deps.toast(message)
        deps.setKeyboardState(if (deps.getKeyboardState().isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
    }

    private fun autoPause(showToast: Boolean, toastMessage: String? = null) {
        val state = deps.getKeyboardState()
        if (showToast && !toastMessage.isNullOrBlank()) {
            deps.toast(toastMessage)
        }

        when {
            state.isRecording -> deps.pauseRecording(if (state.isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
            state == KeyboardState.Transcribing || state == KeyboardState.SmartFixing -> {
                deps.cancelTranscription()
                deps.setKeyboardState(if (state.isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
            }
        }
    }

    private fun shouldAcceptCallback(session: Session, token: SendToken): Boolean {
        if (session.cancelled) return false
        return session.sessionId == token.sessionId
    }

    private fun isSafeToInsert(token: SendToken): Boolean {
        if (!deps.isInsertAllowed()) return false
        val expected = activeSession?.focusKeyAtSend ?: activeSession?.focusKeyAtStart
        return expected != null && expected == token.focusKey && expected == currentFocusKey
    }

    private fun InputConnection.selectionSnapshot(): DictationUndoEntry.SelectionSnapshot? {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        if (start < 0 || end < 0) return null
        return DictationUndoEntry.SelectionSnapshot(start = start, end = end)
    }

    private fun applyUndoSafely(
        ic: InputConnection,
        currentFocus: FocusKey,
        entry: DictationUndoEntry,
    ): Boolean {
        if (currentFocus != entry.focusKey) return false
        if (entry.insertedText.isEmpty()) return false

        val insertionStart = minOf(entry.selectionBeforeInsert.start, entry.selectionBeforeInsert.end)
        val insertionEnd = insertionStart + entry.insertedText.length

        ic.beginBatchEdit()
        return try {
            if (!ic.setSelection(insertionStart, insertionEnd)) return false
            val selectedText = ic.getSelectedText(0)?.toString() ?: return false
            if (selectedText != entry.insertedText) return false
            if (!ic.commitText("", 1)) return false
            ic.setSelection(entry.selectionBeforeInsert.start, entry.selectionBeforeInsert.end)
            true
        } catch (_: Exception) {
            false
        } finally {
            ic.endBatchEdit()
        }
    }
}
