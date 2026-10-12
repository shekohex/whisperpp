package com.github.shekohex.whisperpp.domain.dictation

/**
 * Which models produced a completed run and how long they took; feeds analytics and history.
 * Never carries text.
 */
data class CompletionStats(
    val providerId: String = "",
    val sttModelId: String = "",
    val languageCode: String? = null,
    /** Stop → final transcript (stream commit or batch call). */
    val transcriptionLatencyMs: Long = 0,
    val enhancementProviderId: String? = null,
    val enhancementModelId: String? = null,
    /** Duration of the enhancement call; null when no enhancement ran. */
    val enhancementLatencyMs: Long? = null,
)
