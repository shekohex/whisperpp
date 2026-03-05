package com.github.shekohex.whisperpp.dictation

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo

class FakeInputConnection : InputConnection {
    private val buffer = StringBuilder()
    private var selectionStart: Int = 0
    private var selectionEnd: Int = 0

    fun setText(text: String) {
        buffer.clear()
        buffer.append(text)
        val end = buffer.length
        selectionStart = end
        selectionEnd = end
    }

    fun getText(): String = buffer.toString()

    fun getSelection(): Pair<Int, Int> = selectionStart to selectionEnd

    fun replaceRange(start: Int, end: Int, replacement: String) {
        val s = start.coerceIn(0, buffer.length)
        val e = end.coerceIn(0, buffer.length)
        val a = minOf(s, e)
        val b = maxOf(s, e)
        buffer.replace(a, b, replacement)
        val cursor = a + replacement.length
        selectionStart = cursor
        selectionEnd = cursor
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
        val extracted = ExtractedText()
        extracted.text = buffer.toString()
        extracted.startOffset = 0
        extracted.partialStartOffset = -1
        extracted.partialEndOffset = -1
        extracted.selectionStart = selectionStart
        extracted.selectionEnd = selectionEnd
        return extracted
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        if (start < 0 || end < 0) return false
        if (start > buffer.length || end > buffer.length) return false
        selectionStart = start
        selectionEnd = end
        return true
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        val a = minOf(selectionStart, selectionEnd)
        val b = maxOf(selectionStart, selectionEnd)
        if (a < 0 || b < 0) return null
        if (a > buffer.length || b > buffer.length) return null
        if (a == b) return null
        return buffer.substring(a, b)
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val replacement = text?.toString().orEmpty()
        val a = minOf(selectionStart, selectionEnd)
        val b = maxOf(selectionStart, selectionEnd)
        if (a < 0 || b < 0) return false
        if (a > buffer.length || b > buffer.length) return false
        buffer.replace(a, b, replacement)
        val cursor = a + replacement.length
        selectionStart = cursor
        selectionEnd = cursor
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        return commitText(text, newCursorPosition)
    }

    override fun finishComposingText(): Boolean = true

    override fun beginBatchEdit(): Boolean = true

    override fun endBatchEdit(): Boolean = true

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        val cursor = minOf(selectionStart, selectionEnd).coerceIn(0, buffer.length)
        val start = (cursor - n).coerceAtLeast(0)
        return buffer.substring(start, cursor)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        val cursor = maxOf(selectionStart, selectionEnd).coerceIn(0, buffer.length)
        val end = (cursor + n).coerceAtMost(buffer.length)
        return buffer.substring(cursor, end)
    }

    override fun getCursorCapsMode(reqModes: Int): Int = 0

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val cursor = minOf(selectionStart, selectionEnd).coerceIn(0, buffer.length)
        val start = (cursor - beforeLength).coerceAtLeast(0)
        val end = (cursor + afterLength).coerceAtMost(buffer.length)
        buffer.delete(start, end)
        selectionStart = start
        selectionEnd = start
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        return deleteSurroundingText(beforeLength, afterLength)
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        return setSelection(start, end)
    }

    override fun commitCompletion(text: CompletionInfo?): Boolean = true

    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true

    override fun performEditorAction(editorAction: Int): Boolean = false

    override fun performContextMenuAction(id: Int): Boolean = false

    override fun sendKeyEvent(event: KeyEvent?): Boolean = false

    override fun clearMetaKeyStates(states: Int): Boolean = true

    override fun reportFullscreenMode(enabled: Boolean): Boolean = false

    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false

    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false

    override fun requestCursorUpdates(cursorUpdateMode: Int, cursorUpdateFilter: Int): Boolean = false

    override fun getHandler(): Handler? = null

    override fun closeConnection() {
    }

    override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false

    override fun setImeConsumesInput(imeConsumesInput: Boolean): Boolean = false
}
