package com.github.shekohex.whisperpp.providers.realtime

/** Re-cuts a PCM byte stream into frames of exactly [frameBytes] bytes. */
internal class Rechunker(private val frameBytes: Int) {
    private var buffer = ByteArray(0)

    /** Appends [bytes] and returns every complete frame now available, in order. */
    fun push(bytes: ByteArray): List<ByteArray> {
        buffer += bytes
        val frames = ArrayList<ByteArray>()
        var offset = 0
        while (buffer.size - offset >= frameBytes) {
            frames += buffer.copyOfRange(offset, offset + frameBytes)
            offset += frameBytes
        }
        if (offset > 0) buffer = buffer.copyOfRange(offset, buffer.size)
        return frames
    }

    /** The remainder as-is, or null when empty. */
    fun flush(): ByteArray? {
        if (buffer.isEmpty()) return null
        return buffer.also { buffer = ByteArray(0) }
    }

    /** The remainder zero-padded to a whole frame, or null when empty. */
    fun flushPadded(): ByteArray? = flush()?.copyOf(frameBytes)
}
