package com.github.shekohex.whisperpp.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WaveformHistoryTest {
    private val interval = WaveformHistory.DEFAULT_SAMPLE_INTERVAL_NANOS

    @Test
    fun emptyHistoryReadsAsSilence() {
        val history = WaveformHistory(capacity = 4)

        for (age in 0 until 4) assertEquals(0f, history[age], 0f)
    }

    @Test
    fun push_newestIsAgeZero() {
        val history = WaveformHistory(capacity = 4)

        history.push(0.1f)
        history.push(0.2f)
        history.push(0.3f)

        assertEquals(0.3f, history[0], 0f)
        assertEquals(0.2f, history[1], 0f)
        assertEquals(0.1f, history[2], 0f)
        assertEquals(0f, history[3], 0f)
    }

    @Test
    fun push_overwritesOldestWhenFull() {
        val history = WaveformHistory(capacity = 3)

        for (value in listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)) history.push(value)

        assertEquals(0.5f, history[0], 0f)
        assertEquals(0.4f, history[1], 0f)
        assertEquals(0.3f, history[2], 0f)
    }

    @Test
    fun push_clampsAndSanitizesLevels() {
        val history = WaveformHistory(capacity = 3)

        history.push(1.7f)
        history.push(-0.4f)
        history.push(Float.NaN)

        assertEquals(0f, history[0], 0f)
        assertEquals(0f, history[1], 0f)
        assertEquals(1f, history[2], 0f)
    }

    @Test
    fun advance_belowIntervalPushesNothingAndTracksFraction() {
        val history = WaveformHistory(capacity = 4)

        val pushed = history.advance(interval / 4, 0.8f)

        assertEquals(0, pushed)
        assertEquals(0f, history[0], 0f)
        assertEquals(0.25f, history.fraction, 1e-6f)
    }

    @Test
    fun advance_accumulatesAcrossFrames() {
        val history = WaveformHistory(capacity = 4)

        assertEquals(0, history.advance(interval * 3 / 5, 0.5f))
        assertEquals(1, history.advance(interval * 3 / 5, 0.5f))

        assertEquals(0.5f, history[0], 1e-6f)
        assertEquals(0.2f, history.fraction, 1e-6f)
    }

    @Test
    fun advance_slowFrameInterpolatesFromPreviousLevel() {
        val history = WaveformHistory(capacity = 8)
        history.advance(interval, 0.2f)

        val pushed = history.advance(interval * 4, 1.0f)

        assertEquals(4, pushed)
        assertEquals(1.0f, history[0], 1e-6f)
        assertEquals(0.8f, history[1], 1e-6f)
        assertEquals(0.6f, history[2], 1e-6f)
        assertEquals(0.4f, history[3], 1e-6f)
        assertEquals(0.2f, history[4], 1e-6f)
    }

    @Test
    fun advance_longStallIsCappedAtCapacity() {
        val history = WaveformHistory(capacity = 4)

        val pushed = history.advance(interval * 1_000, 1f)

        assertEquals(4, pushed)
        assertEquals(1f, history[0], 0f)
        assertEquals(0f, history.fraction, 0f)
    }

    @Test
    fun advance_negativeElapsedIsIgnored() {
        val history = WaveformHistory(capacity = 4)

        assertEquals(0, history.advance(-interval, 1f))
        assertEquals(0f, history.fraction, 0f)
    }

    @Test
    fun clear_resetsSamplesAndAccumulator() {
        val history = WaveformHistory(capacity = 4)
        history.advance(interval + interval / 2, 0.9f)

        history.clear()

        assertEquals(0f, history[0], 0f)
        assertEquals(0f, history.fraction, 0f)
        history.advance(interval, 0.4f)
        assertEquals(0.4f, history[0], 1e-6f)
    }

    @Test
    fun get_rejectsOutOfRangeAge() {
        val history = WaveformHistory(capacity = 4)

        assertThrows(IllegalArgumentException::class.java) { history[4] }
        assertThrows(IllegalArgumentException::class.java) { history[-1] }
    }
}
