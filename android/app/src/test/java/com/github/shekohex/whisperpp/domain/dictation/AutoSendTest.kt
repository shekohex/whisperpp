package com.github.shekohex.whisperpp.domain.dictation

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSendTest {

    private val singleLine = InputType.TYPE_CLASS_TEXT
    private val multiLine = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

    @Test
    fun sendLikeActions_areUsedAsIs_evenInMultilineFields() {
        assertEquals(AutoSend.EditorAction(EditorInfo.IME_ACTION_SEND), AutoSend.forEditor(EditorInfo.IME_ACTION_SEND, multiLine))
        assertEquals(AutoSend.EditorAction(EditorInfo.IME_ACTION_GO), AutoSend.forEditor(EditorInfo.IME_ACTION_GO, singleLine))
        assertEquals(
            AutoSend.EditorAction(EditorInfo.IME_ACTION_DONE),
            AutoSend.forEditor(EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION, singleLine),
        )
    }

    @Test
    fun actionNone_orMultilineWithoutAction_isSkipped() {
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_NONE, singleLine))
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_UNSPECIFIED, multiLine))
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_SEND, InputType.TYPE_NULL))
    }

    @Test
    fun singleLineWithoutSendAction_requestsSend() {
        assertEquals(AutoSend.EditorAction(EditorInfo.IME_ACTION_SEND), AutoSend.forEditor(EditorInfo.IME_ACTION_UNSPECIFIED, singleLine))
        assertEquals(AutoSend.EditorAction(EditorInfo.IME_ACTION_SEND), AutoSend.forEditor(EditorInfo.IME_ACTION_SEARCH, singleLine))
    }
}
