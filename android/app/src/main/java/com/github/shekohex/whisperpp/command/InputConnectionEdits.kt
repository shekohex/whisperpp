package com.github.shekohex.whisperpp.command

import android.view.inputmethod.InputConnection

/** Replaces [start, end) with [text] in one batch edit and selects the result. False on any failure. */
fun InputConnection.replaceAndSelect(start: Int, end: Int, text: String): Boolean = runCatching {
    beginBatchEdit()
    try {
        if (!setSelection(start, end)) return@runCatching false
        if (!commitText(text, 1)) return@runCatching false
        setSelection(start, start + text.length)
        true
    } finally {
        endBatchEdit()
    }
}.getOrDefault(false)
