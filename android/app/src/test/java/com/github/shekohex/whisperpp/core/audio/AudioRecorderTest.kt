package com.github.shekohex.whisperpp.core.audio

import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure pieces of [AudioRecorder]: dropped-chunk accounting and duration. AudioRecord itself is not run on the JVM. */
class AudioRecorderTest {

    @Test
    fun pcmSink_countsChunksThatDoNotFit() {
        val sink = PcmChunkSink(capacity = 2)

        val accepted = (1..5).map { sink.offer(ByteArray(it)) }

        assertEquals(listOf(true, true, false, false, false), accepted)
        assertEquals(3, sink.droppedChunks)
    }

    @Test
    fun pcmSink_deliversInOrderAndCountsDropsAfterClose() = runTest {
        val sink = PcmChunkSink(capacity = 4)
        sink.offer(byteArrayOf(1))
        sink.offer(byteArrayOf(2))
        sink.close()

        assertFalse(sink.offer(byteArrayOf(3)))
        val received = mutableListOf<Byte>()
        sink.chunks.consumeEach { received += it[0] }

        assertEquals(listOf<Byte>(1, 2), received)
        assertEquals(1, sink.droppedChunks)
    }

    @Test
    fun pcmSink_noDropsWhenConsumerKeepsUp() {
        val sink = PcmChunkSink(capacity = 1)

        repeat(10) {
            assertTrue(sink.offer(ByteArray(2)))
            sink.chunks.tryReceive()
        }

        assertEquals(0, sink.droppedChunks)
    }

    @Test
    fun recordingClock_excludesPausedTime() {
        var now = 1_000L
        val clock = RecordingClock { now }

        clock.start()
        now += 400
        clock.pause()
        now += 10_000
        clock.resume()
        now += 600

        assertEquals(1_000L, clock.elapsedMs())
        assertEquals(1_000L, clock.stop())
    }

    @Test
    fun recordingClock_stopIsStableAndPauseIdempotent() {
        var now = 0L
        val clock = RecordingClock { now }

        clock.start()
        now += 250
        clock.pause()
        clock.pause()
        now += 1_000
        val first = clock.stop()
        now += 1_000

        assertEquals(250L, first)
        assertEquals(250L, clock.stop())
    }

    @Test
    fun recordingClock_startResetsPreviousSession() {
        var now = 0L
        val clock = RecordingClock { now }
        clock.start()
        now += 500
        clock.stop()

        clock.start()
        now += 100

        assertEquals(100L, clock.elapsedMs())
    }
}
