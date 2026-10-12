package com.github.shekohex.whisperpp.providers.realtime

/**
 * Linear-interpolating resampler for 16-bit little-endian mono PCM. Keeps the last sample and the
 * output phase across chunks, so chunk boundaries do not click. Not thread-safe.
 */
internal class Pcm16Resampler(private val inRate: Int, private val outRate: Int) {
    private var carry: Int? = null

    // Next output position in input samples × outRate, relative to the first sample of the window.
    private var position = 0L

    fun process(pcm: ByteArray): ByteArray {
        if (inRate == outRate) return pcm
        val count = pcm.size / 2
        if (count == 0) return EMPTY
        val previous = carry
        val offset = if (previous != null) 1 else 0
        val size = count + offset
        val out = ByteArray(((size.toLong() * outRate / inRate + 2) * 2).toInt())
        var written = 0
        while (true) {
            val index = (position / outRate).toInt()
            if (index + 1 >= size) break
            val a = sampleAt(pcm, previous, offset, index)
            val b = sampleAt(pcm, previous, offset, index + 1)
            val value = a + ((b - a).toLong() * (position % outRate) / outRate).toInt()
            out[written] = value.toByte()
            out[written + 1] = (value shr 8).toByte()
            written += 2
            position += inRate
        }
        carry = sampleAt(pcm, previous, offset, size - 1)
        position -= (size - 1).toLong() * outRate
        return out.copyOf(written)
    }

    private fun sampleAt(pcm: ByteArray, previous: Int?, offset: Int, index: Int): Int {
        if (index < offset) return previous ?: 0
        val i = (index - offset) * 2
        return (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
