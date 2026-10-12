package com.github.shekohex.whisperpp.data.modes

import com.github.shekohex.whisperpp.data.PromptLibrary
import com.github.shekohex.whisperpp.domain.modes.Mode

/** Built-in modes (§4.10) using the [PromptLibrary] prompts; ordinary, editable modes once stored. */
object ModeSeeds {
    const val DICTATION_ID = "builtin-mode-dictation"
    const val CHAT_ID = "builtin-mode-chat"
    const val EMAIL_ID = "builtin-mode-email"
    const val REWRITE_ID = "builtin-mode-rewrite"
    const val ASSISTANT_ID = "builtin-mode-assistant"

    /** Inherits every global setting: with only this mode the app behaves as before modes existed. */
    val DICTATION = Mode(id = DICTATION_ID, name = "Dictation", icon = Mode.Icon.MIC, isDefault = true)

    val ALL: List<Mode> = listOf(
        DICTATION,
        Mode(
            id = CHAT_ID,
            name = "Chat",
            icon = Mode.Icon.CHAT,
            enhancementEnabled = true,
            promptId = PromptLibrary.CHAT_ID,
        ),
        Mode(
            id = EMAIL_ID,
            name = "Email",
            icon = Mode.Icon.MAIL,
            enhancementEnabled = true,
            promptId = PromptLibrary.EMAIL_ID,
        ),
        Mode(
            id = REWRITE_ID,
            name = "Rewrite",
            icon = Mode.Icon.EDIT,
            enhancementEnabled = true,
            promptId = PromptLibrary.REWRITE_ID,
        ),
        Mode(
            id = ASSISTANT_ID,
            name = "Assistant",
            icon = Mode.Icon.ASSISTANT,
            enhancementEnabled = true,
            promptId = PromptLibrary.ASSISTANT_ID,
            contextSources = Mode.ContextSources(selectedText = true),
        ),
    )
}
