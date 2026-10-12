package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry

/** Runs the current enhancement setup again over an entry's raw text. */
fun interface HistoryReEnhancer {
    suspend fun reEnhance(entry: TranscriptHistoryEntry): Result

    sealed interface Result {
        data class Enhanced(val text: String, val modelId: String) : Result

        /** [message] is user-facing and never contains the transcript. */
        data class Failed(val message: String) : Result
    }
}
