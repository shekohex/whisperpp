package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.command.CommandController
import com.github.shekohex.whisperpp.command.CommandUndoEntry
import com.github.shekohex.whisperpp.command.SelectionSnapshot
import com.github.shekohex.whisperpp.dictation.FocusKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The short-lived undo of the last command-mode replacement, mirrored into [CommandModeState] while it lasts. */
class CommandUndoTracker(
    private val scope: CoroutineScope,
    private val state: CommandModeState,
) {
    private val commandController = CommandController()
    private var tickJob: Job? = null

    fun startRun(focusKey: FocusKey?) = commandController.startRun(focusKey)

    fun recordApplied(focusKey: FocusKey, snapshot: SelectionSnapshot, originalText: String, appliedText: String) {
        commandController.recordUndoAfterApply(
            focusKey = focusKey,
            snapshot = snapshot,
            originalText = originalText,
            appliedText = appliedText,
            nowMs = System.currentTimeMillis(),
        )
    }

    /** Takes the entry if it is still valid; the caller restores the text. */
    fun consume(): CommandUndoEntry? = commandController.consumeUndoEntry(System.currentTimeMillis())

    fun refresh() {
        val now = System.currentTimeMillis()
        val entry = commandController.getUndoEntry(now)
        if (entry == null) {
            state.setUndo(available = false, remainingMs = 0L)
            tickJob?.cancel()
            tickJob = null
            return
        }
        state.setUndo(available = true, remainingMs = (entry.expiresAtMs - now).coerceAtLeast(0L))

        if (tickJob == null) {
            tickJob = scope.launch {
                while (true) {
                    delay(TICK_MS)
                    val nowTick = System.currentTimeMillis()
                    val tickEntry = commandController.getUndoEntry(nowTick)
                    if (tickEntry == null) {
                        state.setUndo(available = false, remainingMs = 0L)
                        tickJob = null
                        return@launch
                    }
                    state.setUndo(available = true, remainingMs = (tickEntry.expiresAtMs - nowTick).coerceAtLeast(0L))
                }
            }
        }
    }

    fun release() {
        tickJob?.cancel()
        tickJob = null
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
