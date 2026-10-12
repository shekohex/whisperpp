package com.github.shekohex.whisperpp.data.modes

import com.github.shekohex.whisperpp.domain.modes.Mode
import com.github.shekohex.whisperpp.domain.modes.OutputMode

/** Invariants of a stored mode list, shared by the repository, the migration and backup import. */
internal object ModeRules {
    /**
     * Valid rows only, unique ids, exactly one (enabled) default and every package in at most one mode.
     * Modes in [claimFirst] keep their packages and default flag over the others; an empty result
     * becomes [ModeSeeds.ALL].
     */
    fun normalize(modes: List<Mode>, claimFirst: Set<String> = emptySet()): List<Mode> {
        val valid = modes.mapNotNull(::sanitize).distinctBy { it.id }
        if (valid.isEmpty()) return ModeSeeds.ALL
        val defaultId = valid.firstOrNull { it.isDefault && it.id in claimFirst }?.id
            ?: valid.firstOrNull { it.isDefault }?.id
            ?: valid.first().id
        val claimed = mutableSetOf<String>()
        val packagesById = (valid.filter { it.id in claimFirst } + valid.filterNot { it.id in claimFirst })
            .associate { mode -> mode.id to mode.appPackages.filter { claimed.add(it) } }
        return valid.map { mode ->
            val isDefault = mode.id == defaultId
            mode.copy(
                isDefault = isDefault,
                enabled = mode.enabled || isDefault,
                appPackages = packagesById[mode.id].orEmpty(),
            )
        }
    }

    /** Gson leaves missing/unknown values null; this restores defaults and trims ids. Null when unusable. */
    fun sanitize(mode: Mode?): Mode? {
        if (mode == null) return null
        val id = (mode.id as String?).orEmpty().trim()
        val name = (mode.name as String?).orEmpty().trim()
        if (id.isEmpty() || name.isEmpty()) return null
        val formatting = (mode.formatting as Mode.Formatting?) ?: Mode.Formatting()
        val context = (mode.contextSources as Mode.ContextSources?) ?: Mode.ContextSources()
        return Mode(
            id = id,
            name = name,
            icon = (mode.icon as Mode.Icon?) ?: Mode.Icon.CUSTOM,
            sttSelection = sanitizeSelection(mode.sttSelection),
            language = mode.language?.trim()?.takeIf { it.isNotEmpty() },
            enhancementEnabled = mode.enhancementEnabled,
            enhancementSelection = sanitizeSelection(mode.enhancementSelection),
            promptId = mode.promptId?.trim()?.takeIf { it.isNotEmpty() },
            formatting = Mode.Formatting(formatting.fillerRemoval, formatting.paragraphs),
            contextSources = Mode.ContextSources(context.selectedText, context.clipboard),
            outputMode = (mode.outputMode as OutputMode?) ?: OutputMode.INSERT,
            autoSend = mode.autoSend,
            appPackages = (mode.appPackages as List<String?>?).orEmpty()
                .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                .distinct(),
            isDefault = mode.isDefault,
            enabled = mode.enabled,
        )
    }

    private fun sanitizeSelection(selection: Mode.Selection?): Mode.Selection? {
        if (selection == null) return null
        val providerId = (selection.providerId as String?).orEmpty().trim()
        val modelId = (selection.modelId as String?).orEmpty().trim()
        return if (providerId.isEmpty() && modelId.isEmpty()) null else Mode.Selection(providerId, modelId)
    }
}
