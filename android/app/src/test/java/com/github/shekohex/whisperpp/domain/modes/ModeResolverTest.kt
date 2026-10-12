package com.github.shekohex.whisperpp.domain.modes

import com.github.shekohex.whisperpp.data.AppPromptMapping
import com.github.shekohex.whisperpp.data.LanguageProfile
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderModelOverride
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeSelectionResolver
import com.github.shekohex.whisperpp.data.RuntimeWarningKind
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.modes.ModeSeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeResolverTest {
    private val openAi = ServiceProvider(
        id = "openai",
        name = "OpenAI",
        type = ProviderType.OPENAI,
        endpoint = "https://api.openai.com/v1",
        models = listOf(
            ModelConfig("whisper", "Whisper", ModelCapabilities.stt(ProviderType.OPENAI)),
            ModelConfig("gpt", "GPT", ModelCapabilities.TEXT),
        ),
    )
    private val local = ServiceProvider(
        id = "local",
        name = "Local",
        type = ProviderType.WHISPER_ASR,
        endpoint = "http://10.0.0.2:9000/asr",
        authMode = ProviderAuthMode.NO_AUTH,
        models = listOf(ModelConfig("asr", "ASR", ModelCapabilities.stt(ProviderType.WHISPER_ASR))),
    )
    private val chatPrompt = PromptProfile("chat", "Chat", "Be casual")

    private val chat = Mode(id = "chat", name = "Chat", promptId = "chat", appPackages = listOf("org.telegram"))
    private val code = Mode(id = "code", name = "Code", sttSelection = Mode.Selection("local", "asr"), language = "en")
    private val modes = listOf(ModeSeeds.DICTATION, chat, code)

    private fun input(
        modes: List<Mode> = this.modes,
        selected: String? = null,
        pkg: String? = null,
        forced: String? = null,
        legacyActive: Boolean = false,
        appMappings: List<AppPromptMapping> = emptyList(),
        languageDefaults: List<LanguageProfile> = emptyList(),
        keyboardLanguage: String = "auto",
        hasCredential: (String) -> Boolean = { true },
    ) = ModeResolver.Input(
        modes = modes,
        selectedModeId = selected,
        packageName = pkg,
        forcedModeId = forced,
        legacyActive = legacyActive,
        keyboardLanguage = keyboardLanguage,
        providers = listOf(openAi, local),
        languageDefaults = languageDefaults,
        appMappings = appMappings,
        globalStt = Mode.Selection("openai", "whisper"),
        globalText = Mode.Selection("openai", "gpt"),
        basePrompt = "Base",
        profiles = listOf(chatPrompt),
        globalEnhancementEnabled = true,
        globalAutoSend = false,
        hasCredential = hasCredential,
    )

    @Test
    fun precedence_selectedThenAppThenDefault() {
        assertEquals("code" to ModeResolution.Source.SELECTED, ModeResolver.pick(modes, "code", "org.telegram").let { it.first.id to it.second })
        assertEquals("chat" to ModeResolution.Source.APP, ModeResolver.pick(modes, null, "org.telegram").let { it.first.id to it.second })
        assertEquals(ModeSeeds.DICTATION_ID, ModeResolver.pick(modes, null, "com.other").first.id)
        assertEquals(ModeResolution.Source.DEFAULT, ModeResolver.pick(modes, "missing", null).second)
    }

    @Test
    fun disabledModes_areSkipped_butAForcedModeIsKept() {
        val disabled = modes.map { if (it.id == "chat") it.copy(enabled = false) else it }
        assertEquals(ModeSeeds.DICTATION_ID, ModeResolver.pick(disabled, "chat", "org.telegram").first.id)
        assertEquals("chat", ModeResolver.pick(disabled, null, "org.telegram", forcedModeId = "chat").first.id)
    }

    @Test
    fun emptyList_fallsBackToSeedDictation() {
        assertEquals(ModeSeeds.DICTATION, ModeResolver.pick(emptyList(), null, null).first)
    }

    @Test
    fun seededDictation_resolvesExactlyLikeTheGlobalSelections() {
        val resolution = ModeResolver.resolve(input(modes = ModeSeeds.ALL, pkg = "com.any"))
        val legacy = RuntimeSelectionResolver.resolve(
            packageName = "com.any",
            languageCode = "auto",
            providers = listOf(openAi, local),
            languageDefaults = emptyList(),
            appMappings = emptyList(),
            globalSttProviderId = "openai",
            globalSttModelId = "whisper",
            globalTextProviderId = "openai",
            globalTextModelId = "gpt",
            basePrompt = "Base",
            profiles = listOf(chatPrompt),
        )
        assertEquals(legacy, resolution.runtime)
        assertEquals("auto", resolution.languageCode)
        assertTrue(resolution.enhancementEnabled)
        assertFalse(resolution.autoSend)
        assertEquals(OutputMode.INSERT, resolution.outputMode)
        assertEquals(ModeAvailability.Available, resolution.availability)
    }

    @Test
    fun beforeMigration_legacyMappingAndLanguageProfileStillApply() {
        val mapping = AppPromptMapping(
            packageName = "com.legacy",
            profileId = "chat",
            appPromptAppend = "App text",
            textOverride = ProviderModelOverride("openai", "gpt"),
            autoSend = true,
        )
        val profile = LanguageProfile("ar", "local", "asr", "", "")
        val args = input(
            modes = ModeSeeds.ALL,
            pkg = "com.legacy",
            legacyActive = true,
            appMappings = listOf(mapping),
            languageDefaults = listOf(profile),
            keyboardLanguage = "ar",
        )
        val resolution = ModeResolver.resolve(args)
        val legacy = RuntimeSelectionResolver.resolve(
            packageName = "com.legacy",
            languageCode = "ar",
            providers = listOf(openAi, local),
            languageDefaults = listOf(profile),
            appMappings = listOf(mapping),
            globalSttProviderId = "openai",
            globalSttModelId = "whisper",
            globalTextProviderId = "openai",
            globalTextModelId = "gpt",
            basePrompt = "Base",
            profiles = listOf(chatPrompt),
        )
        assertEquals(legacy, resolution.runtime)
        assertEquals("local", resolution.runtime.sttProviderId)
        assertTrue(resolution.autoSend)

        val migrated = ModeResolver.resolve(args.copy(legacyActive = false))
        assertEquals("openai", migrated.runtime.sttProviderId)
        assertEquals("Base", migrated.runtime.prompt)
        assertFalse(migrated.autoSend)
    }

    @Test
    fun modeFields_overrideGlobals_andNullsInherit() {
        val resolution = ModeResolver.resolve(input(selected = "code"))
        assertEquals("local", resolution.runtime.sttProviderId)
        assertEquals("asr", resolution.runtime.sttModelId)
        assertEquals("openai", resolution.runtime.textProviderId)
        assertEquals("en", resolution.languageCode)

        val chatResolution = ModeResolver.resolve(input(pkg = "org.telegram"))
        assertEquals("Base\n\nBe casual", chatResolution.runtime.prompt)
        assertEquals(ModeResolution.Source.APP, chatResolution.source)
    }

    @Test
    fun modeSwitches_overrideGlobalSwitches() {
        val mode = Mode(
            id = "m",
            name = "M",
            enhancementEnabled = false,
            formatting = Mode.Formatting(fillerRemoval = true),
            autoSend = true,
            outputMode = OutputMode.CLIPBOARD,
            contextSources = Mode.ContextSources(clipboard = true),
        )
        val resolution = ModeResolver.resolve(input(modes = listOf(ModeSeeds.DICTATION, mode), selected = "m"))
        assertFalse(resolution.enhancementEnabled)
        assertTrue(resolution.removeFillers)
        assertFalse(resolution.formatParagraphs)
        assertTrue(resolution.autoSend)
        assertEquals(OutputMode.CLIPBOARD, resolution.outputMode)
        assertTrue(resolution.contextSources.clipboard)
    }

    @Test
    fun missingProvider_fallsBackToGlobal_andMarksUnavailable() {
        val broken = Mode(id = "b", name = "B", sttSelection = Mode.Selection("gone", "x"))
        val resolution = ModeResolver.resolve(input(modes = listOf(ModeSeeds.DICTATION, broken), selected = "b"))

        assertEquals("openai", resolution.runtime.sttProviderId)
        assertTrue(resolution.runtime.warnings.any { it.kind == RuntimeWarningKind.INVALID_PROVIDER })
        assertEquals(
            ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_PROVIDER, RuntimeChannel.STT, "gone"),
            resolution.availability,
        )
    }

    @Test
    fun missingOrIncompatibleModel_isMissingModel() {
        val wrongKind = Mode(id = "w", name = "W", sttSelection = Mode.Selection("openai", "gpt"))
        val availability = ModeResolver.resolve(input(modes = listOf(ModeSeeds.DICTATION, wrongKind), selected = "w")).availability
        assertEquals(ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_MODEL, RuntimeChannel.STT, "gpt"), availability)
    }

    @Test
    fun missingCredential_onlyForApiKeyProviders() {
        val noKeys: (String) -> Boolean = { false }
        val dictation = ModeResolver.resolve(input(hasCredential = noKeys)).availability
        assertEquals(ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_CREDENTIAL, RuntimeChannel.STT, "openai"), dictation)

        val localOnly = Mode(id = "l", name = "L", sttSelection = Mode.Selection("local", "asr"), enhancementEnabled = false)
        val availability = ModeResolver.resolve(input(modes = listOf(ModeSeeds.DICTATION, localOnly), selected = "l", hasCredential = noKeys)).availability
        assertEquals(ModeAvailability.Available, availability)
    }

    @Test
    fun missingPrompt_isReported_andThePromptFallsBackToBase() {
        val mode = Mode(id = "p", name = "P", promptId = "deleted")
        val resolution = ModeResolver.resolve(input(modes = listOf(ModeSeeds.DICTATION, mode), selected = "p"))
        assertEquals(ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_PROMPT, null, "deleted"), resolution.availability)
        assertEquals("Base", resolution.runtime.prompt)
    }

    @Test
    fun enhancementSelection_isOnlyCheckedWhenEnhancementIsOn() {
        val mode = Mode(id = "t", name = "T", enhancementSelection = Mode.Selection("openai", "missing"), enhancementEnabled = false)
        val list = listOf(ModeSeeds.DICTATION, mode)
        assertEquals(ModeAvailability.Available, ModeResolver.resolve(input(modes = list, selected = "t")).availability)
        val on = list.map { if (it.id == "t") it.copy(enhancementEnabled = true) else it }
        assertEquals(
            ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_MODEL, RuntimeChannel.TEXT, "missing"),
            ModeResolver.resolve(input(modes = on, selected = "t")).availability,
        )
    }
}
