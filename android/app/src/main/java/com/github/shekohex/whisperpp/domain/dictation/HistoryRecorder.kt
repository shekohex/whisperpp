package com.github.shekohex.whisperpp.domain.dictation

import java.io.File

/** Transcript history hook of [DictationSession] (§4.8). Implementations respect the history settings. */
interface HistoryRecorder {
    data class Entry(
        val rawText: String,
        /** Null when no enhanced text replaced the raw one. */
        val enhancedText: String?,
        val stats: CompletionStats,
        val appPackage: String?,
        val durationMs: Long,
        val audioPath: String?,
    )

    /**
     * Called with the take's WAV before the session deletes it. Returns where a kept copy lives, or
     * null when audio is not kept. The file may be moved away.
     */
    suspend fun retainAudio(file: File): String?

    /** Stores a completed run; must not block the caller. */
    fun record(entry: Entry)

    /** A path from [retainAudio] whose run never completed. */
    fun discardAudio(path: String)
}
