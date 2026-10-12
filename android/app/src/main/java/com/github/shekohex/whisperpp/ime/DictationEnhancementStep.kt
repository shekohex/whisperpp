package com.github.shekohex.whisperpp.ime

import android.content.Context
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.ENHANCEMENT_TEMPERATURE
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.PromptComposer
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.data.toNoticeText
import com.github.shekohex.whisperpp.dictation.EnhancementOutcome
import com.github.shekohex.whisperpp.dictation.EnhancementRunner
import com.github.shekohex.whisperpp.dictation.FailureReason
import com.github.shekohex.whisperpp.domain.dictation.DictationEnhancer
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import com.github.shekohex.whisperpp.domain.providers.toProviderUserMessage
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Enhancement step of a dictation run, backed by an [com.github.shekohex.whisperpp.domain.providers.EnhancementProvider] + [EnhancementRunner]. */
class DictationEnhancementStep(private val deps: Deps) : DictationEnhancer {
    class Deps(
        val context: Context,
        val config: RuntimeConfigLoader,
        val enhancementPresetId: Flow<String?>,
        val providers: ProviderFactory,
        val runner: EnhancementRunner,
        val disclosures: FirstUseDisclosureGate,
        /** Non-null when the current editor forbids sending; the text explains why. */
        val blockedNotice: () -> String?,
        val onWarning: (String) -> Unit,
        val onRequestSent: () -> Unit,
        val defaultPrompt: () -> String,
        /** General system template placed before the selected prompt (§4.5). */
        val systemPreamble: () -> String = { "" },
        /** Dictionary vocabulary, injected as preferred spellings. */
        val vocabulary: suspend () -> List<String> = { emptyList() },
    )

    private val context: Context
        get() = deps.context

    override suspend fun isEnabled(request: DictationEnhancer.Request): Boolean {
        val enabled = deps.config.prefs()[ENHANCEMENT_ENABLED] ?: false
        return enabled && !isBlankOrPunctuationOnly(request.rawText)
    }

    override suspend fun enhance(request: DictationEnhancer.Request): DictationEnhancer.Result {
        val snapshot = deps.config.load(request.focus.packageName)
        val textProviderId = snapshot.effective.textProviderId
        val textModelId = snapshot.effective.textModelId
        val provider = snapshot.provider(textProviderId)
        val unknownProvider = context.getString(R.string.enhancement_unknown_provider)
        val providerLabel = provider?.name?.trim().orEmpty().ifBlank { textProviderId.ifBlank { unknownProvider } }
        val modelLabel = textModelId.ifBlank { context.getString(R.string.enhancement_unknown_model) }

        deps.blockedNotice()?.let { notice ->
            return kept(context.getString(R.string.enhancement_skipped_blocked, providerLabel, modelLabel, notice), isError = false)
        }
        snapshot.effective.warnings.toNoticeText(RuntimeChannel.TEXT).takeIf { it.isNotBlank() }?.let(deps.onWarning)
        val model = provider?.models?.find { it.id == textModelId }
        if (provider == null || model == null) {
            return kept(context.getString(R.string.enhancement_unavailable, textProviderId.ifBlank { unknownProvider }, modelLabel))
        }

        val disclosure = PrivacyDisclosureFormatter.disclosureForEnhancement(
            provider = provider,
            selectedModelId = textModelId,
            useContext = snapshot.prefs[USE_CONTEXT] ?: false,
        )
        if (deps.disclosures.await(FirstUseDisclosureGate.Mode.ENHANCEMENT_TEXT, disclosure) != FirstUseDisclosureGate.Decision.CONTINUE) {
            return DictationEnhancer.Result.Kept()
        }
        val label = "${provider.name} ($textModelId)"
        val enhancer = deps.providers.enhancement(provider, model).getOrElse { error ->
            return keptForConfigError(error, label)
        }

        val temperature = if (provider.temperature > 0) provider.temperature else snapshot.prefs[ENHANCEMENT_TEMPERATURE] ?: 0.0f
        val presetInstruction = presetById(deps.enhancementPresetId.first().orEmpty())?.promptInstruction
        val systemPrompt = PromptComposer.composeEnhancementSystemPrompt(
            preamble = deps.systemPreamble(),
            prompt = snapshot.effective.prompt.trim().ifBlank { deps.defaultPrompt() },
            presetInstruction = presetInstruction,
            vocabulary = deps.vocabulary(),
        )
        val thinking = ThinkingConfig.from(provider, model)

        deps.onRequestSent()
        // The provider timeout is the total budget for all attempts and backoff waits.
        val outcome = deps.runner.run(rawText = request.rawText, budgetMs = provider.timeout.toLong()) { attemptTimeoutMs ->
            enhancer.enhance(
                EnhancementRequest(
                    systemPrompt = systemPrompt,
                    userText = request.rawText,
                    contextPrompt = request.contextPrompt,
                    temperature = temperature,
                    thinking = thinking,
                    timeoutMs = attemptTimeoutMs,
                )
            ).getOrThrow()
        }
        return when (outcome) {
            is EnhancementOutcome.Succeeded -> PromptComposer.cleanEnhancementOutput(outcome.enhancedText)
                ?.let { DictationEnhancer.Result.Enhanced(it, label, provider.id, textModelId) }
                ?: kept(context.getString(R.string.enhancement_empty_output, label))
            is EnhancementOutcome.Skipped -> DictationEnhancer.Result.Kept()
            is EnhancementOutcome.Failed -> when (val reason = outcome.reason) {
                FailureReason.Timeout -> kept(context.getString(R.string.enhancement_timed_out, label))
                is FailureReason.Transient -> failed(label, reason.error)
                is FailureReason.NonTransient -> keptForConfigError(reason.error, label)
            }
        }
    }

    /** Unsupported provider/model combinations are a skip, not an error (G-08). */
    private fun keptForConfigError(error: Throwable, label: String): DictationEnhancer.Result {
        val problem = (error as? ProviderConfigException)?.problem ?: return failed(label, error)
        val message = error.toProviderUserMessage(context)
        return when (problem) {
            ProviderConfigException.Problem.UNSUPPORTED_PROVIDER_TYPE,
            ProviderConfigException.Problem.MODEL_LACKS_TEXT,
            ProviderConfigException.Problem.MODEL_NOT_FOUND,
            -> kept(context.getString(R.string.enhancement_skipped, label, message), isError = false)
            ProviderConfigException.Problem.MISSING_ENDPOINT,
            ProviderConfigException.Problem.INVALID_ENDPOINT,
            ProviderConfigException.Problem.MISSING_API_KEY,
            ProviderConfigException.Problem.MISSING_MODEL,
            ProviderConfigException.Problem.MODEL_LACKS_STT,
            -> kept(context.getString(R.string.enhancement_unavailable_reason, label, message))
        }
    }

    private fun failed(label: String, error: Throwable): DictationEnhancer.Result =
        kept(context.getString(R.string.enhancement_failed, label, error.toProviderUserMessage(context)))

    private fun kept(notice: String, isError: Boolean = true) = DictationEnhancer.Result.Kept(notice, isError)

    private fun isBlankOrPunctuationOnly(text: String): Boolean {
        if (text.isBlank()) return true
        return text.all { ch ->
            ch.isWhitespace() || when (Character.getType(ch.code)) {
                Character.CONNECTOR_PUNCTUATION.toInt(),
                Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(),
                Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
                Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt(),
                -> true
                else -> false
            }
        }
    }
}
