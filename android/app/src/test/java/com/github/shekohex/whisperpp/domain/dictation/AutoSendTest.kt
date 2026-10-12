package com.github.shekohex.whisperpp.domain.dictation

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSendTest {

    private val singleLine = InputType.TYPE_CLASS_TEXT
    private val multiLine = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

    @Test
    fun sendLikeActions_areUsedAsIs_withEnterFallbackOnlyForSingleLine() {
        assertEquals(
            AutoSend.EditorAction(EditorInfo.IME_ACTION_SEND, enterFallback = false),
            AutoSend.forEditor(EditorInfo.IME_ACTION_SEND, multiLine),
        )
        assertEquals(
            AutoSend.EditorAction(EditorInfo.IME_ACTION_GO, enterFallback = true),
            AutoSend.forEditor(EditorInfo.IME_ACTION_GO, singleLine),
        )
        assertEquals(
            AutoSend.EditorAction(EditorInfo.IME_ACTION_DONE, enterFallback = false),
            AutoSend.forEditor(EditorInfo.IME_ACTION_DONE, multiLine),
        )
    }

    @Test
    fun noEnterActionFlag_isHonored() {
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION, singleLine))
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION, multiLine))
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_UNSPECIFIED or EditorInfo.IME_FLAG_NO_ENTER_ACTION, singleLine))
    }

    @Test
    fun actionNone_orMultilineWithoutAction_isSkipped() {
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_NONE, singleLine))
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_UNSPECIFIED, multiLine))
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_SEARCH, multiLine))
        assertEquals(AutoSend.Skip, AutoSend.forEditor(EditorInfo.IME_ACTION_SEND, InputType.TYPE_NULL))
    }

    @Test
    fun singleLineWithoutSendAction_requestsSend() {
        val send = AutoSend.EditorAction(EditorInfo.IME_ACTION_SEND, enterFallback = true)
        assertEquals(send, AutoSend.forEditor(EditorInfo.IME_ACTION_UNSPECIFIED, singleLine))
        assertEquals(send, AutoSend.forEditor(EditorInfo.IME_ACTION_SEARCH, singleLine))
    }
}
