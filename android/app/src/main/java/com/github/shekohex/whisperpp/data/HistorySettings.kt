package com.github.shekohex.whisperpp.data

/** Transcript history preferences (§4.8). */
data class HistorySettings(
    val enabled: Boolean = true,
    val keepAudio: Boolean = false,
    /** 0 = keep forever. */
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
) {
    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
    }
}
