package com.github.shekohex.whisperpp.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class AmplitudeMeterTest {

    @Test
    fun silence_staysAtZero() {
        val meter = AmplitudeMeter()

        repeat(10) { meter.process(pcm(ShortArray(320)), 640) }

        assertEquals(0f, meter.level.value, 0f)
        assertEquals(0f, meter.peak.value, 0f)
    }

    @Test
    fun fullScaleSine_convergesNearOne() {
        val meter = AmplitudeMeter()
        val chunk = pcm(sine(amplitude = 32767.0))

        repeat(30) { meter.process(chunk, chunk.size) }

        // RMS of a full-scale sine is -3 dBFS → (50 - 3) / 50 = 0.94.
        assertEquals(0.94f, meter.level.value, 0.01f)
        assertEquals(1f, meter.peak.value, 0.01f)
    }

    @Test
    fun attack_isFasterThanRelease() {
        val meter = AmplitudeMeter()
        val loud = pcm(sine(amplitude = 32767.0))
        val quiet = pcm(ShortArray(320))

        meter.process(loud, loud.size)
        val afterOneLoudChunk = meter.level.value
        repeat(30) { meter.process(loud, loud.size) }
        val settled = meter.level.value
        meter.process(quiet, quiet.size)
        val dropAfterOneQuietChunk = settled - meter.level.value

        assertEquals(0.6f * 0.94f, afterOneLoudChunk, 0.01f)
        assertEquals(0.15f * settled, dropAfterOneQuietChunk, 0.01f)
        assertTrue(afterOneLoudChunk > dropAfterOneQuietChunk)
    }

    @Test
    fun dbfsMapping_matchesReferencePoints() {
        val meter = AmplitudeMeter()

        assertEquals(0f, AmplitudeMeter.toDbfs(32768.0), 0.001f)
        assertEquals(-6.02f, AmplitudeMeter.toDbfs(16384.0), 0.01f)
        assertEquals(Float.NEGATIVE_INFINITY, AmplitudeMeter.toDbfs(0.0), 0f)

        assertEquals(1f, meter.normalize(0f), 0f)
        assertEquals(0.5f, meter.normalize(-25f), 0.0001f)
        assertEquals(0f, meter.normalize(-50f), 0f)
        assertEquals(0f, meter.normalize(-80f), 0f)
        assertEquals(0f, meter.normalize(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun reset_clearsLevelAndPeak() {
        val meter = AmplitudeMeter()
        val chunk = pcm(sine(amplitude = 20000.0))
        meter.process(chunk, chunk.size)

        meter.reset()

        assertEquals(0f, meter.level.value, 0f)
        assertEquals(0f, meter.peak.value, 0f)
    }

    @Test
    fun process_ignoresTrailingOddByteAndEmptyInput() {
        val meter = AmplitudeMeter()

        meter.process(ByteArray(1), 1)
        meter.process(ByteArray(0), 0)

        assertEquals(0f, meter.level.value, 0f)
    }

    private fun sine(amplitude: Double, samples: Int = 320, periodSamples: Int = 32): ShortArray {
        return ShortArray(samples) { i -> (amplitude * sin(2 * PI * i / periodSamples)).roundToInt().toShort() }
    }

    private fun pcm(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, sample ->
            bytes[2 * i] = (sample.toInt() and 0xFF).toByte()
            bytes[2 * i + 1] = (sample.toInt() shr 8).toByte()
        }
        return bytes
    }
}
