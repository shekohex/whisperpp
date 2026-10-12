package com.github.shekohex.whisperpp.ime

/** The clipboard text shown in the command-mode confirm step: a short snippet, its size and a size warning. */
data class CommandClipboardPreview(
    val snippet: String,
    val charCount: Int,
    val isLarge: Boolean,
) {
    companion object {
        const val SNIPPET_LIMIT = 180
        const val LARGE_THRESHOLD = 15_000

        fun of(text: String): CommandClipboardPreview {
            val count = text.length
            val snippet = if (count <= SNIPPET_LIMIT) text else text.substring(0, SNIPPET_LIMIT) + "…"
            return CommandClipboardPreview(snippet, count, isLarge = count >= LARGE_THRESHOLD)
        }
    }
}
