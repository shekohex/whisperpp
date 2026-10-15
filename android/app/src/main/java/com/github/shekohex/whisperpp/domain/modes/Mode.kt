package com.github.shekohex.whisperpp.domain.modes

/**
 * Everything one dictation session needs (§4.10). A null field inherits the global setting, so a mode
 * that sets nothing behaves exactly like the app without modes.
 */
data class Mode(
    // Defaults everywhere give Gson a no-arg constructor, so fields missing from stored JSON keep them.
    val id: String = "",
    val name: String = "",
    val icon: Icon = Icon.MIC,
    /** Transcription provider/model; null = global STT selection. */
    val sttSelection: Selection? = null,
    /** Language code sent to STT; null = the keyboard's language (auto by default). */
    val language: String? = null,
    /** Null = global "Enhancement" switch. */
    val enhancementEnabled: Boolean? = null,
    /** Enhancement provider/model; null = global text selection. */
    val enhancementSelection: Selection? = null,
    /** Prompt profile appended to the base prompt; null = base prompt only. */
    val promptId: String? = null,
    /** Extra prompt text appended after the profile; null/blank = none. */
    val enhancementPrompt: String? = null,
    val formatting: Formatting = Formatting(),
    val contextSources: ContextSources = ContextSources(),
    val outputMode: OutputMode = OutputMode.INSERT,
    /** Null = global "Auto-send after insert". */
    val autoSend: Boolean? = null,
    /** Android package names this mode is used for; a package belongs to at most one mode. */
    val appPackages: List<String> = emptyList(),
    val isDefault: Boolean = false,
    /** Disabled modes are never picked; the default mode is always enabled. */
    val enabled: Boolean = true,
) {
    /** Small Material icon set the Modes UI maps to vectors. */
    enum class Icon { MIC, CHAT, MAIL, EDIT, ASSISTANT, CODE, NOTES, CUSTOM }

    data class Selection(val providerId: String = "", val modelId: String = "")

    /** Transcript clean-up switches; null = the global switch. */
    data class Formatting(
        val fillerRemoval: Boolean? = null,
        val paragraphs: Boolean? = null,
    )

    /** Extra context handed to enhancement (never to STT). */
    data class ContextSources(
        val selectedText: Boolean = false,
        val clipboard: Boolean = false,
    )
}
