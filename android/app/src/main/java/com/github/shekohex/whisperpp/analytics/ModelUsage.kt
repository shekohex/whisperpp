package com.github.shekohex.whisperpp.analytics

/**
 * Usage of one provider/model. A model can serve transcription ([sessions], [words],
 * [transcriptionLatencyMs]) and/or enhancement ([enhancementSessions], [enhancementLatencyMs]).
 */
data class ModelUsage(
    val providerId: String,
    val modelId: String,
    val sessions: Int = 0,
    val words: Long = 0,
    /** Sum of stop → final transcript latencies. */
    val transcriptionLatencyMs: Long = 0,
    val enhancementSessions: Int = 0,
    /** Sum of enhancement call durations. */
    val enhancementLatencyMs: Long = 0,
) {
    val key: String get() = "$providerId/$modelId"

    val averageTranscriptionLatencyMs: Long? get() = if (sessions > 0) transcriptionLatencyMs / sessions else null

    val averageEnhancementLatencyMs: Long? get() =
        if (enhancementSessions > 0) enhancementLatencyMs / enhancementSessions else null

    operator fun plus(other: ModelUsage): ModelUsage = copy(
        sessions = sessions + other.sessions,
        words = words + other.words,
        transcriptionLatencyMs = transcriptionLatencyMs + other.transcriptionLatencyMs,
        enhancementSessions = enhancementSessions + other.enhancementSessions,
        enhancementLatencyMs = enhancementLatencyMs + other.enhancementLatencyMs,
    )

    companion object {
        /** Sums entries with the same provider/model; most sessions (then enhancements) first. */
        fun merge(usages: List<ModelUsage>): List<ModelUsage> = usages
            .groupBy { it.providerId to it.modelId }
            .map { (_, group) -> group.reduce(ModelUsage::plus) }
            .sortedWith(compareByDescending<ModelUsage> { it.sessions }.thenByDescending { it.enhancementSessions }.thenBy { it.key })

        /** The usage contributed by one completed session. */
        fun fromSample(sample: AnalyticsModelSample, words: Int): List<ModelUsage> = buildList {
            if (sample.providerId.isNotBlank() || sample.modelId.isNotBlank()) {
                add(
                    ModelUsage(
                        providerId = sample.providerId,
                        modelId = sample.modelId,
                        sessions = 1,
                        words = words.toLong(),
                        transcriptionLatencyMs = sample.transcriptionLatencyMs.coerceAtLeast(0L),
                    )
                )
            }
            val enhancementModel = sample.enhancementModelId
            val enhancementLatency = sample.enhancementLatencyMs
            if (enhancementModel != null && enhancementLatency != null) {
                add(
                    ModelUsage(
                        providerId = sample.enhancementProviderId.orEmpty(),
                        modelId = enhancementModel,
                        enhancementSessions = 1,
                        enhancementLatencyMs = enhancementLatency.coerceAtLeast(0L),
                    )
                )
            }
        }
    }
}
