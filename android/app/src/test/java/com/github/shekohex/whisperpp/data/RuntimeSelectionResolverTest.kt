package com.github.shekohex.whisperpp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSelectionResolverTest {

    @Test
    fun resolve_appliesAppThenLanguageThenGlobal_precedence_independentlyPerChannel() {
        val providers = listOf(
            provider(id = "appP", models = listOf(ModelConfig(id = "appStt", name = "appStt", kind = ModelKind.STT))),
            provider(id = "langP", models = listOf(ModelConfig(id = "langText", name = "langText", kind = ModelKind.TEXT))),
            provider(id = "globP", models = listOf(
                ModelConfig(id = "globStt", name = "globStt", kind = ModelKind.STT),
                ModelConfig(id = "globText", name = "globText", kind = ModelKind.TEXT),
            )),
        )

        val languageDefaults = listOf(
            LanguageProfile(
                languageCode = "en",
                transcriptionProviderId = "globP",
                transcriptionModelId = "globStt",
                smartFixProviderId = "langP",
                smartFixModelId = "langText",
            )
        )

        val mappings = listOf(
            AppPromptMapping(
                packageName = "com.example.app",
                sttOverride = ProviderModelOverride(providerId = "appP", modelId = "appStt"),
            )
        )

        val effective = RuntimeSelectionResolver.resolve(
            packageName = "com.example.app",
            languageCode = "en",
            providers = providers,
            languageDefaults = languageDefaults,
            appMappings = mappings,
            globalSttProviderId = "globP",
            globalSttModelId = "globStt",
            globalTextProviderId = "globP",
            globalTextModelId = "globText",
            basePrompt = "BASE",
            profiles = emptyList(),
        )

        assertEquals("appP", effective.sttProviderId)
        assertEquals("appStt", effective.sttModelId)
        assertEquals("langP", effective.textProviderId)
        assertEquals("langText", effective.textModelId)
    }

    @Test
    fun resolve_partialOverrideCascadesMissingFields_andFallsBackWhenInvalid() {
        val providers = listOf(
            provider(id = "appP", models = listOf(ModelConfig(id = "appOnly", name = "appOnly", kind = ModelKind.STT))),
            provider(id = "langP", models = listOf(ModelConfig(id = "langStt", name = "langStt", kind = ModelKind.STT))),
            provider(id = "globP", models = listOf(ModelConfig(id = "globStt", name = "globStt", kind = ModelKind.STT))),
        )

        val languageDefaults = listOf(
            LanguageProfile(
                languageCode = "en",
                transcriptionProviderId = "langP",
                transcriptionModelId = "langStt",
                smartFixProviderId = "",
                smartFixModelId = "",
            )
        )

        val mappings = listOf(
            AppPromptMapping(
                packageName = "com.example.app",
                sttOverride = ProviderModelOverride(providerId = "appP", modelId = ""),
            )
        )

        val effective = RuntimeSelectionResolver.resolve(
            packageName = "com.example.app",
            languageCode = "en",
            providers = providers,
            languageDefaults = languageDefaults,
            appMappings = mappings,
            globalSttProviderId = "globP",
            globalSttModelId = "globStt",
            globalTextProviderId = "",
            globalTextModelId = "",
            basePrompt = "BASE",
            profiles = emptyList(),
        )

        assertEquals("langP", effective.sttProviderId)
        assertEquals("langStt", effective.sttModelId)
        assertTrue(effective.warnings.any { it.kind == RuntimeWarningKind.INVALID_MODEL && it.channel == RuntimeChannel.STT })
    }

    @Test
    fun resolve_invalidModelKind_fallsBackAndEmitsWarning() {
        val providers = listOf(
            provider(id = "appP", models = listOf(ModelConfig(id = "textAsStt", name = "textAsStt", kind = ModelKind.TEXT))),
            provider(id = "globP", models = listOf(ModelConfig(id = "globStt", name = "globStt", kind = ModelKind.STT))),
        )

        val mappings = listOf(
            AppPromptMapping(
                packageName = "com.example.app",
                sttOverride = ProviderModelOverride(providerId = "appP", modelId = "textAsStt"),
            )
        )

        val effective = RuntimeSelectionResolver.resolve(
            packageName = "com.example.app",
            languageCode = "en",
            providers = providers,
            languageDefaults = emptyList(),
            appMappings = mappings,
            globalSttProviderId = "globP",
            globalSttModelId = "globStt",
            globalTextProviderId = "",
            globalTextModelId = "",
            basePrompt = "BASE",
            profiles = emptyList(),
        )

        assertEquals("globP", effective.sttProviderId)
        assertEquals("globStt", effective.sttModelId)
        assertTrue(effective.warnings.any { it.kind == RuntimeWarningKind.INCOMPATIBLE_MODEL_KIND && it.channel == RuntimeChannel.STT })
    }

    @Test
    fun resolve_missingProfileReference_usesBaseOnlyPrompt_andEmitsWarning() {
        val providers = listOf(
            provider(id = "globP", models = listOf(
                ModelConfig(id = "globStt", name = "globStt", kind = ModelKind.STT),
                ModelConfig(id = "globText", name = "globText", kind = ModelKind.TEXT),
            ))
        )

        val mappings = listOf(
            AppPromptMapping(
                packageName = "com.example.app",
                profileId = "missing",
                appendMode = AppendMode.APPEND,
                appPromptAppend = "APP",
            )
        )

        val effective = RuntimeSelectionResolver.resolve(
            packageName = "com.example.app",
            languageCode = "en",
            providers = providers,
            languageDefaults = emptyList(),
            appMappings = mappings,
            globalSttProviderId = "globP",
            globalSttModelId = "globStt",
            globalTextProviderId = "globP",
            globalTextModelId = "globText",
            basePrompt = "BASE",
            profiles = emptyList(),
        )

        assertEquals("BASE", effective.prompt)
        assertTrue(effective.warnings.any { it.kind == RuntimeWarningKind.MISSING_PROFILE })
    }

    private fun provider(id: String, models: List<ModelConfig>): ServiceProvider {
        return ServiceProvider(
            id = id,
            name = id,
            type = ProviderType.CUSTOM,
            endpoint = "https://example.com/v1",
            models = models,
        )
    }
}
