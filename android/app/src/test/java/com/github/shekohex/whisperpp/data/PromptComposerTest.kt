package com.github.shekohex.whisperpp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun streamingOutput_neverExposesSplitReasoningOrTranscriptTags() {
        val output = "<think>PRIVATE PLAN</think><TRANSCRIPT>Hello</TRANSCRIPT>"
        val allowed = setOf(null, "H", "He", "Hel", "Hell", "Hello")
        for (length in 0..output.length) {
            val cleaned = PromptComposer.cleanStreamingEnhancementOutput(output.take(length))
            assertTrue("Prefix $length produced $cleaned", cleaned in allowed)
        }
    }

    @Test
    fun streamingOutput_hidesUnclosedReasoningAfterVisibleAnswer() {
        assertEquals("Hello", PromptComposer.cleanStreamingEnhancementOutput("Hello <think>private"))
        assertEquals("Hello", PromptComposer.cleanStreamingEnhancementOutput("Hello <reflection>private"))
        assertEquals("Hello", PromptComposer.cleanStreamingEnhancementOutput("Hello <think\n private"))
        assertEquals("Hello world", PromptComposer.cleanStreamingEnhancementOutput("Hello <think>private</think>world"))
    }

    @Test
    fun streamingOutput_preservesOrdinaryCodeAndArabic() {
        assertEquals("a < b", PromptComposer.cleanStreamingEnhancementOutput("a < b"))
        assertEquals("<div>Hello</div>", PromptComposer.cleanStreamingEnhancementOutput("<div>Hello</div>"))
        assertEquals("مرحباً", PromptComposer.cleanStreamingEnhancementOutput("<think>خطة</think>مرحباً"))
    }
}
