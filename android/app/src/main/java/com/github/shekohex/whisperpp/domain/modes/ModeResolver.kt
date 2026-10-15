package com.github.shekohex.whisperpp.domain.modes

import com.github.shekohex.whisperpp.data.AppPromptMapping
import com.github.shekohex.whisperpp.data.AppendMode
import com.github.shekohex.whisperpp.data.LanguageProfile
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.PromptProfile
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderModelOverride
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeSelectionResolver
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.modes.ModeSeeds

/**
 * Picks the mode for a session (§4.10): keyboard-selected mode → mode owning the editor's package →
 * default mode, then resolves its runtime through [RuntimeSelectionResolver]. Fields the mode leaves
 * null fall back to the global settings; invalid references fall back with warnings and mark the mode
 * [ModeAvailability.Unavailable] instead of failing.
 */
object ModeResolver {
    data class Input(
        val modes: List<Mode>,
        val selectedModeId: String?,
        val packageName: String?,
        /** Pins the mode a running session started with; ignored when that mode no longer exists. */
        val forcedModeId: String? = null,
        /** True until [com.github.shekohex.whisperpp.data.modes.LegacyModesMigration] ran: app mappings and language profiles still apply underneath the mode. */
        val legacyActive: Boolean = false,
        val keyboardLanguage: String = AUTO,
        val providers: List<ServiceProvider> = emptyList(),
        val languageDefaults: List<LanguageProfile> = emptyList(),
        val appMappings: List<AppPromptMapping> = emptyList(),
        val globalStt: Mode.Selection? = null,
        val globalText: Mode.Selection? = null,
        val basePrompt: String = "",
        val profiles: List<PromptProfile> = emptyList(),
        val globalEnhancementEnabled: Boolean = false,
        val globalRemoveFillers: Boolean = false,
        val globalFormatParagraphs: Boolean = false,
        val globalAutoSend: Boolean = false,
        val hasCredential: (providerId: String) -> Boolean = { true },
    )

    const val AUTO = "auto"

    /** Resolution order only; disabled modes are skipped, the default is always usable. */
    fun pick(
        modes: List<Mode>,
        selectedModeId: String?,
        packageName: String?,
        forcedModeId: String? = null,
    ): Pair<Mode, ModeResolution.Source> {
        val pkg = packageName?.trim().orEmpty()
        val usable = modes.filter { it.enabled || it.isDefault }
        fun sourceOf(mode: Mode) = when {
            mode.id == selectedModeId -> ModeResolution.Source.SELECTED
            pkg.isNotEmpty() && pkg in mode.appPackages -> ModeResolution.Source.APP
            else -> ModeResolution.Source.DEFAULT
        }
        forcedModeId?.let { id -> modes.firstOrNull { it.id == id }?.let { return it to sourceOf(it) } }
        selectedModeId?.let { id ->
            usable.firstOrNull { it.id == id }?.let { return it to ModeResolution.Source.SELECTED }
        }
        if (pkg.isNotEmpty()) {
            usable.firstOrNull { pkg in it.appPackages }?.let { return it to ModeResolution.Source.APP }
        }
        val default = modes.firstOrNull { it.isDefault } ?: modes.firstOrNull() ?: ModeSeeds.DICTATION
        return default to ModeResolution.Source.DEFAULT
    }

    fun resolve(input: Input): ModeResolution {
        val (mode, source) = pick(input.modes, input.selectedModeId, input.packageName, input.forcedModeId)
        val pkg = input.packageName?.trim().orEmpty()
        val legacyMapping = if (input.legacyActive && pkg.isNotEmpty()) {
            input.appMappings.firstOrNull { it.packageName == pkg }
        } else {
            null
        }
        val mapping = layer(mode, legacyMapping, pkg)
        val languageCode = mode.language ?: input.keyboardLanguage.trim().ifEmpty { AUTO }
        val runtime = RuntimeSelectionResolver.resolveWithMapping(
            mapping = mapping,
            languageCode = languageCode,
            providers = input.providers,
            languageDefaults = if (input.legacyActive) input.languageDefaults else emptyList(),
            globalSttProviderId = input.globalStt?.providerId.orEmpty(),
            globalSttModelId = input.globalStt?.modelId.orEmpty(),
            globalTextProviderId = input.globalText?.providerId.orEmpty(),
            globalTextModelId = input.globalText?.modelId.orEmpty(),
            basePrompt = input.basePrompt,
            profiles = input.profiles,
        )
        val enhancementEnabled = mode.enhancementEnabled ?: input.globalEnhancementEnabled
        return ModeResolution(
            mode = mode,
            source = source,
            runtime = runtime,
            languageCode = languageCode,
            enhancementEnabled = enhancementEnabled,
            removeFillers = mode.formatting.fillerRemoval ?: input.globalRemoveFillers,
            formatParagraphs = mode.formatting.paragraphs ?: input.globalFormatParagraphs,
            autoSend = mapping.autoSend ?: input.globalAutoSend,
            outputMode = mode.outputMode,
            contextSources = mode.contextSources,
            availability = availability(
                mode = mode,
                providers = input.providers,
                profiles = input.profiles,
                globalStt = input.globalStt,
                globalText = input.globalText,
                enhancementEnabled = enhancementEnabled,
                hasCredential = input.hasCredential,
            ),
        )
    }

