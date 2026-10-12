package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.ThinkingType

/**
 * One enhancement (LLM rewrite) call. [systemPrompt] holds the instructions; [userText] is wrapped
 * in `<TRANSCRIPT>` and [contextPrompt] in `<CONTEXT_INFORMATION>` by [composePrompt].
 */
data class EnhancementRequest(
    val systemPrompt: String,
    val userText: String,
    val contextPrompt: String? = null,
    val temperature: Float = 0f,
    val thinking: ThinkingConfig = ThinkingConfig.Disabled,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    /** Single user message as sent today (instructions, optional context, transcript). */
    fun composePrompt(): String = buildString {
        append(systemPrompt)
        append("\n\n")
        if (!contextPrompt.isNullOrBlank()) {
            append("<CONTEXT_INFORMATION>\n")
            append(contextPrompt)
            append("\n</CONTEXT_INFORMATION>\n\n")
        }
        append("<TRANSCRIPT>\n")
        append(userText)
        append("\n</TRANSCRIPT>")
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}

/** Reasoning settings; only sent for models whose capabilities include thinking. */
sealed interface ThinkingConfig {
    data object Disabled : ThinkingConfig

    /**
     * [level] maps to OpenAI `reasoning_effort` / Gemini `thinkingLevel`; Gemini uses
     * [budgetTokens] (`thinkingBudget`) instead when set.
     */
    data class Enabled(val level: String, val budgetTokens: Int? = null) : ThinkingConfig

    companion object {
        fun from(provider: ServiceProvider, model: ModelConfig?): ThinkingConfig {
            if (!provider.thinkingEnabled || model?.capabilities?.thinking != true) return Disabled
            val budget = if (provider.thinkingType == ThinkingType.BUDGET) provider.thinkingBudget else null
            return Enabled(level = provider.thinkingLevel, budgetTokens = budget)
        }
    }
}
