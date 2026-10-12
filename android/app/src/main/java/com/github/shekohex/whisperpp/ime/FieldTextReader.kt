package com.github.shekohex.whisperpp.ime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * Reads the focused field's text around the cursor for Auto-Learn (§4.11 step 1): the extracted
 * text when the editor provides it, else a window of [WINDOW_CHARS] before and after the cursor.
 */
object FieldTextReader {
    const val WINDOW_CHARS = 2_000
    private const val MAX_EXTRACTED_CHARS = 20_000

    /** Field text (or a window of it) with the cursor (selection end) as an index into [text]. */
    data class Snapshot(val text: String, val cursor: Int)

    fun read(ic: InputConnection?): Snapshot? {
        if (ic == null) return null
        return readExtracted(ic) ?: readWindow(ic)
    }

    private fun readExtracted(ic: InputConnection): Snapshot? = try {
        val request = ExtractedTextRequest().apply { hintMaxChars = MAX_EXTRACTED_CHARS }
        val extracted = ic.getExtractedText(request, 0)
        val text = extracted?.text?.toString()
        val cursor = extracted?.selectionEnd ?: -1
        if (text == null || cursor < 0 || cursor > text.length) null else Snapshot(text, cursor)
    } catch (_: Exception) {
        null
    }

    private fun readWindow(ic: InputConnection): Snapshot? = try {
        val before = ic.getTextBeforeCursor(WINDOW_CHARS, 0)?.toString()
        val after = ic.getTextAfterCursor(WINDOW_CHARS, 0)?.toString()
        if (before == null && after == null) {
            null
        } else {
            val head = before.orEmpty()
            Snapshot(head + after.orEmpty(), head.length)
        }
    } catch (_: Exception) {
        null
    }
}
