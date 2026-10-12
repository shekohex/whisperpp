package com.github.shekohex.whisperpp.analytics

/** Models and latencies of one completed session; ids only, never text. */
data class AnalyticsModelSample(
    val providerId: String,
    val modelId: String,
    val transcriptionLatencyMs: Long,
    val enhancementProviderId: String? = null,
    val enhancementModelId: String? = null,
    val enhancementLatencyMs: Long? = null,
)
