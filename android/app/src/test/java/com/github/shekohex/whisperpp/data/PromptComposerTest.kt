package com.github.shekohex.whisperpp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun enhancementMessage_wrapsTranscriptAndContextInTags() {
        val message = PromptComposer.composeEnhancementMessage("Clean up.", "hello world", "Earlier text")
        assertEquals(
            "Clean up.\n\n<CONTEXT>\nEarlier text\n</CONTEXT>\n\n<TRANSCRIPT>\nhello world\n</TRANSCRIPT>",
            message,
        )
        assertEquals(
            "Clean up.\n\n<TRANSCRIPT>\nhello\n</TRANSCRIPT>",
            PromptComposer.composeEnhancementMessage("Clean up.", "hello", " "),
        )
    }

    @Test
    fun enhancementSystemPrompt_ordersPreamblePromptPresetVocabulary() {
        val prompt = PromptComposer.composeEnhancementSystemPrompt(
            preamble = " PREAMBLE ",
            prompt = "PROMPT",
            presetInstruction = "Be brief.",
            vocabulary = listOf("Kotlin", "Compose"),
        )
        assertEquals(
            "PREAMBLE\n\nPROMPT\n\nPreset instruction:\nBe brief.\n\nVocabulary (preferred spellings): Kotlin, Compose",
            prompt,
        )
        assertEquals("PROMPT", PromptComposer.composeEnhancementSystemPrompt("", "PROMPT", null, emptyList()))
    }

    @Test
    fun cleanOutput_stripsReasoningBlocksAndTags() {
        assertEquals("Fixed text.", PromptComposer.cleanEnhancementOutput("<think>\nplan the edit\n</think>\nFixed text."))
        assertEquals("Fixed text.", PromptComposer.cleanEnhancementOutput("<THINKING>a</THINKING> Fixed <reasoning>b</reasoning>text."))
        assertEquals("Fixed text.", PromptComposer.cleanEnhancementOutput("<TRANSCRIPT>\nFixed text.\n</TRANSCRIPT>"))
        assertEquals("a < b and c > d", PromptComposer.cleanEnhancementOutput("a < b and c > d"))
    }

    @Test
    fun cleanOutput_rejectsEmptyResults() {
        assertNull(PromptComposer.cleanEnhancementOutput("   "))
        assertNull(PromptComposer.cleanEnhancementOutput("<think>only reasoning</think>"))
        assertNull(PromptComposer.cleanEnhancementOutput("<think>reasoning that never closed"))
    }
}
