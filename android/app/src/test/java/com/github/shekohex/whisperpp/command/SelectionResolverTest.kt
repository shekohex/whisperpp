package com.github.shekohex.whisperpp.command

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.github.shekohex.whisperpp.dictation.FakeInputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionResolverTest {
    private val resolver = SelectionResolver()

    @Test
    fun resolve_noSelection_returnsNone() {
        val ic = FakeInputConnection().apply {
            setText("hello")
            setSelection(2, 2)
        }
        val editorInfo = EditorInfo().apply { inputType = 1 }

        val resolved = resolver.resolve(editorInfo, ic)

        assertEquals(ResolvedSelection.None(ResolvedSelection.Reason.NO_SELECTION), resolved)
    }

    @Test
    fun resolve_selectionPresent_returnsSelectedWithSnapshot() {
        val ic = FakeInputConnection().apply {
            setText("hello world")
            setSelection(0, 5)
        }
        val editorInfo = EditorInfo().apply { inputType = 1 }

        val resolved = resolver.resolve(editorInfo, ic)

        assertTrue(resolved is ResolvedSelection.Selected)
        val selected = resolved as ResolvedSelection.Selected
        assertEquals("hello", selected.text)
        assertEquals(SelectionSnapshot(start = 0, end = 5), selected.snapshot)
    }

    @Test
    fun resolve_selectionIndicesPresentButSelectedTextNull_returnsNeedsClipboardWithSnapshot() {
        val base = FakeInputConnection().apply {
            setText("hello world")
            setSelection(0, 5)
        }
        val ic = SelectedTextNullInputConnection(base)
        val editorInfo = EditorInfo().apply { inputType = 1 }

        val resolved = resolver.resolve(editorInfo, ic)

        assertEquals(
            ResolvedSelection.NeedsClipboard(SelectionSnapshot(start = 0, end = 5)),
            resolved,
        )
    }

    @Test
    fun resolve_typeNull_returnsNoneTypeNull() {
        val ic = FakeInputConnection().apply {
            setText("hello world")
            setSelection(0, 5)
        }
        val editorInfo = EditorInfo().apply { inputType = 0 }

        val resolved = resolver.resolve(editorInfo, ic)

        assertEquals(ResolvedSelection.None(ResolvedSelection.Reason.TYPE_NULL), resolved)
    }

    private class SelectedTextNullInputConnection(
        private val delegate: InputConnection,
    ) : InputConnection by delegate {
        override fun getSelectedText(flags: Int): CharSequence? = null
    }
}
