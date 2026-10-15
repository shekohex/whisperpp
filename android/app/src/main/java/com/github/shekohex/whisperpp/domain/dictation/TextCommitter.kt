package com.github.shekohex.whisperpp.domain.dictation

import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.github.shekohex.whisperpp.dictation.DictationUndoEntry
import com.github.shekohex.whisperpp.dictation.FocusKey
import java.util.ArrayDeque

/**
 * The only place dictation touches [InputConnection]: composing partials, raw inserts, enhancement
 * replacement and both undo stacks. Call from the main thread.
 */
class TextCommitter(
    private val inputConnection: () -> InputConnection?,
) {
    /** Where the inserted text landed. */
    data class CapturedSegment(
        val focusKey: FocusKey,
        val start: Int,
        val endAtInsert: Int,
        val rawText: String,
    )

    sealed interface CommitResult {
        /** [segment] is null when the editor did not report a selection (no undo / enhancement). */
        data class Inserted(val segment: CapturedSegment?) : CommitResult

        data object NoField : CommitResult

        /** The editor rejected the insert (returned false or threw); the text is not in the field. */
        data class Failed(val reason: String) : CommitResult
    }

    private val undoStacks = mutableMapOf<FocusKey, ArrayDeque<DictationUndoEntry>>()

    // Field that holds our composing partial; null when nothing is composing. Tracked independently of
    // [composingStart]: an editor without an extracted-text snapshot still gets its partial replaced.
    private var composingOwner: FocusKey? = null

    // Where our composing region started, when the editor reported it (enables undo / enhancement).
    private var composingStart: Int? = null
    private var composingText: String? = null

    /** Shows [text] as the composing partial of [focus], replacing our previous partial there. */
    fun setComposing(focus: FocusKey, text: String, trackEdits: Boolean = false): Boolean {
        val ic = inputConnection() ?: return false
        val starting = composingOwner != focus
        if (starting) {
            // Never continue a composition that belongs to another field; it is no longer ours to edit.
            dropComposing()
            ic.finishComposingText()
        }
        val start = if (starting) ic.selectionSnapshot()?.let { minOf(it.start, it.end) } else composingStart
        if (trackEdits && ((starting && start == null) || (!starting && !preserveComposing(focus)))) return false
        ic.beginBatchEdit()
        val composed = try {
            ic.setComposingText(text, 1)
        } catch (_: Exception) {
            false
        } finally {
            ic.endBatchEdit()
        }
        if (composed && starting) {
            composingOwner = focus
            composingStart = start
        }
        if (composed) composingText = text
        return composed
    }

    fun preserveComposing(focus: FocusKey): Boolean {
        if (composingOwner != focus) return false
        val start = composingStart ?: return false
        val expected = composingText ?: return false
        val ic = inputConnection() ?: return false
        return try {
            val snapshot = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return false
            val text = snapshot.text ?: return false
            val end = start + expected.length
            val localStart = start - snapshot.startOffset
            val localEnd = end - snapshot.startOffset
            if (snapshot.selectionStart != end || snapshot.selectionEnd != end ||
                localStart < 0 || localEnd > text.length ||
                text.subSequence(localStart, localEnd).toString() != expected
            ) return false
            expected.isEmpty() || ic.setComposingRegion(start, end)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Removes our composing partial, but only from the field that owns it: with [currentFocus] on
     * another field the editor connection belongs to that field, so only local state is dropped.
     */
    fun clearComposing(currentFocus: FocusKey?) {
        val owner = composingOwner ?: return
        dropComposing()
        if (currentFocus != owner) return
        val ic = inputConnection() ?: return
        ic.beginBatchEdit()
        try {
            ic.setComposingText("", 1)
            ic.finishComposingText()
        } catch (_: Exception) {
            // Best effort; the editor may already be gone.
        } finally {
            ic.endBatchEdit()
        }
    }

    /** Forgets our composing partial without touching any editor. */
    fun dropComposing() {
        composingOwner = null
        composingStart = null
        composingText = null
    }

    fun textBeforeCursor(maxChars: Int): String? = inputConnection()?.getTextBeforeCursor(maxChars, 0)?.toString()

    /** The editor's non-empty selection, or null. */
    fun selectedRange(): DictationUndoEntry.SelectionSnapshot? =
        inputConnection()?.selectionSnapshot()?.takeIf { it.start != it.end }

    fun selectedText(): String? = try {
        inputConnection()?.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** Selects [range] again so the next [commit] replaces it (output mode REPLACE_SELECTION). */
    fun select(range: DictationUndoEntry.SelectionSnapshot): Boolean {
        val ic = inputConnection() ?: return false
        return try {
            ic.setSelection(minOf(range.start, range.end), maxOf(range.start, range.end))
        } catch (_: Exception) {
            false
        }
    }

    /** Inserts [text] for [focus], replacing our composing partial if any, and records undo. */
    fun commit(focus: FocusKey, text: String): CommitResult {
        val ic = inputConnection()
        if (ic == null || focus.inputType == 0) {
            dropComposing()
            return CommitResult.NoField
        }

        val ownsComposing = composingOwner == focus
        if (!ownsComposing) dropComposing()
        val selectionBefore = captureSelectionBeforeInsert(ic, ownsComposing)
        val insertionStart = selectionBefore?.let { minOf(it.start, it.end) }
        dropComposing()

        ic.beginBatchEdit()
        val failure = try {
            when {
                !ic.setComposingText(text, 1) -> "setComposingText rejected"
                !ic.finishComposingText() -> "finishComposingText rejected"
                else -> null
            }
        } catch (e: Exception) {
            e.message ?: e::class.java.simpleName
        } finally {
            ic.endBatchEdit()
        }
        if (failure != null) return CommitResult.Failed(failure)
        if (insertionStart == null) return CommitResult.Inserted(null)

        val selectionAfter = ic.selectionSnapshot()
        val before = selectionBefore ?: DictationUndoEntry.SelectionSnapshot(insertionStart, insertionStart)
        val end = insertionStart + text.length
        undoStacks.getOrPut(focus) { ArrayDeque() }.addLast(
            DictationUndoEntry(
                focusKey = focus,
                insertedText = text,
                selectionBeforeInsert = before,
                selectionAfterInsert = selectionAfter ?: DictationUndoEntry.SelectionSnapshot(end, end),
            )
        )
        return CommitResult.Inserted(CapturedSegment(focus, insertionStart, end, text))
    }

    /**
     * Presses the editor's send action for [focus] (auto-send after insert); falls back to an Enter
     * key press when the editor rejects the action and [AutoSend.EditorAction.enterFallback] allows it.
     * Returns false when nothing was sent.
     */
    fun performAutoSend(focus: FocusKey, imeOptions: Int): Boolean {
        val action = AutoSend.forEditor(imeOptions, focus.inputType) as? AutoSend.EditorAction ?: return false
        val ic = inputConnection() ?: return false
        return try {
            ic.performEditorAction(action.actionId) || (action.enterFallback && sendEnter(ic))
        } catch (_: Exception) {
            false
        }
    }

    private fun sendEnter(ic: InputConnection): Boolean {
        val down = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        val up = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        return down && up
    }

    fun isUndoAvailable(focus: FocusKey?): Boolean {
        val key = focus ?: return false
        return undoStacks[key]?.isNotEmpty() == true
    }

    /** Returns false when the last insertion can no longer be undone here (stack is dropped). */
    fun undoLastInsertion(focus: FocusKey?): Boolean {
        val key = focus ?: return false
        val stack = undoStacks[key] ?: return true
        val entry = stack.peekLast() ?: return true
        val ic = inputConnection()
        if (ic == null || !applyUndoSafely(ic, key, entry)) {
            undoStacks.remove(key)
            return false
        }
        stack.removeLast()
        if (stack.isEmpty()) undoStacks.remove(key)
        return true
    }

    /**
     * Where the insert begins, as if nothing were composing (G-03). The editor's selection sits after
     * a composing region, so our own partial (about to be replaced) is located by its tracked start
     * (unknown → no segment), and a foreign one is finished first so it cannot shift the range.
     */
    private fun captureSelectionBeforeInsert(ic: InputConnection, ownsComposing: Boolean): DictationUndoEntry.SelectionSnapshot? {
        if (ownsComposing) {
            val start = composingStart ?: return null
            return DictationUndoEntry.SelectionSnapshot(start, start)
        }
        ic.finishComposingText()
        return ic.selectionSnapshot()
    }

    private fun applyUndoSafely(ic: InputConnection, focus: FocusKey, entry: DictationUndoEntry): Boolean {
        if (focus != entry.focusKey || entry.insertedText.isEmpty()) return false
        val start = minOf(entry.selectionBeforeInsert.start, entry.selectionBeforeInsert.end)
        val end = start + entry.insertedText.length
        ic.beginBatchEdit()
        return try {
            finishForeignComposing(ic)
            if (!ic.setSelection(start, end)) return false
            if (ic.getSelectedText(0)?.toString() != entry.insertedText) return false
            if (!ic.commitText("", 1)) return false
            ic.setSelection(entry.selectionBeforeInsert.start, entry.selectionBeforeInsert.end)
            true
        } catch (_: Exception) {
            false
        } finally {
            ic.endBatchEdit()
        }
    }

    // Our own partial is never frozen into the text; only another composer's region is finished.
    private fun finishForeignComposing(ic: InputConnection) {
        if (composingOwner == null) ic.finishComposingText()
    }

    private fun InputConnection.selectionSnapshot(): DictationUndoEntry.SelectionSnapshot? {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        if (start < 0 || end < 0) return null
        return DictationUndoEntry.SelectionSnapshot(start = start, end = end)
    }
}
