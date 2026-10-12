package com.github.shekohex.whisperpp.data.history

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SerialWorker"

/**
 * Runs submitted jobs one at a time in submission order on [scope]. Idle when the queue is empty
 * (no coroutine is parked), so it never keeps a test or process scope alive. A failing job is logged
 * (type only) and does not stop the ones behind it.
 */
internal class SerialWorker(private val scope: CoroutineScope) {
    private val queue = ConcurrentLinkedQueue<suspend () -> Unit>()
    private val draining = AtomicBoolean(false)

    fun submit(job: suspend () -> Unit) {
        queue.add(job)
        startDrain()
    }

    private fun startDrain() {
        if (!draining.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val job = queue.poll() ?: break
                    run(job)
                }
            } finally {
                draining.set(false)
                // A job submitted between the last poll and the flag reset would otherwise wait.
                if (queue.isNotEmpty()) startDrain()
            }
        }
    }

    private suspend fun run(job: suspend () -> Unit) {
        try {
            job()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "History job failed: ${e.javaClass.simpleName}")
        }
    }
}
