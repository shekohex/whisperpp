package com.github.shekohex.whisperpp.domain.modes

import com.github.shekohex.whisperpp.data.EffectiveRuntimeConfig

/** The mode picked for a session and the runtime it resolves to (null mode fields replaced by globals). */
data class ModeResolution(
    val mode: Mode,
    val source: Source,
    /** Provider/model ids and composed prompt; invalid references fell back with warnings. */
    val runtime: EffectiveRuntimeConfig,
    val languageCode: String,
    val enhancementEnabled: Boolean,
    val removeFillers: Boolean,
    val formatParagraphs: Boolean,
    val autoSend: Boolean,
    val outputMode: OutputMode,
    val contextSources: Mode.ContextSources,
    val availability: ModeAvailability,
) {
    enum class Source {
        /** Picked with the keyboard mode chip. */
        SELECTED,

        /** The editor's package is assigned to the mode. */
        APP,
        DEFAULT,
    }
}
