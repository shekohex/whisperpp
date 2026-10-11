package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.SmartFixer
import com.github.shekohex.whisperpp.data.PreferenceKeys.SMART_FIX_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.SMART_FIX_TEMPERATURE
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.presetById
import com.github.shekohex.whisperpp.data.toNoticeText
import com.github.shekohex.whisperpp.dictation.EnhancementOutcome
import com.github.shekohex.whisperpp.dictation.EnhancementRunner
import com.github.shekohex.whisperpp.dictation.FailureReason
import com.github.shekohex.whisperpp.domain.dictation.DictationEnhancer
import com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatter
import com.github.shekohex.whisperpp.privacy.SecretsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** Enhancement ("Smart Fix") step of a dictation run, backed by [SmartFixer] + [EnhancementRunner]. */
class DictationEnhancementStep(private val deps: Deps) : DictationEnhancer {
    class Deps(
        val config: RuntimeConfigLoader,
        val enhancementPresetId: Flow<String?>,
        val secretsStore: SecretsStore,
        val smartFixer: SmartFixer,
        val runner: EnhancementRunner,
        val disclosures: FirstUseDisclosureGate,
        /** Non-null when the current editor forbids sending; the text explains why. */
        val blockedNotice: () -> String?,
        val onWarning: (String) -> Unit,
        val onRequestSent: () -> Unit,
        val defaultPrompt: () -> String,
    )

    override suspend fun isEnabled(request: DictationEnhancer.Request): Boolean {
        val enabled = deps.config.prefs()[SMART_FIX_ENABLED] ?: false
        return enabled && !isBlankOrPunctuationOnly(request.rawText)
    }

    override suspend fun enhance(request: DictationEnhancer.Request): DictationEnhancer.Result {
        // The runner reports cancellation as an outcome; make sure the blocking call is aborted too.
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) deps.smartFixer.cancel()
        }
        try {
            return run(request)
        } finally {
            cancelHandle.dispose()
        }
    }

    private suspend fun run(request: DictationEnhancer.Request): DictationEnhancer.Result {
        val snapshot = deps.config.load(request.focus.packageName)
        val textProviderId = snapshot.effective.textProviderId
        val textModelId = snapshot.effective.textModelId
        val provider = snapshot.provider(textProviderId)
        val providerLabel = provider?.name?.trim().orEmpty().ifBlank { textProviderId.ifBlank { "Unknown provider" } }
        val modelLabel = textModelId.ifBlank { "Unknown model" }

        deps.blockedNotice()?.let { notice ->
            return kept("Enhancement skipped for $providerLabel ($modelLabel): $notice", isError = false)
        }
        snapshot.effective.warnings.toNoticeText(RuntimeChannel.TEXT).takeIf { it.isNotBlank() }?.let(deps.onWarning)
        if (provider == null || textModelId.isBlank() || provider.models.none { it.id == textModelId }) {
            return kept("Enhancement unavailable for ${textProviderId.ifBlank { "Unknown provider" }} ($modelLabel)")
        }

        val disclosure = PrivacyDisclosureFormatter.disclosureForEnhancement(
            provider = provider,
            selectedModelId = textModelId,
            useContext = snapshot.prefs[USE_CONTEXT] ?: false,
        )
        if (deps.disclosures.await(FirstUseDisclosureGate.Mode.ENHANCEMENT_TEXT, disclosure) != FirstUseDisclosureGate.Decision.CONTINUE) {
            return DictationEnhancer.Result.Kept()
        }
        if (provider.endpoint.isBlank()) {
            return kept("Enhancement unavailable for ${provider.name} ($textModelId): missing endpoint")
        }
        val providerWithApiKey = provider.copy(apiKey = deps.secretsStore.getProviderApiKey(provider.id).orEmpty())
        if (providerWithApiKey.authMode == ProviderAuthMode.API_KEY && providerWithApiKey.apiKey.isBlank()) {
            return kept("Enhancement unavailable for ${provider.name} ($textModelId): missing API key")
        }

        val temperature = if (provider.temperature > 0) provider.temperature else snapshot.prefs[SMART_FIX_TEMPERATURE] ?: 0.0f
        val presetInstruction = presetById(deps.enhancementPresetId.first().orEmpty())?.promptInstruction
        val promptTemplate = buildPromptTemplate(snapshot.effective.prompt, presetInstruction)

        deps.onRequestSent()
        val outcome = deps.runner.run(rawText = request.rawText) {
            withContext(Dispatchers.IO) {
                deps.smartFixer.fix(
                    text = request.rawText,
                    contextInformation = request.contextPrompt,
                    provider = providerWithApiKey,
                    modelId = textModelId,
                    temperature = temperature,
                    promptTemplate = promptTemplate,
                )
            }
        }
        val label = "${provider.name} ($textModelId)"
        return when (outcome) {
            is EnhancementOutcome.Succeeded -> DictationEnhancer.Result.Enhanced(outcome.enhancedText, label)
            is EnhancementOutcome.Skipped -> DictationEnhancer.Result.Kept()
            is EnhancementOutcome.Failed -> when (val reason = outcome.reason) {
                FailureReason.Cancelled -> DictationEnhancer.Result.Kept()
                FailureReason.Timeout -> kept("Enhancement timed out for $label")
                is FailureReason.Transient -> kept("Enhancement failed for $label: ${reason.message}")
                is FailureReason.NonTransient -> kept("Enhancement failed for $label: ${reason.message}")
            }
        }
    }

    private fun kept(notice: String, isError: Boolean = true) = DictationEnhancer.Result.Kept(notice, isError)

    private fun buildPromptTemplate(basePrompt: String, presetInstruction: String?): String {
        val base = basePrompt.trim().ifBlank { deps.defaultPrompt().trim() }
        val preset = presetInstruction?.trim().orEmpty()
        if (preset.isBlank()) return base
        return buildString {
            if (base.isNotEmpty()) {
                append(base)
                append("\n\n")
            }
            append("Preset instruction:\n")
            append(preset)
        }.trimEnd()
    }

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
