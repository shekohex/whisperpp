package com.github.shekohex.whisperpp.ime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun newFile_isDistinctPerTake_evenAtTheSameInstant() {
        val files = RecordingFiles(directory = { folder.root }, clock = { 42L })

        val first = files.newFile()
        val second = files.newFile()

        assertNotEquals(first, second)
        assertEquals(folder.root, first.parentFile)
        assertEquals("rec_42_1.wav", first.name)
        assertEquals("rec_42_2.wav", second.name)
    }

    @Test
    fun deleteStale_removesLeftoverTakesOnly() {
        val take = File(folder.root, "rec_1_1.wav").apply { writeText("x") }
        val legacy = File(folder.root, "recorded.wav").apply { writeText("x") }
        val unrelated = File(folder.root, "keep.txt").apply { writeText("x") }

        RecordingFiles(directory = { folder.root }, clock = { 0L }).deleteStale()

        assertFalse(take.exists())
        assertFalse(legacy.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun deleteStale_withMissingDirectory_doesNothing() {
        RecordingFiles(directory = { File(folder.root, "missing") }, clock = { 0L }).deleteStale()
    }
}
