package com.github.shekohex.whisperpp.core.audio

/**
 * Measures active recording time (paused spans excluded) using a monotonic clock.
 * Not thread-safe: owned by a single recording session.
 */
class RecordingClock(private val now: () -> Long) {
    private var accumulatedMs = 0L
    private var runningSinceMs: Long? = null

    fun start() {
        accumulatedMs = 0L
        runningSinceMs = now()
    }

    fun pause() {
        val since = runningSinceMs ?: return
        accumulatedMs += now() - since
        runningSinceMs = null
    }

    fun resume() {
        if (runningSinceMs == null) runningSinceMs = now()
    }

    fun elapsedMs(): Long {
        val since = runningSinceMs ?: return accumulatedMs
        return accumulatedMs + (now() - since)
    }

    fun stop(): Long {
        pause()
        return accumulatedMs
    }
}
