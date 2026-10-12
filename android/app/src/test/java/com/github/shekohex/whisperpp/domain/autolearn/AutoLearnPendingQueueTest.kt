package com.github.shekohex.whisperpp.domain.autolearn

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnPendingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AutoLearnPendingQueueTest {

    private fun candidate(i: Int) = DetectedCorrectionCandidate(id = "c$i", originalText = "teh $i", correctedText = "the $i")

    @Test
    fun enqueue_skipsDuplicatePairsCaseInsensitively() = runBlocking {
        val queue = AutoLearnPendingQueue(InMemoryPreferencesStore())
        assertEquals(1, queue.enqueue(listOf(candidate(1))))
        val duplicate = DetectedCorrectionCandidate(id = "x", originalText = " TEH 1 ", correctedText = "The 1")
        assertEquals(0, queue.enqueue(listOf(duplicate)))
        assertEquals(1, queue.pendingCount.first())
    }

    @Test
    fun claimBatch_releaseAndRemove() = runBlocking {
        val queue = AutoLearnPendingQueue(InMemoryPreferencesStore())
        queue.enqueue((1..5).map(::candidate))

        val batch = queue.claimBatch(max = 2)
        assertEquals(listOf("c1", "c2"), batch.map { it.id })
        assertTrue(batch.all { it.claimed })
        assertEquals(3, queue.pendingCount.first())
        assertEquals(5, queue.outstandingCount.first())
        assertEquals(listOf("c3", "c4"), queue.claimBatch(max = 2).map { it.id })

        queue.release(listOf("c1", "c2"))
        assertEquals(3, queue.pendingCount.first())
        queue.remove(listOf("c3", "c4"))
        assertEquals(3, queue.outstandingCount.first())

        queue.claimBatch()
        queue.releaseAll()
        assertEquals(3, queue.pendingCount.first())
    }

    @Test
    fun claimBatch_defaultsToTwentyFive() = runBlocking {
        val queue = AutoLearnPendingQueue(InMemoryPreferencesStore())
        queue.enqueue((1..30).map(::candidate))
        assertEquals(AutoLearnPendingQueue.BATCH_SIZE, queue.claimBatch().size)
    }

    @Test
    fun enqueue_capsPendingAndDropsOldest() = runBlocking {
        val queue = AutoLearnPendingQueue(InMemoryPreferencesStore())
        queue.enqueue((1..150).map(::candidate))
        queue.enqueue((151..260).map(::candidate))
        assertEquals(AutoLearnPendingQueue.MAX_PENDING, queue.outstandingCount.first())
        assertEquals("c61", queue.claimBatch(max = 1).single().id)
    }

    @Test
    fun queue_survivesProcessRestart() {
        val dir = Files.createTempDirectory("autolearn").toFile()
        val file = File(dir, "autolearn_queue.preferences_pb")
        try {
            val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            runBlocking {
                val queue = AutoLearnPendingQueue(PreferenceDataStoreFactory.create(scope = firstScope) { file })
                queue.enqueue((1..3).map(::candidate))
                queue.claimBatch(max = 1)
            }
            firstScope.cancel()

            val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            runBlocking {
                val queue = AutoLearnPendingQueue(PreferenceDataStoreFactory.create(scope = secondScope) { file })
                assertEquals(3, queue.outstandingCount.first())
                assertEquals(2, queue.pendingCount.first())
                queue.releaseAll()
                assertEquals(3, queue.pendingCount.first())
            }
            secondScope.cancel()
        } finally {
            dir.deleteRecursively()
        }
    }
}
