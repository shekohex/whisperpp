package com.github.shekohex.whisperpp.command

import com.github.shekohex.whisperpp.dictation.FocusKey

class CommandController {

    private var currentRunFocusKey: FocusKey? = null
    private var undoEntry: CommandUndoEntry? = null

    fun startRun(focusKey: FocusKey?) {
        currentRunFocusKey = focusKey
        undoEntry = null
    }

    fun recordUndoAfterApply(
        focusKey: FocusKey,
        snapshot: SelectionSnapshot,
        originalText: String,
        appliedText: String,
        nowMs: Long,
    ) {
        if (currentRunFocusKey != focusKey) {
            return
        }
        val expiresAtMs = nowMs + UNDO_TTL_MS
        undoEntry = CommandUndoEntry(
            focusKey = focusKey,
            snapshot = snapshot,
            originalText = originalText,
            appliedText = appliedText,
            createdAtMs = nowMs,
            expiresAtMs = expiresAtMs,
        )
    }

    fun shouldShowUndo(nowMs: Long): Boolean {
        return getUndoEntry(nowMs) != null
    }

    fun getUndoEntry(nowMs: Long): CommandUndoEntry? {
        val entry = undoEntry ?: return null
        if (nowMs >= entry.expiresAtMs) {
            undoEntry = null
            return null
        }
        return entry
    }

    private companion object {
        private const val UNDO_TTL_MS = 30_000L
    }
}
