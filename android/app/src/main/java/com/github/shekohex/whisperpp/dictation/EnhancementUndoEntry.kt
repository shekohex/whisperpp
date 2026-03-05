package com.github.shekohex.whisperpp.dictation

data class EnhancementUndoEntry(
    val focusKey: FocusKey,
    val start: Int,
    val endAtReplace: Int,
    val rawText: String,
)
