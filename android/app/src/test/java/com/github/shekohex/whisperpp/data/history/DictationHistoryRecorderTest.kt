package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.domain.dictation.CompletionStats
import com.github.shekohex.whisperpp.domain.dictation.HistoryRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DictationHistoryRecorderTest {
    private lateinit var root: File
    private val dao = FakeTranscriptHistoryDao()
    private val settings = MutableStateFlow(HistorySettings())

    @Before
    fun setUp() {
        root = Files.createTempDirectory("history-recorder").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun TestScope.recorder(): DictationHistoryRecorder {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val audio = HistoryAudioStore(File(root, HistoryAudioStore.DIR_NAME))
        return DictationHistoryRecorder(
            repository = TranscriptHistoryRepository(dao, audio, clock = { 42L }, ioDispatcher = dispatcher),
            audio = audio,
            settings = settings,
            scope = this,
            clock = { 42L },
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun record_storesTrimmedRawAndEnhancedText() = runTest {
        recorder().record(entry(raw = "hello world ", enhanced = "Hello, world."))
        advanceUntilIdle()

        val row = dao.snapshot.single()
        assertEquals(42L, row.createdAt)
        assertEquals("hello world", row.rawText)
        assertEquals("Hello, world.", row.enhancedText)
        assertEquals("gpt", row.enhancementModelId)
        assertEquals("whisper-1", row.sttModelId)
        assertEquals("ar", row.language)
        assertEquals("com.example", row.appPackage)
        assertEquals(2, row.wordCount)
    }

    @Test
    fun record_unchangedEnhancement_storesNoEnhancedText() = runTest {
        recorder().record(entry(raw = "same ", enhanced = "same"))
        advanceUntilIdle()

        assertNull(dao.snapshot.single().enhancedText)
        assertNull(dao.snapshot.single().enhancementModelId)
    }

    @Test
    fun disabledHistory_recordsNothing_andKeepsNoAudio() = runTest {
        settings.value = HistorySettings(enabled = false, keepAudio = true)
        val recorder = recorder()
        val wav = File(root, "take.wav").apply { writeText("pcm") }

        assertNull(recorder.retainAudio(wav))
        recorder.record(entry(raw = "secret"))
        advanceUntilIdle()

        assertTrue(dao.snapshot.isEmpty())
        assertTrue(wav.exists())
    }

    @Test
    fun keepAudio_movesTheTakeIntoTheHistoryDir() = runTest {
        settings.value = HistorySettings(keepAudio = true)
        val recorder = recorder()
        val wav = File(root, "take.wav").apply { writeText("pcm") }

        val path = recorder.retainAudio(wav)
        assertNotNull(path)
        assertEquals(File(root, HistoryAudioStore.DIR_NAME).canonicalFile, File(path!!).canonicalFile.parentFile)
        assertFalse(wav.exists())

        recorder.record(entry(raw = "with audio", audioPath = path))
        advanceUntilIdle()
        assertEquals(path, dao.snapshot.single().audioPath)

        recorder.discardAudio(path)
        advanceUntilIdle()
        assertFalse(File(path).exists())
    }

    @Test
    fun keepAudioOff_retainsNothing() = runTest {
        val wav = File(root, "take.wav").apply { writeText("pcm") }
        assertNull(recorder().retainAudio(wav))
        assertTrue(wav.exists())
    }

    private fun entry(raw: String, enhanced: String? = null, audioPath: String? = null) = HistoryRecorder.Entry(
        rawText = raw,
        enhancedText = enhanced,
        stats = CompletionStats(providerId = "p", sttModelId = "whisper-1", languageCode = "ar", enhancementModelId = "gpt"),
        appPackage = "com.example",
        durationMs = 1_000,
        audioPath = audioPath,
    )
}
