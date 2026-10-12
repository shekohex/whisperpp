package com.github.shekohex.whisperpp.ime

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.dictation.TextCommitter

/** What the keyboard offers to undo for the focused field: the last insertion and the last enhancement. */
class KeyboardUndoState(
    private val textCommitter: TextCommitter,
    private val currentFocus: () -> FocusKey?,
) {
    private val _undoAvailable = mutableStateOf(false)
    private val _quickActionVisible = mutableStateOf(false)
    private val _enhancementUndoAvailable = mutableStateOf(false)

    val undoAvailable: State<Boolean> = _undoAvailable
    val quickActionVisible: State<Boolean> = _quickActionVisible
    val enhancementUndoAvailable: State<Boolean> = _enhancementUndoAvailable

    fun refresh() {
        val available = textCommitter.isUndoAvailable(currentFocus())
        _undoAvailable.value = available
        if (!available) {
            _quickActionVisible.value = false
        }
    }

    fun showQuickActionIfAvailable() {
        refresh()
        if (_undoAvailable.value) {
            _quickActionVisible.value = true
        }
    }

    fun clearQuickAction() {
        _quickActionVisible.value = false
    }

    fun refreshEnhancement() {
        _enhancementUndoAvailable.value = textCommitter.isEnhancementUndoAvailable(currentFocus())
    }

    fun clearEnhancement() {
        _enhancementUndoAvailable.value = false
    }
}
