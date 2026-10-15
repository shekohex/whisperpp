package com.github.shekohex.whisperpp.data.autolearn

import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewModel
import com.github.shekohex.whisperpp.domain.providers.ProviderConfigException
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig

/** Picks the review model: the Auto-Learn selection when valid, else the enhancement selection. */
object AutoLearnModelResolver {

    fun select(settings: AutoLearnSettings, providers: List<ServiceProvider>): Pair<ServiceProvider, ModelConfig>? =
        find(providers, settings.providerId, settings.modelId)
            ?: find(providers, settings.fallbackProviderId, settings.fallbackModelId)

    fun resolve(
        settings: AutoLearnSettings,
        providers: List<ServiceProvider>,
        factory: ProviderFactory,
    ): Result<AutoLearnReviewModel> {
        val (provider, model) = select(settings, providers)
            ?: return Result.failure(ProviderConfigException(ProviderConfigException.Problem.MISSING_MODEL))
        return factory.enhancement(provider, model).map { enhancement ->
            AutoLearnReviewModel(enhancement, ThinkingConfig.from(provider, model), model.timeoutMs?.takeIf { it > 0 }?.toLong())
        }
    }

    private fun find(providers: List<ServiceProvider>, providerId: String, modelId: String): Pair<ServiceProvider, ModelConfig>? {
        val pid = providerId.trim()
        val mid = modelId.trim()
        if (pid.isEmpty() || mid.isEmpty()) return null
        val provider = providers.firstOrNull { it.id == pid } ?: return null
        val model = provider.models.firstOrNull { it.id == mid && it.capabilities.text } ?: return null
        return provider to model
    }
}
