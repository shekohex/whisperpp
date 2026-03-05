package com.github.shekohex.whisperpp.dictation

import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.os.SystemClock
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isLocked
import com.github.shekohex.whisperpp.keyboard.isPaused
import com.github.shekohex.whisperpp.keyboard.isRecording
import java.util.ArrayDeque

class DictationController(
    private val deps: Deps,
) {
    private val partialThrottleMs = 150L

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

    data class CapturedDictationSegment(
        val focusKey: FocusKey,
        val start: Int,
        val endAtInsert: Int,
        val rawText: String,
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

    private var lastPartialAppliedAtMs: Long = 0L
    private var lastPartialApplied: String? = null
    private var pendingPartial: String? = null

    private val undoStacks = mutableMapOf<FocusKey, ArrayDeque<DictationUndoEntry>>()

    private var enhancementUndoEntry: EnhancementUndoEntry? = null

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
        resetPartialTracking()
        enhancementUndoEntry = null
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
        resetPartialTracking()
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
        if (text.isBlank()) return

        pendingPartial = text

        val now = SystemClock.elapsedRealtime()
        if (now - lastPartialAppliedAtMs < partialThrottleMs) return

        val candidate = pendingPartial ?: return
        if (candidate == lastPartialApplied) return

        val ic = deps.getInputConnection() ?: return
        ic.beginBatchEdit()
        try {
            ic.setComposingText(candidate, 1)
        } finally {
            ic.endBatchEdit()
        }

        lastPartialAppliedAtMs = now
        lastPartialApplied = candidate
        pendingPartial = null
    }

    fun onFinalTranscript(token: SendToken, text: String) {
        insertRawAndCaptureSegment(token, text)
    }

    fun insertRawAndCaptureSegment(
        token: SendToken,
        rawText: String,
    ): CapturedDictationSegment? {
        val session = activeSession ?: return null
        if (!shouldAcceptCallback(session, token)) return null
        session.pendingTranscript = rawText

        resetPartialTracking()

        if (!isSafeToInsert(token)) {
            deps.copyToClipboard(rawText)
            deps.toast("Focus changed; transcript copied")
            deps.setKeyboardState(if (deps.getKeyboardState().isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
            return null
        }

        if (token.focusKey?.inputType == 0) {
            deps.copyToClipboard(rawText)
            deps.toast("No field focused; copied to clipboard")
            deps.setKeyboardState(if (deps.getKeyboardState().isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
            return null
        }

        val ic = deps.getInputConnection()
        if (ic == null) {
            deps.copyToClipboard(rawText)
            deps.toast("No field focused; copied to clipboard")
            deps.setKeyboardState(if (deps.getKeyboardState().isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
            return null
        }

        val focusKey = token.focusKey ?: currentFocusKey
        val selectionBefore = ic.selectionSnapshot()
        val insertionStart = selectionBefore?.let { minOf(it.start, it.end) }

        enhancementUndoEntry = null

        ic.beginBatchEdit()
        val committed = try {
            val composed = ic.setComposingText(rawText, 1)
            val finished = ic.finishComposingText()
            composed && finished
        } catch (_: Exception) {
            false
        } finally {
            ic.endBatchEdit()
        }

        val selectionAfter = ic.selectionSnapshot()

        val capturedSegment = if (committed && focusKey != null && insertionStart != null) {
            CapturedDictationSegment(
                focusKey = focusKey,
                start = insertionStart,
                endAtInsert = insertionStart + rawText.length,
                rawText = rawText,
            )
        } else {
            null
        }

        if (committed && focusKey != null && selectionBefore != null && insertionStart != null) {
            val fallbackAfter = DictationUndoEntry.SelectionSnapshot(
                start = insertionStart + rawText.length,
                end = insertionStart + rawText.length,
            )
            val entry = DictationUndoEntry(
                focusKey = focusKey,
                insertedText = rawText,
                selectionBeforeInsert = selectionBefore,
                selectionAfterInsert = selectionAfter ?: fallbackAfter,
            )
            val stack = undoStacks.getOrPut(focusKey) { ArrayDeque() }
            stack.addLast(entry)
        }

        deps.setKeyboardState(KeyboardState.Ready)
        activeSession = null

        return capturedSegment
    }

    fun replaceCapturedSegment(
        captured: CapturedDictationSegment,
        enhancedText: String,
    ): Boolean {
        if (enhancedText.isBlank()) return false
        if (currentFocusKey != captured.focusKey) return false
        val ic = deps.getInputConnection() ?: return false

        enhancementUndoEntry = null

        val replacementEnd = captured.start + enhancedText.length

        ic.beginBatchEdit()
        val replaced = try {
            if (!ic.setSelection(captured.start, captured.endAtInsert)) {
                false
            } else if (!ic.commitText(enhancedText, 1)) {
                false
            } else {
                ic.setSelection(replacementEnd, replacementEnd)
                true
            }
        } catch (_: Exception) {
            false
        } finally {
            ic.endBatchEdit()
        }

        if (!replaced) return false

        updateLatestDictationUndoForReplacement(captured, enhancedText)
        enhancementUndoEntry = EnhancementUndoEntry(
            focusKey = captured.focusKey,
            start = captured.start,
            endAtReplace = replacementEnd,
            rawText = captured.rawText,
        )
        return true
    }

    fun isEnhancementUndoAvailable(): Boolean {
        val current = currentFocusKey ?: return false
        val entry = enhancementUndoEntry ?: return false
        return entry.focusKey == current
    }

    fun applyEnhancementUndo(): Boolean {
        val current = currentFocusKey ?: return false
        val entry = enhancementUndoEntry ?: return false
        if (entry.focusKey != current) return false
        val ic = deps.getInputConnection() ?: return false

        val end = entry.start + entry.rawText.length

        ic.beginBatchEdit()
        val applied = try {
            if (!ic.setSelection(entry.start, entry.endAtReplace)) {
                false
            } else if (!ic.commitText(entry.rawText, 1)) {
                false
            } else {
                ic.setSelection(end, end)
                true
            }
        } catch (_: Exception) {
            false
        } finally {
            ic.endBatchEdit()
        }

        if (applied) {
            enhancementUndoEntry = null
        }

        return applied
    }

    private fun updateLatestDictationUndoForReplacement(
        captured: CapturedDictationSegment,
        enhancedText: String,
    ) {
        val stack = undoStacks[captured.focusKey] ?: return
        val last = stack.peekLast() ?: return

        val insertionStart = minOf(last.selectionBeforeInsert.start, last.selectionBeforeInsert.end)
        if (insertionStart != captured.start) return
        if (last.insertedText != captured.rawText) return

        val updatedAfter = DictationUndoEntry.SelectionSnapshot(
            start = insertionStart + enhancedText.length,
            end = insertionStart + enhancedText.length,
        )
        stack.removeLast()
        stack.addLast(
            last.copy(
                insertedText = enhancedText,
                selectionAfterInsert = updatedAfter,
            )
        )
    }

    fun onTranscriptionError(token: SendToken, message: String) {
        val session = activeSession ?: return
        if (!shouldAcceptCallback(session, token)) return
        deps.toast(message)
        deps.setKeyboardState(if (deps.getKeyboardState().isLocked) KeyboardState.PausedLocked else KeyboardState.Paused)
    }

    private fun resetPartialTracking() {
        lastPartialAppliedAtMs = 0L
        lastPartialApplied = null
        pendingPartial = null
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
