package com.github.shekohex.whisperpp.ime

import android.view.inputmethod.InputConnection
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnRevision
import com.github.shekohex.whisperpp.domain.dictation.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * IME side of Auto-Learn (§4.11 step 1). After a dictation is inserted it keeps a baseline of the
 * field; when the user leaves the field, hides the keyboard or [DEADLINE_MS] passes, an edited
 * committed region becomes an [AutoLearnRevision] for [onRevision]. Leaving the field unchanged
 * parks the capture until the deadline so a fix made after a keyboard switch is still seen.
 * Auto-send, secure fields, blocked apps and a disabled feature skip the capture. Main thread only.
 */
class AutoLearnObserver(
    private val scope: CoroutineScope,
    private var inputConnection: () -> InputConnection? = { null },
    private var currentFocus: () -> FocusKey? = { null },
    private var gates: (event: SessionEvent.Completed) -> AutoLearnCaptureStateMachine.Gates = { DISABLED_GATES },
    private val onRevision: (AutoLearnRevision) -> Unit,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val machine = AutoLearnCaptureStateMachine(clock)
    private var capturedEvent: SessionEvent.Completed? = null
    private var deadlineJob: Job? = null
    private var refreshJob: Job? = null

    val isCapturing: Boolean
        get() = machine.isActive

    fun attach(
        inputConnection: () -> InputConnection?,
        currentFocus: () -> FocusKey?,
        gates: (SessionEvent.Completed) -> AutoLearnCaptureStateMachine.Gates,
    ) {
        this.inputConnection = inputConnection
        this.currentFocus = currentFocus
        this.gates = gates
    }

    fun detach() {
        onFieldLeft()
        refreshJob?.cancel()
        refreshJob = null
        inputConnection = { null }
        currentFocus = { null }
        gates = { DISABLED_GATES }
    }

    /** A dictation landed: the previous capture ends with its last known text, a new one starts. */
    fun onCompleted(event: SessionEvent.Completed) {
        if (!event.inserted) return
        if (machine.isActive) end(snapshot = null)
        if (currentFocus() != event.focus) return
        val currentGates = gates(event)
        if (!currentGates.enabled || currentGates.secureField || currentGates.blockedApp || currentGates.autoSend) return
        val snapshot = readLive()
        val started = machine.begin(event.focus, event.finalText, snapshot, currentGates)
        if (started == AutoLearnCaptureStateMachine.BeginResult.Started) {
            capturedEvent = event
            machine.cache(event.focus, snapshot)
            deadlineJob = scope.launch {
                delay(DEADLINE_MS)
                end(snapshot = readIfFocused())
            }
        }
    }

    /** Auto-send fired for [focus]: the text left the field, nothing to learn from. */
    fun onAutoSend(focus: FocusKey) {
        if (machine.isTracking(focus)) cancel()
    }

    /** `onUpdateSelection` / `onUpdateExtractedText`: mark dirty and refresh the cache once edits settle. */
    fun onFieldUpdated() {
        val focus = currentFocus()
        if (!machine.isTracking(focus)) return
        machine.markDirty(focus)
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(REFRESH_DEBOUNCE_MS)
            machine.cache(focus, readIfFocused())
        }
    }

    /** `onStartInput`: back in the same field after a keyboard switch, or a different field. */
    fun onFocusChanged(focus: FocusKey?) {
        if (!machine.isActive) return
        if (machine.parked && machine.isSameField(focus) && focus != null) {
            machine.resume(focus)
            val snapshot = readIfFocused()
            machine.cache(focus, snapshot)
            when (machine.evaluate(snapshot, focus.packageName)) {
                is AutoLearnCaptureStateMachine.Evaluation.Changed -> end(snapshot)
                AutoLearnCaptureStateMachine.Evaluation.Unrelated -> cancel()
                AutoLearnCaptureStateMachine.Evaluation.Unchanged -> Unit
            }
            return
        }
        if (machine.isTracking(focus)) return
        // The connection already belongs to the new field: only the cached text is usable.
        end(snapshot = null)
    }

    /** `onFinishInput` / `onWindowHidden`: the field is still readable. */
    fun onFieldLeft() {
        if (!machine.isActive) return
        val snapshot = readIfFocused()
        val packageName = machine.focus?.packageName
        when (val evaluation = machine.evaluate(snapshot, packageName)) {
            is AutoLearnCaptureStateMachine.Evaluation.Changed -> {
                cancel()
                onRevision(evaluation.revision)
            }
            AutoLearnCaptureStateMachine.Evaluation.Unchanged -> {
                machine.cache(machine.focus, snapshot)
                machine.park()
            }
            AutoLearnCaptureStateMachine.Evaluation.Unrelated -> cancel()
        }
    }

    fun cancel() {
        deadlineJob?.cancel()
        refreshJob?.cancel()
        deadlineJob = null
        refreshJob = null
        capturedEvent = null
        machine.cancel()
    }

    private fun end(snapshot: FieldTextReader.Snapshot?) {
        val packageName = machine.focus?.packageName
        val revision = if (captureAllowed()) machine.finish(snapshot, packageName) else null
        cancel()
        revision?.let(onRevision)
    }

    private fun readLive(): FieldTextReader.Snapshot? = FieldTextReader.read(inputConnection())

    private fun captureAllowed(): Boolean {
        val event = capturedEvent ?: return false
        val current = gates(event)
        return current.enabled && !current.secureField && !current.blockedApp && !current.autoSend
    }

    private fun readIfFocused(): FieldTextReader.Snapshot? {
        if (!captureAllowed()) {
            cancel()
            return null
        }
        return if (machine.isTracking(currentFocus())) readLive() else null
    }

    companion object {
        const val DEADLINE_MS = 90_000L
        const val REFRESH_DEBOUNCE_MS = 400L
        private val DISABLED_GATES = AutoLearnCaptureStateMachine.Gates(
            enabled = false,
            secureField = false,
            blockedApp = false,
            autoSend = false,
        )
    }
}
