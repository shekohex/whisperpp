package com.github.shekohex.whisperpp.data.autolearn

import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewSchedule

/**
 * Auto-Learn preferences (§4.11). [providerId]/[modelId] pick the review model; when blank or
 * invalid the enhancement selection ([fallbackProviderId]/[fallbackModelId]) is used.
 */
data class AutoLearnSettings(
    val enabled: Boolean = false,
    val providerId: String = "",
    val modelId: String = "",
    val fallbackProviderId: String = "",
    val fallbackModelId: String = "",
    val reviewSchedule: AutoLearnReviewSchedule = AutoLearnReviewSchedule.IMMEDIATELY,
    /** Epoch millis of the last completed review run; 0 when never. */
    val lastReviewAt: Long = 0L,
)
