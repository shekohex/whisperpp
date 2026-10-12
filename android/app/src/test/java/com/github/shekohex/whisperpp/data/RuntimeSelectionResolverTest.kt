package com.github.shekohex.whisperpp.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuntimeSelectionResolverTest {

    @Test
    fun resolve_appliesAppThenLanguageThenGlobal_precedence_independentlyPerChannel() {
        val providers = listOf(
            provider(id = "appP", models = listOf(ModelConfig(id = "appStt", name = "appStt", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)))),
            provider(id = "langP", models = listOf(ModelConfig(id = "langText", name = "langText", capabilities = ModelCapabilities.TEXT))),
            provider(id = "globP", models = listOf(
                ModelConfig(id = "globStt", name = "globStt", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)),
                ModelConfig(id = "globText", name = "globText", capabilities = ModelCapabilities.TEXT),
            )),
        )

        val languageDefaults = listOf(
            LanguageProfile(
                languageCode = "en",
                transcriptionProviderId = "globP",
                transcriptionModelId = "globStt",
                enhancementProviderId = "langP",
                enhancementModelId = "langText",
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
            provider(id = "appP", models = listOf(ModelConfig(id = "appOnly", name = "appOnly", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)))),
            provider(id = "langP", models = listOf(ModelConfig(id = "langStt", name = "langStt", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)))),
            provider(id = "globP", models = listOf(ModelConfig(id = "globStt", name = "globStt", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)))),
        )

        val languageDefaults = listOf(
            LanguageProfile(
                languageCode = "en",
                transcriptionProviderId = "langP",
                transcriptionModelId = "langStt",
                enhancementProviderId = "",
                enhancementModelId = "",
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
    fun resolve_textOnlyModelForStt_isRejected_fallsBackAndEmitsWarning() {
        val providers = listOf(
            provider(id = "appP", models = listOf(ModelConfig(id = "textAsStt", name = "textAsStt", capabilities = ModelCapabilities.TEXT))),
            provider(id = "globP", models = listOf(ModelConfig(id = "globStt", name = "globStt", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)))),
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
    fun resolve_sttOnlyModelForText_isRejected() {
        val providers = listOf(
            provider(id = "p", models = listOf(ModelConfig(id = "whisper", name = "whisper", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)))),
        )

        val effective = RuntimeSelectionResolver.resolve(
            packageName = null,
            languageCode = "auto",
            providers = providers,
            languageDefaults = emptyList(),
            appMappings = emptyList(),
            globalSttProviderId = "p",
            globalSttModelId = "whisper",
            globalTextProviderId = "p",
            globalTextModelId = "whisper",
            basePrompt = "",
            profiles = emptyList(),
        )

        assertEquals("whisper", effective.sttModelId)
        assertEquals("", effective.textModelId)
        assertTrue(effective.warnings.any { it.kind == RuntimeWarningKind.INCOMPATIBLE_MODEL_KIND && it.channel == RuntimeChannel.TEXT })
    }

    @Test
    fun resolve_missingProfileReference_usesBaseOnlyPrompt_andEmitsWarning() {
        val providers = listOf(
            provider(id = "globP", models = listOf(
                ModelConfig(id = "globStt", name = "globStt", capabilities = ModelCapabilities.stt(ProviderType.OPENAI)),
                ModelConfig(id = "globText", name = "globText", capabilities = ModelCapabilities.TEXT),
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

    @Test
    fun resolve_geminiModelFromV2Storage_isSelectableForSttAfterMigration() = runBlocking {
        val file = File.createTempFile("settings-gemini-stt", ".preferences_pb").apply { delete(); deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        dataStore.edit {
            it[PreferenceKeys.PROVIDERS_JSON] = """
                [{"id": "gem", "name": "Gemini", "type": "GEMINI", "endpoint": "https://generativelanguage.googleapis.com/v1beta",
                  "authMode": "API_KEY", "models": [{"id": "gemini-2.0-flash", "name": "Flash", "kind": "MULTIMODAL"}]}]
            """.trimIndent()
        }
        val repository = SettingsRepository(dataStore)
        repository.migrateProviderSchemaV3IfNeeded()

        val effective = RuntimeSelectionResolver.resolve(
            packageName = null,
            languageCode = null,
            providers = repository.providers.first(),
            languageDefaults = emptyList(),
            appMappings = emptyList(),
            globalSttProviderId = "gem",
            globalSttModelId = "gemini-2.0-flash",
            globalTextProviderId = "gem",
            globalTextModelId = "gemini-2.0-flash",
            basePrompt = "BASE",
            profiles = emptyList(),
        )

        assertEquals("gem", effective.sttProviderId)
        assertEquals("gemini-2.0-flash", effective.sttModelId)
        assertEquals("gemini-2.0-flash", effective.textModelId)
        assertTrue(effective.warnings.isEmpty())
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
