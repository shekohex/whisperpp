package com.github.shekohex.whisperpp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptComposerTest {

    @Test
    fun compose_ordersBaseThenProfileThenApp_andOmitsBlanks() {
        val prompt = PromptComposer.compose(
            basePrompt = "BASE",
            profileAppend = "PROFILE",
            appAppend = "APP",
            appendMode = AppendMode.APPEND,
        )
        assertEquals("BASE\n\nPROFILE\n\nAPP", prompt)

        val promptWithBlanks = PromptComposer.compose(
            basePrompt = " BASE ",
            profileAppend = " ",
            appAppend = "APP",
            appendMode = AppendMode.APPEND,
        )
        assertEquals("BASE\n\nAPP", promptWithBlanks)
    }

    @Test
    fun compose_noAppend_returnsBaseOnly() {
        val prompt = PromptComposer.compose(
            basePrompt = "BASE",
            profileAppend = "PROFILE",
            appAppend = "APP",
            appendMode = AppendMode.NO_APPEND,
        )
        assertEquals("BASE", prompt)
    }
}
