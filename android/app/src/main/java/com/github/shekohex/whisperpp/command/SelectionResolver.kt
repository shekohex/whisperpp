package com.github.shekohex.whisperpp.command

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

class SelectionResolver {

    fun resolve(editorInfo: EditorInfo?, inputConnection: InputConnection?): ResolvedSelection {
        if (editorInfo?.inputType == 0) {
            return ResolvedSelection.None(ResolvedSelection.Reason.TYPE_NULL)
        }
        val ic = inputConnection ?: return ResolvedSelection.None(ResolvedSelection.Reason.NO_SELECTION)

        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            ?: return ResolvedSelection.None(ResolvedSelection.Reason.NO_SELECTION)

        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        if (start < 0 || end < 0 || start == end) {
            return ResolvedSelection.None(ResolvedSelection.Reason.NO_SELECTION)
        }
        val snapshot = SelectionSnapshot(start = start, end = end)

        val selectedText = ic.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
        return if (selectedText == null) {
            ResolvedSelection.NeedsClipboard(snapshot)
        } else {
            ResolvedSelection.Selected(text = selectedText, snapshot = snapshot)
        }
    }
}
