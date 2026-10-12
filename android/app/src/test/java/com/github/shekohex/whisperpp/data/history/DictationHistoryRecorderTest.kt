package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.domain.dictation.CompletionStats
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryRecorder
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

/** The take's row is written when recording stops and follows the pipeline; audio is never lost on failure. */
@OptIn(ExperimentalCoroutinesApi::class)
class DictationHistoryRecorderTest {
    private lateinit var root: File
    private lateinit var audio: HistoryAudioStore
    private val dao = FakeTranscriptHistoryDao()
    private val settings = MutableStateFlow(HistorySettings())
    private val stats = CompletionStats(providerId = "p", sttModelId = "whisper-1", languageCode = "ar", enhancementModelId = "gpt")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("history-recorder").toFile()
        audio = HistoryAudioStore(File(root, HistoryAudioStore.DIR_NAME), clock = { 42L })
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun TestScope.recorder(): DictationHistoryRecorder {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DictationHistoryRecorder(
            repository = TranscriptHistoryRepository(dao, audio, clock = { 42L }, ioDispatcher = dispatcher),
            audio = audio,
            settings = settings,
            scope = this,
            clock = { 42L },
        )
    }

    private fun take(file: File = audio.newRecordingFile().apply { writeBytes(wav(4_000)) }) = HistoryRecorder.Take(
        file = file,
        durationMs = 1_500,
        appPackage = "com.example",
        modeId = "mode-1",
        stats = stats.copy(enhancementModelId = null),
    )

    private fun wav(dataBytes: Int) = ByteArray(44 + dataBytes)

    @Test
    fun begin_writesTheTakeBeforeAnyTranscription() = runTest {
        val recorder = recorder()
        val take = take()
        recorder.begin(take)
        advanceUntilIdle()

        val row = dao.snapshot.single()
        assertEquals(HistoryStatus.RECORDED, row.status)
        assertEquals("", row.rawText)
        assertEquals(take.file.absolutePath, row.audioPath)
        assertEquals(1_500L, row.durationMs)
        assertEquals("com.example", row.appPackage)
        assertEquals("mode-1", row.modeId)
        assertEquals("p", row.providerId)
        assertEquals("whisper-1", row.sttModelId)
        assertEquals("ar", row.language)
        assertEquals(42L, row.createdAt)
        assertTrue(take.file.exists())
    }

    @Test
    fun pipeline_updatesStatusAndText_thenCompletes() = runTest {
        val recorder = recorder()
        val ticket = recorder.begin(take())
        recorder.transcribing(ticket)
        advanceUntilIdle()
        assertEquals(HistoryStatus.TRANSCRIBING, dao.snapshot.single().status)

        recorder.transcribed(ticket, "hello world ")
        advanceUntilIdle()
        val transcribed = dao.snapshot.single()
        assertEquals(HistoryStatus.TRANSCRIBED, transcribed.status)
        assertEquals("hello world", transcribed.rawText)
        assertEquals(2, transcribed.wordCount)

        recorder.enhancing(ticket)
        recorder.complete(ticket, "hello world ", "Hello, world.", stats)
        advanceUntilIdle()
        val done = dao.snapshot.single()
        assertEquals(HistoryStatus.COMPLETED, done.status)
        assertEquals("hello world", done.rawText)
        assertEquals("Hello, world.", done.enhancedText)
        assertEquals("gpt", done.enhancementModelId)
        assertNull(done.failureReason)
    }

    @Test
    fun complete_unchangedEnhancement_storesNoEnhancedText() = runTest {
        val recorder = recorder()
        val ticket = recorder.begin(take())
        recorder.complete(ticket, "same ", "same", stats)
        advanceUntilIdle()

        val row = dao.snapshot.single()
        assertNull(row.enhancedText)
        assertNull(row.enhancementModelId)
    }

    @Test
    fun complete_keepsTheAudio_whileKeepRecordingsIsOn() = runTest {
        settings.value = HistorySettings(keepAudio = true)
        val recorder = recorder()
        val take = take()
        recorder.complete(recorder.begin(take), "text", null, stats)
        advanceUntilIdle()

        assertTrue(take.file.exists())
        assertEquals(take.file.absolutePath, dao.snapshot.single().audioPath)
    }

