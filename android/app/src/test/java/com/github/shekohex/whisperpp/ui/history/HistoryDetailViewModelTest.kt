package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.history.FakeTranscriptHistoryDao
import com.github.shekohex.whisperpp.data.history.HistoryAudioStore
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_800_000_000_000L
    private lateinit var root: File
    private lateinit var dao: FakeTranscriptHistoryDao
    private lateinit var repository: TranscriptHistoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        root = Files.createTempDirectory("history-detail-vm").toFile()
        dao = FakeTranscriptHistoryDao()
        repository = TranscriptHistoryRepository(
            dao = dao,
            audio = HistoryAudioStore(File(root, HistoryAudioStore.DIR_NAME)),
            clock = { now },
            ioDispatcher = dispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    private fun viewModel(id: Long, reEnhancer: HistoryReEnhancer = HistoryReEnhancer { error("unused") }) =
        HistoryDetailViewModel(id, repository, reEnhancer, appResolver = { null })

    @Test
    fun loadsTheEntry() = runTest(dispatcher) {
        val id = repository.insert(historyEntry(now, "raw words"))
        val vm = viewModel(id)

        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        assertEquals("raw words", vm.state.value.entry?.rawText)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isGone)
        assertNull(vm.state.value.player)
    }

    @Test
    fun missingEntry_isGone() = runTest(dispatcher) {
        val vm = viewModel(404)
        advanceUntilIdle()

        assertTrue(vm.state.value.isGone)
        assertNull(vm.state.value.entry)
    }

    @Test
    fun delete_removesEntry_andLeaves() = runTest(dispatcher) {
        val id = repository.insert(historyEntry(now, "bye"))
        val vm = viewModel(id)
        advanceUntilIdle()

        vm.delete()
        advanceUntilIdle()

        assertTrue(dao.snapshot.isEmpty())
        assertTrue(vm.state.value.isGone)
    }

    @Test
    fun reEnhance_storesTextAndModel_andReports() = runTest(dispatcher) {
        val id = repository.insert(historyEntry(now, "um hello there"))
        val seen = mutableListOf<String>()
        val vm = viewModel(id) { entry ->
            seen += entry.rawText
            HistoryReEnhancer.Result.Enhanced("Hello there.", "gpt-4o-mini")
        }
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.reEnhance()
        advanceUntilIdle()

        assertEquals(listOf("um hello there"), seen)
        val stored = dao.snapshot.single()
        assertEquals("Hello there.", stored.enhancedText)
        assertEquals("gpt-4o-mini", stored.enhancementModelId)
        assertEquals("Hello there.", vm.state.value.entry?.enhancedText)
        assertFalse(vm.state.value.isEnhancing)
        assertEquals(1, messages.size)
    }

    @Test
    fun reEnhance_failure_keepsEntryAndShowsMessage() = runTest(dispatcher) {
        val id = repository.insert(historyEntry(now, "raw", enhanced = "old enhanced"))
        val vm = viewModel(id) { HistoryReEnhancer.Result.Failed("Enhancement failed for X: offline") }
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.reEnhance()
        advanceUntilIdle()

        assertEquals("old enhanced", dao.snapshot.single().enhancedText)
        assertEquals(listOf<Message>(Message.Text("Enhancement failed for X: offline")), messages)
        assertFalse(vm.state.value.isEnhancing)
    }

    @Test
    fun reEnhance_unexpectedError_isReportedNotThrown() = runTest(dispatcher) {
        val id = repository.insert(historyEntry(now, "raw"))
        val vm = viewModel(id) { error("boom") }
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.reEnhance()
        advanceUntilIdle()

        assertEquals(1, messages.size)
        assertTrue(messages.single() is Message.Res)
        assertFalse(vm.state.value.isEnhancing)
    }

    @Test
    fun reEnhance_canBeCancelled_andLeavesTheEntryUntouched() = runTest(dispatcher) {
        val id = repository.insert(historyEntry(now, "raw"))
        val gate = CompletableDeferred<HistoryReEnhancer.Result>()
        val vm = viewModel(id) { gate.await() }
        advanceUntilIdle()

        vm.reEnhance()
        runCurrent()
        assertTrue(vm.state.value.isEnhancing)

        vm.cancelReEnhance()
        advanceUntilIdle()

        assertFalse(vm.state.value.isEnhancing)
        assertNull(dao.snapshot.single().enhancedText)
    }

    @Test
    fun reEnhance_whileRunning_isIgnored() = runTest(dispatcher) {
        val id = repository.insert(historyEntry(now, "raw"))
        val gate = CompletableDeferred<HistoryReEnhancer.Result>()
        var calls = 0
        val vm = viewModel(id) {
            calls++
            gate.await()
        }
        advanceUntilIdle()

        vm.reEnhance()
        runCurrent()
        vm.reEnhance()
        runCurrent()

        assertEquals(1, calls)
        gate.complete(HistoryReEnhancer.Result.Enhanced("Done.", "m"))
        advanceUntilIdle()
        assertEquals("Done.", dao.snapshot.single().enhancedText)
    }

    private fun TestScope.collectMessages(vm: HistoryDetailViewModel): List<Message> {
        val collected = mutableListOf<Message>()
        // Unconfined: a background collector would otherwise starve behind advanceUntilIdle().
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { collected += it } }
        return collected
    }
}
