package com.github.shekohex.whisperpp.ui.history

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HistoryHelpersTest {
    private lateinit var root: File
    private lateinit var audioDir: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("history-helpers").toFile()
        audioDir = File(root, "history").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun audioFile_insideTheStoreResolves() {
        val file = File(audioDir, "a.wav").also { it.writeText("x") }

        assertEquals(file.canonicalFile, resolveHistoryAudioFile(audioDir, file.absolutePath))
    }

    @Test
    fun audioFile_outsideTheStore_missingOrBlank_isRejected() {
        val outside = File(root, "secret.txt").also { it.writeText("x") }
        val traversal = File(audioDir, "../secret.txt").path

        assertNull(resolveHistoryAudioFile(audioDir, outside.absolutePath))
        assertNull(resolveHistoryAudioFile(audioDir, traversal))
        assertNull(resolveHistoryAudioFile(audioDir, File(audioDir, "gone.wav").absolutePath))
        assertNull(resolveHistoryAudioFile(audioDir, "  "))
        assertNull(resolveHistoryAudioFile(audioDir, null))
    }

    @Test
    fun audioFile_nestedDirectoryIsRejected() {
        val nested = File(audioDir, "sub").also { it.mkdirs() }
        val file = File(nested, "a.wav").also { it.writeText("x") }

        assertNull(resolveHistoryAudioFile(audioDir, file.absolutePath))
    }

    @Test
    fun retentionOptions_listsPresetsThenForever_andKeepsACustomValue() {
        assertEquals(listOf(7, 30, 90, 0), retentionOptions(30))
        assertEquals(listOf(7, 30, 45, 90, 0), retentionOptions(45))
        assertEquals(listOf(7, 30, 90, 0), retentionOptions(0))
    }

    @Test
    fun shareText_isOldestFirst_trimmedAndUsesTheEnhancedText() {
        val entries = listOf(
            historyEntry(3_000, "third"),
            historyEntry(1_000, "first raw", enhanced = "  First enhanced.  "),
        )

        assertEquals("First enhanced.\n\nthird", buildShareText(entries))
        assertTrue(buildShareText(emptyList()).isEmpty())
    }

    @Test
    fun formatClock_roundsToWholeSeconds() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:05", formatClock(4_600))
        assertEquals("1:05", formatClock(65_000))
        assertEquals("0:00", formatClock(-10))
    }
}