    @Test
    fun complete_deletesTheAudio_whenKeepRecordingsIsOff() = runTest {
        settings.value = HistorySettings(keepAudio = false)
        val recorder = recorder()
        val take = take()
        recorder.complete(recorder.begin(take), "text", null, stats)
        advanceUntilIdle()

        assertFalse(take.file.exists())
        assertNull(dao.snapshot.single().audioPath)
        assertEquals(HistoryStatus.COMPLETED, dao.snapshot.single().status)
    }

    @Test
    fun fail_keepsTheAudioAndText_evenWithKeepRecordingsOff() = runTest {
        settings.value = HistorySettings(keepAudio = false)
        val recorder = recorder()
        val take = take()
        val ticket = recorder.begin(take)
        recorder.transcribed(ticket, "partial words")
        recorder.fail(ticket, HistoryFailureReason.INSERT_BLOCKED, rawText = null, enhancedText = "Partial words.")
        advanceUntilIdle()

        val row = dao.snapshot.single()
        assertEquals(HistoryStatus.FAILED, row.status)
        assertEquals(HistoryFailureReason.INSERT_BLOCKED, row.failureReason)
        assertEquals("partial words", row.rawText)
        assertEquals("Partial words.", row.enhancedText)
        assertEquals(take.file.absolutePath, row.audioPath)
        assertTrue(take.file.exists())
    }

    @Test
    fun enhancementFailure_completesWithTheReasonKept() = runTest {
        val recorder = recorder()
        val ticket = recorder.begin(take())
        recorder.complete(ticket, "raw", null, stats, enhancementFailed = true)
        advanceUntilIdle()

        val row = dao.snapshot.single()
        assertEquals(HistoryStatus.COMPLETED, row.status)
        assertEquals(HistoryFailureReason.ENHANCEMENT_FAILED, row.failureReason)
    }

    @Test
    fun disabledHistory_storesNothing_andDeletesTheFileWhenTheRunEnds() = runTest {
        settings.value = HistorySettings(enabled = false)
        val recorder = recorder()
        val take = take()
        val ticket = recorder.begin(take)
        recorder.transcribing(ticket)
        advanceUntilIdle()

        assertTrue(dao.snapshot.isEmpty())
        // Batch transcription may still need the file until the run ends.
        assertTrue(take.file.exists())

        recorder.fail(ticket, HistoryFailureReason.TRANSCRIPTION_FAILED)
        advanceUntilIdle()
        assertFalse(take.file.exists())
        assertTrue(dao.snapshot.isEmpty())
    }

    @Test
    fun fileOutsideTheStore_isNotTracked_andDeletedWhenTheRunEnds() = runTest {
        val recorder = recorder()
        val outside = File(root, "elsewhere.wav").apply { writeBytes(wav(4_000)) }
        val ticket = recorder.begin(take(outside))
        recorder.complete(ticket, "text", null, stats)
        advanceUntilIdle()

        assertTrue(dao.snapshot.isEmpty())
        assertFalse(outside.exists())
    }

    @Test
    fun begin_repairsAWavTheRecorderNeverFinalized() = runTest {
        val recorder = recorder()
        val take = take()
        recorder.begin(take)
        advanceUntilIdle()

        assertArrayEquals("RIFF".toByteArray(), take.file.readBytes().copyOfRange(0, 4))
    }

    @Test
    fun updatesFromOneTakeApplyInCallOrder() = runTest {
        val recorder = recorder()
        val ticket = recorder.begin(take())
        recorder.transcribing(ticket)
        recorder.transcribed(ticket, "one")
        recorder.enhancing(ticket)
        recorder.complete(ticket, "one", "One.", stats)
        recorder.fail(ticket, HistoryFailureReason.CANCELLED)
        advanceUntilIdle()

        // The last call wins, as issued.
        assertEquals(HistoryStatus.FAILED, dao.snapshot.single().status)
        assertEquals("One.", dao.snapshot.single().enhancedText)
    }
}
