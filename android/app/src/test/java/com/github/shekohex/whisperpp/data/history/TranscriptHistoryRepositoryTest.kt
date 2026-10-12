package com.github.shekohex.whisperpp.data.history

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class TranscriptHistoryRepositoryTest {
    private val now = 1_800_000_000_000L
    private lateinit var root: File
    private lateinit var audioDir: File
    private lateinit var dao: FakeTranscriptHistoryDao
    private lateinit var repository: TranscriptHistoryRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("history-test").toFile()
        audioDir = File(root, HistoryAudioStore.DIR_NAME)
        dao = FakeTranscriptHistoryDao()
        repository = TranscriptHistoryRepository(dao, HistoryAudioStore(audioDir), clock = { now })
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun insertListAndUpdateEnhancedText() = runBlocking {
        val older = repository.insert(entry(createdAt = now - 2_000, raw = "first"))
        val newer = repository.insert(entry(createdAt = now - 1_000, raw = "second"))

        assertEquals(listOf(newer, older), repository.observeRecent().first().map { it.id })
        assertEquals(listOf(newer), repository.observeRecent(limit = 1).first().map { it.id })
        assertEquals(2, repository.count.first())

        assertTrue(repository.updateEnhancedText(older, "First.", "gpt"))
        val updated = repository.get(older)!!
        assertEquals("First.", updated.enhancedText)
        assertEquals("gpt", updated.enhancementModelId)
        assertEquals("First.", updated.finalText)
        assertFalse(repository.updateEnhancedText(999, "x", null))
    }

    @Test
    fun search_matchesRawAndEnhanced_caseInsensitive_withEscapedWildcards() = runBlocking {
        repository.insert(entry(createdAt = now - 3, raw = "Meeting at NOON"))
        repository.insert(entry(createdAt = now - 2, raw = "draft", enhanced = "Final noon report"))
        repository.insert(entry(createdAt = now - 1, raw = "50% done_now"))

        assertEquals(listOf("draft", "Meeting at NOON"), repository.search("noon").first().map { it.rawText })
        assertEquals(listOf("50% done_now"), repository.search("0% d").first().map { it.rawText })
        assertTrue(repository.search("%").first().size == 1)
        assertTrue(repository.search("g_n").first().isEmpty())
        assertEquals(3, repository.search("  ").first().size)
        assertEquals("%50\\%\\_x\\\\%", TranscriptHistoryRepository.likePattern("50%_x\\"))
    }

    @Test
    fun delete_removesRowsAndTheirAudio() = runBlocking {
        val audio = audioFile("a.wav")
        val keep = audioFile("b.wav")
        val first = repository.insert(entry(createdAt = now, raw = "one", audioPath = audio.absolutePath))
        val second = repository.insert(entry(createdAt = now, raw = "two", audioPath = keep.absolutePath))

        repository.delete(listOf(first))

        assertNull(repository.get(first))
        assertFalse(audio.exists())
        assertTrue(keep.exists())
        assertEquals(listOf(second), repository.observeRecent().first().map { it.id })

        repository.deleteAll()
        assertEquals(0, repository.count.first())
        assertFalse(keep.exists())
    }

    @Test
    fun pruneExpired_deletesOldEntriesAudioAndOrphans() = runBlocking {
        val day = TimeUnit.DAYS.toMillis(1)
        val oldAudio = audioFile("old.wav")
        val freshAudio = audioFile("fresh.wav")
        val orphan = audioFile("orphan.wav").apply { setLastModified(now - day) }
        val inFlight = audioFile("inflight.wav").apply { setLastModified(now) }
        repository.insert(entry(createdAt = now - 31 * day, raw = "old", audioPath = oldAudio.absolutePath))
        repository.insert(entry(createdAt = now - 29 * day, raw = "fresh", audioPath = freshAudio.absolutePath))

        assertEquals(1, repository.pruneExpired(retentionDays = 30))

        assertEquals(listOf("fresh"), repository.observeRecent().first().map { it.rawText })
        assertFalse(oldAudio.exists())
        assertTrue(freshAudio.exists())
        assertFalse(orphan.exists())
        assertTrue(inFlight.exists())
    }

    @Test
    fun pruneExpired_withZeroRetention_keepsEverything() = runBlocking {
        repository.insert(entry(createdAt = 1L, raw = "ancient"))
        assertEquals(0, repository.pruneExpired(retentionDays = 0))
        assertEquals(1, repository.count.first())
    }

    @Test
    fun exportAndImport_roundTripTextOnly() = runBlocking {
        repository.insert(entry(createdAt = now - 1, raw = "one", enhanced = "One.", audioPath = audioFile("x.wav").absolutePath))
        repository.insert(entry(createdAt = now, raw = "two"))
        val backup = repository.exportBackup()
        assertFalse(repository.exportJson().contains("audioPath"))
        assertFalse(repository.exportJson().contains("x.wav"))

        assertEquals(0, repository.importBackup(backup, replace = false))
        assertEquals(2, repository.count.first())

        val incoming = backup.copy(entries = backup.entries + HistoryBackupEntry(createdAt = now + 1, rawText = "three"))
        assertEquals(1, repository.importBackup(incoming, replace = false))
        assertEquals(3, repository.count.first())

        assertEquals(1, repository.importBackup(HistoryBackup(entries = listOf(HistoryBackupEntry(createdAt = 5, rawText = "only"))), replace = true))
        assertEquals(listOf("only"), repository.observeRecent().first().map { it.rawText })
        assertTrue(audioDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun import_dropsEntriesWithoutText() = runBlocking {
        val backup = HistoryBackup(entries = listOf(HistoryBackupEntry(createdAt = 5, rawText = " "), HistoryBackupEntry(createdAt = 0, rawText = "x")))
        assertEquals(0, repository.importBackup(backup, replace = false))
    }

    private fun audioFile(name: String): File {
        audioDir.mkdirs()
        return File(audioDir, name).apply { writeText("pcm") }
    }

    private fun entry(createdAt: Long, raw: String, enhanced: String? = null, audioPath: String? = null) = TranscriptHistoryEntry(
        createdAt = createdAt,
        rawText = raw,
        enhancedText = enhanced,
        providerId = "p",
        sttModelId = "whisper-1",
        durationMs = 1_000,
        wordCount = 1,
        audioPath = audioPath,
    )
}
