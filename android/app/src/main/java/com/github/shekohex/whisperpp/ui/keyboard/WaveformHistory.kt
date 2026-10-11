package com.github.shekohex.whisperpp.ui.keyboard

/**
 * Fixed-size ring buffer of 0..1 loudness samples, filled at a fixed [sampleIntervalNanos] regardless of the
 * frame rate that drives [advance]. Allocation-free after construction. Not thread-safe (UI thread only).
 */
class WaveformHistory(
    val capacity: Int,
    private val sampleIntervalNanos: Long = DEFAULT_SAMPLE_INTERVAL_NANOS,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
        require(sampleIntervalNanos > 0) { "sampleIntervalNanos must be positive" }
    }

    private val samples = FloatArray(capacity)
    private var next = 0
    private var accumulatedNanos = 0L
    private var lastLevel = 0f

    /** Progress (0..1) towards the next sample; lets the renderer scroll smoothly between 50 Hz steps. */
    val fraction: Float
        get() = accumulatedNanos.toFloat() / sampleIntervalNanos

    /**
     * Accounts for [elapsedNanos] since the previous call and pushes one sample per full interval. When a frame
     * covers several intervals the pushed samples are interpolated from the previous level to [level].
     *
     * @return the number of samples pushed.
     */
    fun advance(elapsedNanos: Long, level: Float): Int {
        val target = sanitize(level)
        accumulatedNanos += elapsedNanos.coerceAtLeast(0L)
        val due = accumulatedNanos / sampleIntervalNanos
        accumulatedNanos -= due * sampleIntervalNanos
        if (due == 0L) return 0

        // Samples older than the buffer would be overwritten straight away, so skip them.
        val count = due.coerceAtMost(capacity.toLong()).toInt()
        val previous = lastLevel
        for (step in 1..count) {
            push(previous + (target - previous) * step / count)
        }
        lastLevel = target
        return count
    }

    /** Appends a sample directly, bypassing the interval accounting. */
    fun push(level: Float) {
        samples[next] = sanitize(level)
        next = (next + 1) % capacity
    }

    /** Sample [age] steps back in time: 0 is the newest, `capacity - 1` the oldest. */
    operator fun get(age: Int): Float {
        require(age in 0 until capacity) { "age $age out of range" }
        return samples[(next - 1 - age + 2 * capacity) % capacity]
    }

    fun clear() {
        samples.fill(0f)
        next = 0
        accumulatedNanos = 0L
        lastLevel = 0f
    }

    private fun sanitize(level: Float): Float = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)

    companion object {
        /** 50 Hz, matching the 20 ms chunks that feed `AmplitudeMeter`. */
        const val DEFAULT_SAMPLE_INTERVAL_NANOS = 20_000_000L
    }
}
