package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.analytics.AnalyticsModelSample
import com.github.shekohex.whisperpp.domain.dictation.CompletionStats
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationAnalyticsReporterTest {
    @Test
    fun completionStats_mapToAnalyticsSample() {
        val stats = CompletionStats(
            providerId = "p1",
            sttModelId = "m1",
            languageCode = "en",
            transcriptionLatencyMs = 120,
            enhancementProviderId = "p2",
            enhancementModelId = "m2",
            enhancementLatencyMs = 340,
        )

        assertEquals(
            AnalyticsModelSample(
                providerId = "p1",
                modelId = "m1",
                transcriptionLatencyMs = 120,
                enhancementProviderId = "p2",
                enhancementModelId = "m2",
                enhancementLatencyMs = 340,
            ),
            stats.toAnalyticsSample(),
        )
    }

    @Test
    fun statsWithoutEnhancement_keepEnhancementFieldsNull() {
        val sample = CompletionStats(providerId = "p", sttModelId = "m").toAnalyticsSample()

        assertEquals(null, sample.enhancementProviderId)
        assertEquals(null, sample.enhancementLatencyMs)
    }
}
