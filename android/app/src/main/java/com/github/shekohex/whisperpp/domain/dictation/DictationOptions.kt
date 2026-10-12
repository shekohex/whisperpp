package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.modes.OutputMode

/** Per-run behaviour taken from the session's mode (§4.10). Defaults keep the pre-modes behaviour. */
data class DictationOptions(
    /** Mode the run started with; enhancement resolves this same mode later. */
    val modeId: String? = null,
    val outputMode: OutputMode = OutputMode.INSERT,
    /** Null = the enhancer reads the global switch. */
    val enhancementEnabled: Boolean? = null,
    /** Null = the global filler/paragraph switches. */
    val removeFillers: Boolean? = null,
    val formatParagraphs: Boolean? = null,
    /** Null = the service decides from the global setting. */
    val autoSend: Boolean? = null,
    /** Selected text / clipboard added to the enhancement context. */
    val contextSelectedText: Boolean = false,
    val contextClipboard: Boolean = false,
)
