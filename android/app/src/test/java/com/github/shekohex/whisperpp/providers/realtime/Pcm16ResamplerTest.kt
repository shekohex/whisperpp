package com.github.shekohex.whisperpp.providers.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pcm16ResamplerTest {
    private fun pcm(vararg samples: Int): ByteArray = ByteArray(samples.size * 2).also { out ->
        samples.forEachIndexed { i, s ->
            out[i * 2] = s.toByte()
            out[i * 2 + 1] = (s shr 8).toByte()
        }
    }

    private fun ByteArray.samples(): List<Int> = List(size / 2) { i -> (this[i * 2].toInt() and 0xff) or (this[i * 2 + 1].toInt() shl 8) }

    @Test
    fun sameRate_passesThrough_andFlushIsEmpty() {
        val resampler = Pcm16Resampler(24_000, 24_000)
        val chunk = pcm(1, 2, 3)

        assertEquals(chunk.toList(), resampler.process(chunk).toList())
        assertTrue(resampler.flush().isEmpty())
    }

    @Test
    fun flush_drainsTheHeldBackSamples_repeatingTheLastInput() {
        val resampler = Pcm16Resampler(16_000, 24_000)
        val emitted = resampler.process(pcm(0, 300, 600, 900)).samples()
        val tail = resampler.flush().samples()

        // 4 input samples at 16 kHz = 6 at 24 kHz; process holds the last ones back.
        assertEquals(6, emitted.size + tail.size)
        assertTrue(tail.isNotEmpty())
        assertTrue(tail.all { it == 900 })
        assertTrue(resampler.flush().isEmpty())
    }

    @Test
    fun flush_beforeAnyInput_isEmpty() {
        assertTrue(Pcm16Resampler(16_000, 24_000).flush().isEmpty())
    }
}