    /**
     * First missing reference of [mode]: its STT selection (or the global one), its enhancement selection
     * (or the global one) when enhancement is on, then its prompt.
     */
    fun availability(
        mode: Mode,
        providers: List<ServiceProvider>,
        profiles: List<PromptProfile>,
        globalStt: Mode.Selection?,
        globalText: Mode.Selection?,
        enhancementEnabled: Boolean,
        hasCredential: (providerId: String) -> Boolean,
    ): ModeAvailability {
        check(RuntimeChannel.STT, merge(mode.sttSelection, globalStt), providers, hasCredential) { it.capabilities.stt }
            ?.let { return it }
        if (enhancementEnabled) {
            check(RuntimeChannel.TEXT, merge(mode.enhancementSelection, globalText), providers, hasCredential) { it.capabilities.text }
                ?.let { return it }
        }
        val promptId = mode.promptId
        if (promptId != null && profiles.none { it.id == promptId }) {
            return ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_PROMPT, channel = null, reference = promptId)
        }
        return ModeAvailability.Available
    }

    /** The mode as the app layer of [RuntimeSelectionResolver]; before migration the legacy mapping fills its gaps. */
    private fun layer(mode: Mode, legacy: AppPromptMapping?, packageName: String): AppPromptMapping {
        val promptId = mode.promptId
        val modePromptAppend = mode.enhancementPrompt?.trim()?.takeIf { it.isNotEmpty() }
        return AppPromptMapping(
            packageName = packageName,
            profileId = promptId ?: legacy?.profileId,
            appendMode = if (promptId != null || modePromptAppend != null) AppendMode.APPEND else legacy?.appendMode ?: AppendMode.APPEND,
            appPromptAppend = modePromptAppend ?: if (promptId != null) null else legacy?.appPromptAppend,
            sttOverride = mode.sttSelection?.toOverride() ?: legacy?.sttOverride,
            textOverride = mode.enhancementSelection?.toOverride() ?: legacy?.textOverride,
            autoSend = mode.autoSend ?: legacy?.autoSend,
        )
    }

    private fun Mode.Selection.toOverride() = ProviderModelOverride(providerId = providerId, modelId = modelId)

    /** Blank parts of a partial mode selection come from the global one, as in [RuntimeSelectionResolver]. */
    private fun merge(selection: Mode.Selection?, global: Mode.Selection?): Mode.Selection? {
        if (selection == null) return global
        return Mode.Selection(
            providerId = selection.providerId.ifBlank { global?.providerId.orEmpty() },
            modelId = selection.modelId.ifBlank { global?.modelId.orEmpty() },
        )
    }

    private fun check(
        channel: RuntimeChannel,
        selection: Mode.Selection?,
        providers: List<ServiceProvider>,
        hasCredential: (String) -> Boolean,
        isCompatible: (ModelConfig) -> Boolean,
    ): ModeAvailability.Unavailable? {
        val providerId = selection?.providerId?.trim().orEmpty()
        val provider = providers.firstOrNull { it.id == providerId }
            ?: return ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_PROVIDER, channel, providerId)
        val modelId = selection?.modelId?.trim().orEmpty()
        val model = provider.models.firstOrNull { it.id == modelId }
        if (model == null || !isCompatible(model)) {
            return ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_MODEL, channel, modelId)
        }
        if (provider.authMode == ProviderAuthMode.API_KEY && !hasCredential(provider.id)) {
            return ModeAvailability.Unavailable(ModeAvailability.Reason.MISSING_CREDENTIAL, channel, provider.id)
        }
        return null
    }
}
