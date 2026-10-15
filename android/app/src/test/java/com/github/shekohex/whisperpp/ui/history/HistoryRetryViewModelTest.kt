package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.FakeTranscriptHistoryDao
import com.github.shekohex.whisperpp.data.history.HistoryAudioStore
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryRepository
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import com.github.shekohex.whisperpp.ui.history.HistoryDetailViewModel.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Retry transcription from History: success (with and without enhancement), failure, empty, cancel. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRetryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_800_000_000_000L
    private lateinit var root: File
    private lateinit var audioDir: File
    private lateinit var dao: FakeTranscriptHistoryDao
    private lateinit var repository: TranscriptHistoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        root = Files.createTempDirectory("history-retry-vm").toFile()
        audioDir = File(root, HistoryAudioStore.DIR_NAME).apply { mkdirs() }
        dao = FakeTranscriptHistoryDao()
        repository = TranscriptHistoryRepository(
            dao = dao,
            audio = HistoryAudioStore(audioDir),
            clock = { now },
            ioDispatcher = dispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    private class FakeTranscriber(
        var enhance: Boolean = false,
        var result: suspend () -> HistoryTranscriber.Result,
    ) : HistoryTranscriber {
        var calls = 0

        override suspend fun transcribe(audio: File, entry: TranscriptHistoryEntry): HistoryTranscriber.Result {
            calls++
            return result()
        }

        override suspend fun enhancementEnabled(entry: TranscriptHistoryEntry) = enhance
    }

    private fun text(value: String) = HistoryTranscriber.Result.Text(value, providerId = "groq", sttModelId = "whisper-large", languageCode = "ar")

    private suspend fun failedEntry(): Pair<Long, File> {
        val audio = File(audioDir, "take.wav").apply { writeText("pcm") }
        val id = repository.insert(
            historyEntry(
                createdAt = now,
                raw = "",
                audioPath = audio.absolutePath,
                status = HistoryStatus.FAILED,
                failureReason = HistoryFailureReason.NETWORK_ERROR,
            ).copy(providerId = "old", sttModelId = "old-model"),
        )
        return id to audio
    }

    private fun viewModel(
        id: Long,
        transcriber: HistoryTranscriber,
        reEnhancer: HistoryReEnhancer = HistoryReEnhancer { error("unused") },
        keepAudio: Boolean = true,
    ) = HistoryDetailViewModel(
        entryId = id,
        repository = repository,
        reEnhancer = reEnhancer,
        transcriber = transcriber,
        appResolver = { null },
        keepAudio = { keepAudio },
        resolveAudio = { path -> path?.let(::File)?.takeIf { it.isFile } },
        playerFactory = { null },
        clock = { now },
    )

    @Test
    fun failedEntryWithAudio_canBeRetried() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        val vm = viewModel(id, FakeTranscriber { text("unused") })
        advanceUntilIdle()

        assertTrue(vm.state.value.canRetry)
    }

    @Test
    fun success_fillsRawText_withCurrentModel_andCompletes() = runTest(dispatcher) {
        val (id, audio) = failedEntry()
        val transcriber = FakeTranscriber { text("hello again") }
        val vm = viewModel(id, transcriber)
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.retryTranscription()
        advanceUntilIdle()

        val stored = dao.snapshot.single()
        assertEquals("hello again", stored.rawText)
        assertEquals(HistoryStatus.COMPLETED, stored.status)
        assertNull(stored.failureReason)
        assertEquals("groq", stored.providerId)
        assertEquals("whisper-large", stored.sttModelId)
        assertEquals("ar", stored.language)
        assertEquals(2, stored.wordCount)
        assertTrue("keep recordings is on", audio.exists())
        assertEquals("hello again", vm.state.value.entry?.rawText)
        assertFalse(vm.state.value.isRetrying)
        assertTrue(vm.state.value.canRetry)
        assertEquals(listOf<Message>(Message.Res(R.string.history_retry_done)), messages)
    }

    @Test
    fun completedEntryWithAudio_canBeRetranscribed_andReplacesOldText() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        repository.update(id) {
            it.copy(rawText = "old raw", enhancedText = "Old enhanced.", status = HistoryStatus.COMPLETED)
        }
        val transcriber = FakeTranscriber { text("new raw") }
        val vm = viewModel(id, transcriber)
        advanceUntilIdle()

        assertTrue(vm.state.value.canRetry)
        vm.retryTranscription()
        advanceUntilIdle()

        assertEquals(1, transcriber.calls)
        assertEquals("new raw", dao.snapshot.single().rawText)
        assertNull(dao.snapshot.single().enhancedText)
        assertEquals(HistoryStatus.COMPLETED, dao.snapshot.single().status)
        assertTrue(vm.state.value.canRetry)
    }

    @Test
    fun completedEntry_failedRetranscription_preservesExistingText() = runTest(dispatcher) {
        val (id, audio) = failedEntry()
        repository.update(id) {
            it.copy(rawText = "old raw", enhancedText = "Old enhanced.", status = HistoryStatus.COMPLETED)
        }
        val vm = viewModel(id, FakeTranscriber {
            HistoryTranscriber.Result.Failed("offline", HistoryFailureReason.NETWORK_ERROR)
        })
        advanceUntilIdle()

        vm.retryTranscription()
        advanceUntilIdle()

        assertEquals("old raw", dao.snapshot.single().rawText)
        assertEquals("Old enhanced.", dao.snapshot.single().enhancedText)
        assertTrue(audio.exists())
        assertTrue(vm.state.value.canRetry)
    }

    @Test
    fun activeTake_cannotBeRetranscribed() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        repository.update(id) { it.copy(status = HistoryStatus.TRANSCRIBED, rawText = "in progress") }
        val transcriber = FakeTranscriber { text("new raw") }
        val vm = viewModel(id, transcriber)
        advanceUntilIdle()

        assertFalse(vm.state.value.canRetry)
        vm.retryTranscription()
        advanceUntilIdle()

        assertEquals(0, transcriber.calls)
        assertEquals("in progress", dao.snapshot.single().rawText)
    }

    @Test
    fun success_withKeepRecordingsOff_deletesTheAudioOnceResolved() = runTest(dispatcher) {
        val (id, audio) = failedEntry()
        val vm = viewModel(id, FakeTranscriber { text("done") }, keepAudio = false)
        advanceUntilIdle()

        vm.retryTranscription()
        advanceUntilIdle()

        assertNull(dao.snapshot.single().audioPath)
        assertFalse(audio.exists())
        assertFalse(vm.state.value.canRetry)
    }

    @Test
    fun success_runsEnhancement_whenTheModeEnhances() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        val seen = mutableListOf<String>()
        val vm = viewModel(
            id,
            FakeTranscriber(enhance = true) { text("um hello") },
            reEnhancer = { entry ->
                seen += entry.rawText
                HistoryReEnhancer.Result.Enhanced("Hello.", "gpt")
            },
        )
        advanceUntilIdle()

        vm.retryTranscription()
        advanceUntilIdle()

        assertEquals(listOf("um hello"), seen)
        val stored = dao.snapshot.single()
        assertEquals("um hello", stored.rawText)
        assertEquals("Hello.", stored.enhancedText)
        assertEquals("gpt", stored.enhancementModelId)
        assertEquals(HistoryStatus.COMPLETED, stored.status)
    }

    @Test
    fun enhancementFailure_keepsTheRawText_andFlagsIt() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        val vm = viewModel(
            id,
            FakeTranscriber(enhance = true) { text("raw words") },
            reEnhancer = { HistoryReEnhancer.Result.Failed("Enhancement failed for X: offline") },
        )
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.retryTranscription()
        advanceUntilIdle()

        val stored = dao.snapshot.single()
        assertEquals("raw words", stored.rawText)
        assertNull(stored.enhancedText)
        assertEquals(HistoryStatus.COMPLETED, stored.status)
        assertEquals(HistoryFailureReason.ENHANCEMENT_FAILED, stored.failureReason)
        assertTrue(messages.contains(Message.Text("Enhancement failed for X: offline")))
    }

    @Test
    fun failure_keepsTheTakeFailed_withTheNewReason_andReportsIt() = runTest(dispatcher) {
        val (id, audio) = failedEntry()
        val vm = viewModel(
            id,
            FakeTranscriber {
                HistoryTranscriber.Result.Failed("HTTP 500", HistoryFailureReason.TRANSCRIPTION_FAILED)
            },
        )
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.retryTranscription()
        advanceUntilIdle()

        val stored = dao.snapshot.single()
        assertEquals(HistoryStatus.FAILED, stored.status)
        assertEquals(HistoryFailureReason.TRANSCRIPTION_FAILED, stored.failureReason)
        assertEquals("", stored.rawText)
        assertTrue(audio.exists())
        assertEquals(listOf<Message>(Message.Format(R.string.history_retry_failed, "HTTP 500")), messages)
        assertFalse(vm.state.value.isRetrying)
        assertTrue("can be retried again", vm.state.value.canRetry)
    }

    @Test
    fun noSpeechFound_marksTheTakeEmpty() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        val vm = viewModel(id, FakeTranscriber { HistoryTranscriber.Result.Empty })
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.retryTranscription()
        advanceUntilIdle()

        assertEquals(HistoryFailureReason.EMPTY_TRANSCRIPT, dao.snapshot.single().failureReason)
        assertEquals(listOf<Message>(Message.Res(R.string.history_retry_empty)), messages)
    }

    @Test
    fun unexpectedError_isReportedNotThrown_andKeepsTheTake() = runTest(dispatcher) {
        val (id, audio) = failedEntry()
        val vm = viewModel(id, FakeTranscriber { error("boom") })
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.retryTranscription()
        advanceUntilIdle()

        assertEquals(HistoryStatus.FAILED, dao.snapshot.single().status)
        assertTrue(audio.exists())
        assertEquals(listOf<Message>(Message.Res(R.string.history_retry_failed_generic)), messages)
    }

    @Test
    fun cancel_leavesTheTakeAsItWas() = runTest(dispatcher) {
        val (id, audio) = failedEntry()
        val gate = CompletableDeferred<HistoryTranscriber.Result>()
        val vm = viewModel(id, FakeTranscriber { gate.await() })
        advanceUntilIdle()

        vm.retryTranscription()
        runCurrent()
        assertTrue(vm.state.value.isRetrying)

        vm.cancelRetry()
        advanceUntilIdle()

        assertFalse(vm.state.value.isRetrying)
        val stored = dao.snapshot.single()
        assertEquals(HistoryStatus.FAILED, stored.status)
        assertEquals(HistoryFailureReason.NETWORK_ERROR, stored.failureReason)
        assertTrue(audio.exists())
        assertTrue(vm.state.value.canRetry)
    }

    @Test
    fun cancelDuringEnhancement_stillFinishesTheTakeWithItsRawText() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        val gate = CompletableDeferred<HistoryReEnhancer.Result>()
        val vm = viewModel(id, FakeTranscriber(enhance = true) { text("raw words") }, reEnhancer = { gate.await() })
        advanceUntilIdle()

        vm.retryTranscription()
        runCurrent()
        assertTrue(vm.state.value.isEnhancing)

        vm.cancelRetry()
        advanceUntilIdle()

        val stored = dao.snapshot.single()
        assertEquals("raw words", stored.rawText)
        assertEquals(HistoryStatus.COMPLETED, stored.status)
        assertNull(stored.enhancedText)
        assertFalse(vm.state.value.isRetrying)
        assertFalse(vm.state.value.isEnhancing)
    }

    @Test
    fun retryWhileRunning_isIgnored() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        val gate = CompletableDeferred<HistoryTranscriber.Result>()
        val transcriber = FakeTranscriber { gate.await() }
        val vm = viewModel(id, transcriber)
        advanceUntilIdle()

        vm.retryTranscription()
        runCurrent()
        vm.retryTranscription()
        runCurrent()

        assertEquals(1, transcriber.calls)
        gate.complete(text("ok"))
        advanceUntilIdle()
        assertEquals("ok", dao.snapshot.single().rawText)
    }

    @Test
    fun missingAudio_cannotBeRetried() = runTest(dispatcher) {
        val id = repository.insert(
            historyEntry(now, "", audioPath = File(audioDir, "gone.wav").absolutePath, status = HistoryStatus.FAILED),
        )
        val transcriber = FakeTranscriber { text("unused") }
        val vm = viewModel(id, transcriber)
        val messages = collectMessages(vm)
        advanceUntilIdle()

        assertFalse(vm.state.value.canRetry)
        vm.retryTranscription()
        advanceUntilIdle()

        assertEquals(0, transcriber.calls)
        assertEquals(listOf<Message>(Message.Res(R.string.history_retry_no_audio)), messages)
    }

    @Test
    fun retriedEntry_canBeReEnhancedAgain() = runTest(dispatcher) {
        val (id, _) = failedEntry()
        val vm = viewModel(
            id,
            FakeTranscriber { text("fresh words") },
            reEnhancer = { entry -> HistoryReEnhancer.Result.Enhanced("Fresh: ${entry.rawText}", "gpt") },
        )
        advanceUntilIdle()
        vm.retryTranscription()
        advanceUntilIdle()

        vm.reEnhance()
        advanceUntilIdle()

        assertEquals("Fresh: fresh words", dao.snapshot.single().enhancedText)
    }

    @Test
    fun markResolved_completesAFailedTakeThatHasText() = runTest(dispatcher) {
        val audio = File(audioDir, "held.wav").apply { writeText("pcm") }
        val id = repository.insert(
            historyEntry(
                now,
                "held text",
                audioPath = audio.absolutePath,
                status = HistoryStatus.FAILED,
                failureReason = HistoryFailureReason.INSERT_BLOCKED,
            ),
        )
        val vm = viewModel(id, UnusedTranscriber, keepAudio = false)
        advanceUntilIdle()

        vm.markResolved()
        advanceUntilIdle()

        val stored = dao.snapshot.single()
        assertEquals(HistoryStatus.COMPLETED, stored.status)
        assertNull(stored.audioPath)
        assertFalse(audio.exists())
        assertFalse(vm.state.value.canRetry)
    }

    private fun TestScope.collectMessages(vm: HistoryDetailViewModel): List<Message> {
        val collected = mutableListOf<Message>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { collected += it } }
        return collected
    }
}
