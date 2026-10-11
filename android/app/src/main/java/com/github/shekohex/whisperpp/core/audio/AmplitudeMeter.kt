package com.github.shekohex.whisperpp.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Converts PCM16 chunks into a smoothed 0..1 loudness level (RMS in dBFS) plus a per-chunk peak.
 *
 * [process] is called from the capture thread; [level] and [peak] can be read from any thread.
 */
class AmplitudeMeter(
    private val floorDb: Float = DEFAULT_FLOOR_DB,
    private val attack: Float = DEFAULT_ATTACK,
    private val release: Float = DEFAULT_RELEASE,
) {
    private val lock = Any()
    private var smoothed = 0f

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _peak = MutableStateFlow(0f)
    val peak: StateFlow<Float> = _peak.asStateFlow()

    fun process(pcm16: ByteArray, length: Int) {
        val sampleCount = length.coerceAtMost(pcm16.size) / 2
        if (sampleCount == 0) return

        var sumSquares = 0.0
        var maxAbs = 0
        for (i in 0 until sampleCount) {
            val lo = pcm16[2 * i].toInt() and 0xFF
            val hi = pcm16[2 * i + 1].toInt()
            val sample = (hi shl 8) or lo
            sumSquares += sample.toDouble() * sample
            val abs = if (sample < 0) -sample else sample
            if (abs > maxAbs) maxAbs = abs
        }
        val rms = sqrt(sumSquares / sampleCount)
        val target = normalize(toDbfs(rms))
        val peakLevel = normalize(toDbfs(maxAbs.toDouble()))

        synchronized(lock) {
            val coefficient = if (target > smoothed) attack else release
            smoothed += coefficient * (target - smoothed)
            _level.value = smoothed
            _peak.value = peakLevel
        }
    }

    fun reset() {
        synchronized(lock) {
            smoothed = 0f
            _level.value = 0f
            _peak.value = 0f
        }
    }

    /** Maps a dBFS value to 0..1, with [floorDb] → 0 and 0 dBFS → 1. */
    fun normalize(db: Float): Float {
        if (db.isNaN()) return 0f
        return ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
    }

    companion object {
        const val FULL_SCALE = 32768.0
        const val DEFAULT_FLOOR_DB = -50f
        const val DEFAULT_ATTACK = 0.6f
        const val DEFAULT_RELEASE = 0.15f

        fun toDbfs(amplitude: Double): Float {
            if (amplitude <= 0.0) return Float.NEGATIVE_INFINITY
            return (20.0 * log10(amplitude / FULL_SCALE)).toFloat()
        }
    }
}
