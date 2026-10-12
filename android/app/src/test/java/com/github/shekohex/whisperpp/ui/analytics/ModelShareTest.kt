package com.github.shekohex.whisperpp.ui.analytics

import com.github.shekohex.whisperpp.analytics.ModelUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelShareTest {
    @Test
    fun countsTranscriptionsAndEnhancements_biggestFirst() {
        val shares = ModelShare.from(
            listOf(
                ModelUsage("openai", "whisper-1", sessions = 30),
                ModelUsage("openai", "gpt", enhancementSessions = 60),
                ModelUsage("groq", "idle"),
            ),
        )

        assertEquals(listOf("openai/gpt", "openai/whisper-1"), shares.map { it.key })
        assertEquals(listOf(66, 33), shares.map { it.percent })
    }

    @Test
    fun tailFoldsIntoOther() {
        val usages = (1..7).map { ModelUsage("p", "m$it", sessions = 10 * it) }

        val shares = ModelShare.from(usages, maxRows = 5)

        assertEquals(5, shares.size)
        assertNull(shares.last().key)
        assertEquals(10L + 20 + 30, shares.last().uses)
    }

    @Test
    fun noUses_isEmpty() {
        assertTrue(ModelShare.from(listOf(ModelUsage("p", "m"))).isEmpty())
    }
}
