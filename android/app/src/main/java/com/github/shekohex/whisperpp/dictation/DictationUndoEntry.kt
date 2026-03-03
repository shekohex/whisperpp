package com.github.shekohex.whisperpp.dictation

data class DictationUndoEntry(
    val focusKey: FocusKey,
    val insertedText: String,
    val selectionBeforeInsert: SelectionSnapshot,
    val selectionAfterInsert: SelectionSnapshot,
) {
    data class SelectionSnapshot(
        val start: Int,
        val end: Int,
    )
}
