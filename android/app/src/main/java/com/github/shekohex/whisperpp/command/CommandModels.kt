package com.github.shekohex.whisperpp.command

import com.github.shekohex.whisperpp.dictation.FocusKey

data class SelectionSnapshot(
    val start: Int,
    val end: Int,
)

enum class CommandStage {
    WAITING,
    CLIPBOARD_CONFIRM,
    LISTENING,
    PROCESSING,
    DONE,
    ERROR,
}

sealed class ResolvedSelection {
    data class Selected(
        val text: String,
        val snapshot: SelectionSnapshot,
    ) : ResolvedSelection()

    data class NeedsClipboard(
        val snapshot: SelectionSnapshot,
    ) : ResolvedSelection()

    data class None(
        val reason: Reason,
    ) : ResolvedSelection()

    enum class Reason {
        NO_SELECTION,
        TYPE_NULL,
        UNKNOWN,
    }
}

data class CommandUndoEntry(
    val focusKey: FocusKey,
    val snapshot: SelectionSnapshot,
    val originalText: String,
    val appliedText: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
)
