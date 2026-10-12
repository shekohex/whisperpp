package com.github.shekohex.whisperpp.data

import com.github.shekohex.whisperpp.core.text.Vocabulary

object PromptComposer {
    fun compose(
        basePrompt: String,
        profileAppend: String?,
        appAppend: String?,
        appendMode: AppendMode,
    ): String {
        val base = basePrompt.trim().takeIf { it.isNotBlank() }.orEmpty()

        if (appendMode == AppendMode.NO_APPEND) {
            return base
        }

        val segments = listOf(
            base,
            profileAppend?.trim().orEmpty(),
            appAppend?.trim().orEmpty(),
        ).filter { it.isNotBlank() }

        return segments.joinToString(separator = "\n\n")
    }

    /**
     * Enhancement system prompt (§4.5): general preamble, the effective prompt (base + profile + app),
     * the preset instruction and the vocabulary block; blank parts are left out.
     */
    fun composeEnhancementSystemPrompt(
        preamble: String,
        prompt: String,
        presetInstruction: String?,
        vocabulary: List<String>,
    ): String {
        val preset = presetInstruction?.trim().orEmpty().takeIf { it.isNotEmpty() }?.let { "Preset instruction:\n$it" }
        return listOfNotNull(preamble.trim(), prompt.trim(), preset, Vocabulary.enhancementBlock(vocabulary))
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n\n")
    }

    /** Single user message: instructions, optional `<CONTEXT>` (data, not instructions), `<TRANSCRIPT>`. */
    fun composeEnhancementMessage(systemPrompt: String, transcript: String, context: String?): String = buildString {
        append(systemPrompt)
        append("\n\n")
        if (!context.isNullOrBlank()) {
            append("<CONTEXT>\n")
            append(context)
            append("\n</CONTEXT>\n\n")
        }
        append("<TRANSCRIPT>\n")
        append(transcript)
        append("\n</TRANSCRIPT>")
    }

    /**
     * Model output with reasoning blocks (`<think>`, `<thinking>`, `<reasoning>`, also an unclosed
     * leading one) and echoed `<TRANSCRIPT>` tags removed; null when nothing usable is left.
     */
    fun cleanEnhancementOutput(output: String): String? {
        var text = REASONING_BLOCK.replace(output, "")
        text = UNCLOSED_REASONING.replace(text, "")
        text = TRANSCRIPT_TAG.replace(text, "")
        return text.trim().takeIf { it.isNotEmpty() }
    }

    private val REASONING_BLOCK = Regex(
        "<(think|thinking|reasoning|reflection)\\b[^>]*>[\\s\\S]*?</\\1\\s*>",
        RegexOption.IGNORE_CASE,
    )
    private val UNCLOSED_REASONING = Regex("^\\s*<(think|thinking|reasoning)\\b[^>]*>[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val TRANSCRIPT_TAG = Regex("</?TRANSCRIPT>", RegexOption.IGNORE_CASE)
}
