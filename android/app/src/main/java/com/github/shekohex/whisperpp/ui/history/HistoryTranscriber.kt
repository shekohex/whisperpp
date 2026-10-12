package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import java.io.File

/** Transcribes a saved recording again with today's speech-to-text settings (no keyboard involved). */
interface HistoryTranscriber {
    /** Batch transcription of [audio] with the provider, model and language the entry's mode resolves to now. */
    suspend fun transcribe(audio: File, entry: TranscriptHistoryEntry): Result

    /** Whether the entry's mode would enhance a new dictation today. */
    suspend fun enhancementEnabled(entry: TranscriptHistoryEntry): Boolean

    sealed interface Result {
        /** [text] is post-processed like a live dictation. */
        data class Text(val text: String, val providerId: String, val sttModelId: String, val languageCode: String?) : Result

        data object Empty : Result

        /** [message] is user-facing and never contains the transcript. */
        data class Failed(val message: String, val reason: HistoryFailureReason) : Result
    }
}
