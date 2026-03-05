package com.github.shekohex.whisperpp.command

import org.junit.Assert.assertTrue
import org.junit.Test

class TransformPromptBuilderTest {

    @Test
    fun build_includesPresetAndSpoken_withExplicitOverrideRule_andDelimitedSelectedText() {
        val preset = "Make it shorter."
        val spoken = "Keep the tone friendly."
        val selected = "Hello world"

        val prompt = TransformPromptBuilder.build(
            basePrompt = "BASE",
            presetInstruction = preset,
            spokenInstruction = spoken,
            selectedText = selected,
        )

        assertTrue(prompt.contains(preset))
        assertTrue(prompt.contains(spoken))
        assertTrue(prompt.contains(TransformPromptBuilder.SPOKEN_OVERRIDES_PRESET_RULE))
        assertTrue(prompt.contains("```text\n$selected\n```"))
    }
}
