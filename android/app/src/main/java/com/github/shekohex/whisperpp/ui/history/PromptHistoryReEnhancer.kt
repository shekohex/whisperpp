package com.github.shekohex.whisperpp.ui.history

import android.content.Context
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_TEMPERATURE
import com.github.shekohex.whisperpp.data.PromptComposer
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.dictation.EnhancementOutcome
import com.github.shekohex.whisperpp.dictation.EnhancementRunner
import com.github.shekohex.whisperpp.dictation.FailureReason
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import com.github.shekohex.whisperpp.domain.providers.toProviderUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Re-enhancement with the prompt, preset and text model that dictation would use today for the
 * entry's app. Mirrors the keyboard's enhancement step minus its first-use disclosure sheet: here
 * the user explicitly asked, and the screen confirms what is sent first.
 */
class PromptHistoryReEnhancer(
    private val context: Context,
    private val config: RuntimeConfigLoader,
    private val providers: ProviderFactory,
    private val enhancementPresetId: Flow<String?>,
    private val vocabulary: suspend () -> List<String>,
    private val runner: EnhancementRunner = EnhancementRunner(),
) : HistoryReEnhancer {
    override suspend fun reEnhance(entry: TranscriptHistoryEntry): HistoryReEnhancer.Result {
        val snapshot = config.load(entry.appPackage)
        val providerId = snapshot.effective.textProviderId
        val modelId = snapshot.effective.textModelId
        val provider = snapshot.provider(providerId)
        val model = provider?.models?.find { it.id == modelId }
        if (provider == null || model == null) {
            return failed(
                context.getString(
                    R.string.enhancement_unavailable,
                    providerId.ifBlank { context.getString(R.string.enhancement_unknown_provider) },
                    modelId.ifBlank { context.getString(R.string.enhancement_unknown_model) },
                ),
            )
        }
        val label = "${provider.name} ($modelId)"
        val enhancer = providers.enhancement(provider, model).getOrElse { return failedWith(label, it) }

        val temperature = if (provider.temperature > 0) provider.temperature else snapshot.prefs[ENHANCEMENT_TEMPERATURE] ?: 0.0f
        val systemPrompt = PromptComposer.composeEnhancementSystemPrompt(
            preamble = readRaw(R.raw.enhancement_system_preamble),
            prompt = snapshot.effective.prompt.trim().ifBlank { readRaw(R.raw.enhancement_prompt) },
            presetInstruction = presetById(enhancementPresetId.first().orEmpty())?.promptInstruction,
            vocabulary = vocabulary(),
        )
        val thinking = ThinkingConfig.from(provider, model)

        val outcome = runner.run(rawText = entry.rawText, budgetMs = provider.timeout.toLong()) { attemptTimeoutMs ->
            enhancer.enhance(
                EnhancementRequest(
                    systemPrompt = systemPrompt,
                    userText = entry.rawText,
                    temperature = temperature,
                    thinking = thinking,
                    timeoutMs = attemptTimeoutMs,
                ),
            ).getOrThrow()
        }
        return when (outcome) {
            is EnhancementOutcome.Succeeded -> PromptComposer.cleanEnhancementOutput(outcome.enhancedText)
                ?.let { HistoryReEnhancer.Result.Enhanced(it, modelId) }
                ?: failed(context.getString(R.string.enhancement_empty_output, label))
            is EnhancementOutcome.Skipped -> failed(context.getString(R.string.history_reenhance_nothing_to_enhance))
            is EnhancementOutcome.Failed -> when (val reason = outcome.reason) {
                FailureReason.Timeout -> failed(context.getString(R.string.enhancement_timed_out, label))
                is FailureReason.Transient -> failedWith(label, reason.error)
                is FailureReason.NonTransient -> failedWith(label, reason.error)
            }
        }
    }

    private suspend fun readRaw(resId: Int): String = withContext(Dispatchers.IO) {
        runCatching { context.resources.openRawResource(resId).bufferedReader().use { it.readText() } }.getOrDefault("")
    }

    private fun failedWith(label: String, error: Throwable) =
        failed(context.getString(R.string.enhancement_failed, label, error.toProviderUserMessage(context)))

    private fun failed(message: String) = HistoryReEnhancer.Result.Failed(message)
}
