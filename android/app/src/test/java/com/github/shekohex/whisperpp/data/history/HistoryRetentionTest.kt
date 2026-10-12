package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Retention (7 / 15 / 30 days / forever), orphan cleanup and crash recovery of the history. */
class HistoryRetentionTest {
    private val now = 1_800_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)
    private lateinit var root: File
    private lateinit var audioDir: File
    private lateinit var audio: HistoryAudioStore
    private lateinit var dao: FakeTranscriptHistoryDao
    private lateinit var repository: TranscriptHistoryRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("history-retention").toFile()
        audioDir = File(root, HistoryAudioStore.DIR_NAME)
        audio = HistoryAudioStore(audioDir, clock = { now })
        dao = FakeTranscriptHistoryDao()
        repository = TranscriptHistoryRepository(dao, audio, clock = { now })
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun retentionWindows_deleteOnlyOlderEntries_withTheirAudio() = runBlocking {
        listOf(7, 15, 30).forEach { days ->
            val local = TranscriptHistoryRepository(FakeTranscriptHistoryDao(), audio, clock = { now })
            val oldAudio = audioFile("old-$days.wav")
            val freshAudio = audioFile("fresh-$days.wav")
            local.insert(entry(createdAt = now - (days + 1) * day, raw = "old", audioPath = oldAudio.absolutePath))
            local.insert(entry(createdAt = now - (days - 1) * day, raw = "fresh", audioPath = freshAudio.absolutePath))

            assertEquals("$days days", 1, local.pruneExpired(retentionDays = days))

            assertEquals("$days days", listOf("fresh"), local.observeRecent().first().map { it.rawText })
            assertFalse("$days days", oldAudio.exists())
            assertTrue("$days days", freshAudio.exists())
        }
    }

    @Test
    fun forever_keepsEverything_includingFailedTakes() = runBlocking {
        val file = audioFile("ancient.wav")
        repository.insert(entry(createdAt = 1L, raw = "", audioPath = file.absolutePath, status = HistoryStatus.FAILED))

        assertEquals(0, repository.pruneExpired(retentionDays = 0))

        assertEquals(1, repository.count.first())
        assertTrue(file.exists())
    }

    @Test
    fun expiredFailedTakes_areDeletedToo() = runBlocking {
        val file = audioFile("failed.wav")
        repository.insert(
            entry(createdAt = now - 40 * day, raw = "", audioPath = file.absolutePath, status = HistoryStatus.FAILED),
        )

        assertEquals(1, repository.pruneExpired(retentionDays = 30))

        assertEquals(0, repository.count.first())
        assertFalse(file.exists())
    }

    @Test
    fun orphanCleanup_sparesOwnedFiles_andFilesOfTakesInUse() = runBlocking {
        val owned = audioFile("owned.wav").apply { setLastModified(now - 3 * day) }
        val orphan = audioFile("orphan.wav").apply { setLastModified(now - 3 * day) }
        val recording = audio.newRecordingFile().apply {
            writeBytes(ByteArray(100))
            setLastModified(now - 3 * day)
        }
        val fresh = audioFile("fresh.wav").apply { setLastModified(now) }
        repository.insert(entry(createdAt = now, raw = "x", audioPath = owned.absolutePath))

        repository.pruneExpired(retentionDays = 30)

        assertTrue("a file an entry references", owned.exists())
        assertTrue("a take still being recorded", recording.exists())
        assertTrue("too recent to judge", fresh.exists())
        assertFalse("abandoned and unreferenced", orphan.exists())

        audio.release(recording.absolutePath)
        repository.pruneExpired(retentionDays = 30)
        assertFalse("once released and unreferenced it is an orphan", recording.exists())
    }

    @Test
    fun recoverOrphans_turnsAbandonedAudioIntoFailedTakes_andLeavesOwnedOnesAlone() = runBlocking {
        val abandoned = audioFile("abandoned.wav", bytes = 44 + 32_000).apply { setLastModified(now - 3_600_000) }
        val tiny = audioFile("tiny.wav", bytes = 44).apply { setLastModified(now - 3 * day) }
        val owned = audioFile("owned.wav", bytes = 44 + 32_000).apply { setLastModified(now - 3_600_000) }
        repository.insert(entry(createdAt = now, raw = "x", audioPath = owned.absolutePath))

        assertEquals(1, repository.recoverOrphans())

        val recovered = dao.snapshot.single { it.audioPath == abandoned.absolutePath }
        assertEquals(HistoryStatus.FAILED, recovered.status)
        assertEquals(HistoryFailureReason.INTERRUPTED, recovered.failureReason)
        assertEquals(1_000L, recovered.durationMs)
        assertEquals("", recovered.rawText)
        assertArrayEquals("RIFF".toByteArray(), abandoned.readBytes().copyOfRange(0, 4))
        assertEquals(2, dao.snapshot.size)

        repository.pruneExpired(retentionDays = 30)
        assertTrue(abandoned.exists())
        assertTrue(owned.exists())
        assertFalse("a header-only leftover has no audio worth a row", tiny.exists())
    }

    @Test
    fun recoverOrphans_skipsAFileTheRecorderIsStillWriting() = runBlocking {
        val recording = audio.newRecordingFile().apply {
            writeBytes(ByteArray(44 + 32_000))
            setLastModified(now - 3_600_000)
        }

        assertEquals(0, repository.recoverOrphans())

        assertTrue(recording.exists())
        assertTrue(dao.snapshot.isEmpty())
    }

    @Test
    fun reconcileInterrupted_marksStaleInFlightTakes_butNotNewOnesOrFinishedOnes() = runBlocking {
        val processStart = now
        val stale = listOf(HistoryStatus.RECORDED, HistoryStatus.TRANSCRIBING, HistoryStatus.TRANSCRIBED, HistoryStatus.ENHANCING)
            .map { status -> repository.insert(entry(createdAt = now - 1_000, raw = "t", status = status)) }
        val live = repository.insert(entry(createdAt = now + 1_000, raw = "", status = HistoryStatus.TRANSCRIBING))
        val done = repository.insert(entry(createdAt = now - 1_000, raw = "ok", status = HistoryStatus.COMPLETED))
        val failed = repository.insert(
            entry(createdAt = now - 1_000, raw = "", status = HistoryStatus.FAILED, reason = HistoryFailureReason.NETWORK_ERROR),
        )

        assertEquals(4, repository.reconcileInterrupted(before = processStart))

        stale.forEach { id ->
            val row = repository.get(id)!!
            assertEquals(HistoryStatus.FAILED, row.status)
            assertEquals(HistoryFailureReason.INTERRUPTED, row.failureReason)
        }
        assertEquals(HistoryStatus.TRANSCRIBING, repository.get(live)!!.status)
        assertEquals(HistoryStatus.COMPLETED, repository.get(done)!!.status)
        assertEquals(HistoryFailureReason.NETWORK_ERROR, repository.get(failed)!!.failureReason)
    }

    @Test
    fun complete_deletesTheAudioUnlessKeepRecordingsIsOn() = runBlocking {
        val keep = audioFile("keep.wav")
        val drop = audioFile("drop.wav")
        val keepId = repository.insert(entry(createdAt = now, raw = "", audioPath = keep.absolutePath, status = HistoryStatus.FAILED))
        val dropId = repository.insert(entry(createdAt = now, raw = "", audioPath = drop.absolutePath, status = HistoryStatus.FAILED))

        repository.complete(keepId, keepAudio = true) { it.copy(rawText = "kept") }
        repository.complete(dropId, keepAudio = false) { it.copy(rawText = "dropped") }

        assertTrue(keep.exists())
        assertEquals(keep.absolutePath, repository.get(keepId)!!.audioPath)
        assertFalse(drop.exists())
        assertNull(repository.get(dropId)!!.audioPath)
        assertEquals(HistoryStatus.COMPLETED, repository.get(dropId)!!.status)
        assertEquals("dropped", repository.get(dropId)!!.rawText)
    }

    @Test
    fun needsAttentionCount_countsOnlyFailedTakes() = runBlocking {
        repository.insert(entry(createdAt = 1, raw = "a", status = HistoryStatus.COMPLETED))
        repository.insert(entry(createdAt = 2, raw = "", status = HistoryStatus.FAILED))
        repository.insert(entry(createdAt = 3, raw = "", status = HistoryStatus.TRANSCRIBING))
        repository.insert(entry(createdAt = 4, raw = "b", status = HistoryStatus.FAILED))

        assertEquals(2, repository.needsAttentionCount.first())
    }

    @Test
    fun audioStore_refusesToDeleteFilesOutsideItsDirectory() {
        val outside = File(root, "outside.wav").apply { writeText("pcm") }

        audio.delete(outside.absolutePath)

        assertTrue(outside.exists())
    }

    private fun audioFile(name: String, bytes: Int = 3): File {
        audioDir.mkdirs()
        return File(audioDir, name).apply { writeBytes(ByteArray(bytes)) }
    }

    private fun entry(
        createdAt: Long,
        raw: String,
        audioPath: String? = null,
        status: HistoryStatus = HistoryStatus.COMPLETED,
        reason: HistoryFailureReason? = null,
    ) = TranscriptHistoryEntry(
        createdAt = createdAt,
        rawText = raw,
        providerId = "p",
        sttModelId = "whisper-1",
        durationMs = 1_000,
        wordCount = 1,
        audioPath = audioPath,
        status = status,
        failureReason = reason,
    )
}
