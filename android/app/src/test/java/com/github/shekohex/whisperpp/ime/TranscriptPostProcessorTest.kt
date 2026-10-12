package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.core.text.TranscriptFilter
import com.github.shekohex.whisperpp.core.text.WordReplacements
import com.github.shekohex.whisperpp.domain.dictation.DictationOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptPostProcessorTest {
    private fun processor(
        filter: TranscriptFilter.Options = TranscriptFilter.Options(),
        replacements: WordReplacements = WordReplacements.EMPTY,
    ) = TranscriptPostProcessor({ filter }, { replacements })

    @Test
    fun globalFillerSwitch_appliesWhenModeHasNoOpinion() {
        val result = processor(TranscriptFilter.Options(removeFillers = true))
            .process("um hello there", DictationOptions())

        assertEquals("hello there", result)
    }

    @Test
    fun modeSwitch_overridesGlobalSwitch() {
        val global = TranscriptFilter.Options(removeFillers = true)

        val result = processor(global).process("um hello", DictationOptions(removeFillers = false))

        assertEquals("um hello", result)
    }

    @Test
    fun modeSwitch_enablesWhatGlobalLeavesOff() {
        val result = processor().process("um hello", DictationOptions(removeFillers = true))

        assertEquals("hello", result)
    }

    @Test
    fun replacements_runAfterTheFilter() {
        val rules = WordReplacements.compile(listOf(Replacement(listOf("gpt"), "ChatGPT")))

        val result = processor(replacements = rules).process("um use gpt", DictationOptions(removeFillers = true))

        assertEquals("use ChatGPT", result)
    }
}
