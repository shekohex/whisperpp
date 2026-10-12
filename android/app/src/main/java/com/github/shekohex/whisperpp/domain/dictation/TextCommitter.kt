package com.github.shekohex.whisperpp.domain.dictation

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.github.shekohex.whisperpp.dictation.DictationUndoEntry
import com.github.shekohex.whisperpp.dictation.EnhancementUndoEntry
import com.github.shekohex.whisperpp.dictation.FocusKey
import java.util.ArrayDeque

/**
 * The only place dictation touches [InputConnection]: composing partials, raw inserts, enhancement
 * replacement and both undo stacks. Call from the main thread.
 */
class TextCommitter(
    private val inputConnection: () -> InputConnection?,
) {
    /** Where the raw text landed, so enhancement can replace exactly that range later. */
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
    private var enhancementUndoEntry: EnhancementUndoEntry? = null

    // Field that holds our composing partial; null when nothing is composing. Tracked independently of
    // [composingStart]: an editor without an extracted-text snapshot still gets its partial replaced.
    private var composingOwner: FocusKey? = null

    // Where our composing region started, when the editor reported it (enables undo / enhancement).
    private var composingStart: Int? = null

    /** Shows [text] as the composing partial of [focus], replacing our previous partial there. */
    fun setComposing(focus: FocusKey, text: String): Boolean {
        val ic = inputConnection() ?: return false
        val starting = composingOwner != focus
        if (starting) {
            // Never continue a composition that belongs to another field; it is no longer ours to edit.
            dropComposing()
            ic.finishComposingText()
        }
        val start = if (starting) ic.selectionSnapshot()?.let { minOf(it.start, it.end) } else composingStart
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
        return composed
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
    }

    fun textBeforeCursor(maxChars: Int): String? = inputConnection()?.getTextBeforeCursor(maxChars, 0)?.toString()

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
        val insertionStart = composingStart ?: selectionBefore?.let { minOf(it.start, it.end) }
        dropComposing()
        enhancementUndoEntry = null

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

    /** Replaces a captured raw segment with [enhancedText] when [currentFocus] still owns it. */
    fun replaceSegment(segment: CapturedSegment, enhancedText: String, currentFocus: FocusKey?): Boolean {
        if (enhancedText.isBlank()) return false
        if (currentFocus != segment.focusKey) return false
        val ic = inputConnection() ?: return false

        enhancementUndoEntry = null
        val replacementEnd = segment.start + enhancedText.length
        if (!ic.replaceRange(segment.start, segment.endAtInsert, enhancedText)) return false

        updateLatestUndoForReplacement(segment, enhancedText)
        enhancementUndoEntry = EnhancementUndoEntry(
            focusKey = segment.focusKey,
            start = segment.start,
            endAtReplace = replacementEnd,
            rawText = segment.rawText,
        )
        return true
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

    fun isEnhancementUndoAvailable(focus: FocusKey?): Boolean {
        val entry = enhancementUndoEntry ?: return false
        return focus != null && entry.focusKey == focus
    }

    fun applyEnhancementUndo(focus: FocusKey?): Boolean {
        val entry = enhancementUndoEntry ?: return false
        if (focus == null || entry.focusKey != focus) return false
        val ic = inputConnection() ?: return false
        if (!ic.replaceRange(entry.start, entry.endAtReplace, entry.rawText)) return false
        enhancementUndoEntry = null
        return true
    }

    // Our composing partial is about to be replaced, so only a foreign composing region is finished.
    private fun captureSelectionBeforeInsert(ic: InputConnection, ownsComposing: Boolean): DictationUndoEntry.SelectionSnapshot? {
        if (!ownsComposing) ic.finishComposingText()
        return ic.selectionSnapshot()
    }

    private fun InputConnection.replaceRange(start: Int, end: Int, text: String): Boolean {
        val cursor = start + text.length
        beginBatchEdit()
        return try {
            if (setSelection(start, end) && commitText(text, 1)) {
                setSelection(cursor, cursor)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            endBatchEdit()
        }
    }

    private fun updateLatestUndoForReplacement(segment: CapturedSegment, enhancedText: String) {
        val stack = undoStacks[segment.focusKey] ?: return
        val last = stack.peekLast() ?: return
        val insertionStart = minOf(last.selectionBeforeInsert.start, last.selectionBeforeInsert.end)
        if (insertionStart != segment.start || last.insertedText != segment.rawText) return
        val end = insertionStart + enhancedText.length
        stack.removeLast()
        stack.addLast(
            last.copy(
                insertedText = enhancedText,
                selectionAfterInsert = DictationUndoEntry.SelectionSnapshot(end, end),
            )
        )
    }

    private fun applyUndoSafely(ic: InputConnection, focus: FocusKey, entry: DictationUndoEntry): Boolean {
        if (focus != entry.focusKey || entry.insertedText.isEmpty()) return false
        val start = minOf(entry.selectionBeforeInsert.start, entry.selectionBeforeInsert.end)
        val end = start + entry.insertedText.length
        ic.beginBatchEdit()
        return try {
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

    private fun InputConnection.selectionSnapshot(): DictationUndoEntry.SelectionSnapshot? {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        if (start < 0 || end < 0) return null
        return DictationUndoEntry.SelectionSnapshot(start = start, end = end)
    }
}
