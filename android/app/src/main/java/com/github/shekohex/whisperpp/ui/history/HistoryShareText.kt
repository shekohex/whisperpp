package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry

/** What "share selected" sends: each entry's final text, oldest first, blank-line separated. */
fun buildShareText(entries: Collection<TranscriptHistoryEntry>): String =
    entries.sortedBy { it.createdAt }.joinToString(separator = "\n\n") { it.finalText.trim() }
