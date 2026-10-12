package com.github.shekohex.whisperpp.data

/** Transcript history preferences (§4.8). */
data class HistorySettings(
    val enabled: Boolean = true,
    /** Keep the audio of finished takes too. Unresolved (failed) takes always keep theirs. */
    val keepAudio: Boolean = DEFAULT_KEEP_AUDIO,
    /** Entries and recordings older than this many days are deleted; 0 = keep forever. */
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
) {
    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
        const val DEFAULT_KEEP_AUDIO = true

        /** The retention windows offered in the UI; 0 = forever. */
        val RETENTION_CHOICES: List<Int> = listOf(7, 15, 30, 0)
    }
}
