package com.github.shekohex.whisperpp.core.audio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded hand-off of PCM chunks from the capture thread to a consumer. Never blocks the producer;
 * chunks that do not fit are dropped and counted (F-05).
 */
class PcmChunkSink(capacity: Int = DEFAULT_CAPACITY) {
    private val channel = Channel<ByteArray>(capacity)
    private val dropped = AtomicInteger(0)

    val chunks: ReceiveChannel<ByteArray>
        get() = channel

    val droppedChunks: Int
        get() = dropped.get()

    /** Returns false (and counts a drop) when the consumer is behind or the sink is closed. */
    fun offer(chunk: ByteArray): Boolean {
        val accepted = channel.trySend(chunk).isSuccess
        if (!accepted) dropped.incrementAndGet()
        return accepted
    }

    fun close() {
        channel.close()
    }

    companion object {
        const val DEFAULT_CAPACITY = 8
    }
}
