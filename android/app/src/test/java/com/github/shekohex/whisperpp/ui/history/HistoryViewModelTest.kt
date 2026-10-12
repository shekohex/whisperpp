package com.github.shekohex.whisperpp.ui.history

import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.data.history.FakeTranscriptHistoryDao
import com.github.shekohex.whisperpp.data.history.HistoryAudioStore
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryRepository
import com.github.shekohex.whisperpp.ui.history.HistoryViewModel.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_800_000_000_000L
    private lateinit var root: File
    private lateinit var dao: FakeTranscriptHistoryDao
    private lateinit var repository: TranscriptHistoryRepository
    private lateinit var settings: FakeHistorySettingsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        root = Files.createTempDirectory("history-vm").toFile()
        dao = FakeTranscriptHistoryDao()
        repository = TranscriptHistoryRepository(
            dao = dao,
            audio = HistoryAudioStore(File(root, HistoryAudioStore.DIR_NAME)),
            clock = { now },
            ioDispatcher = dispatcher,
        )
        settings = FakeHistorySettingsStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    private suspend fun seed(vararg texts: String): List<Long> =
        texts.mapIndexed { index, text -> repository.insert(historyEntry(now - (texts.size - index) * 1_000L, text)) }

    private fun viewModel(resolver: HistoryAppResolver = HistoryAppResolver { null }) = HistoryViewModel(
        repository = repository,
        settingsStore = settings,
        appResolver = resolver,
        writeText = { _, _ -> },
    )

    @Test
    fun listsNewestFirstAndStopsLoading() = runTest(dispatcher) {
        seed("first", "second")
        val vm = viewModel()
        assertTrue(vm.state.value.isLoading)

        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf("second", "first"), state.entries.map { it.rawText })
        assertEquals(2, state.totalCount)
    }

    @Test
    fun search_isDebounced_andOnlyTheLastQueryRuns() = runTest(dispatcher) {
        seed("alpha one", "beta two", "alphabet three")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(3, vm.state.value.entries.size)

        vm.onQueryChange("a")
        advanceTimeBy(100)
        vm.onQueryChange("al")
        advanceTimeBy(100)
        vm.onQueryChange("alpha")
        // The query text updates at once, the results only after the pause.
        assertEquals("alpha", vm.state.value.query)
        advanceTimeBy(HistoryViewModel.SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(3, vm.state.value.entries.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("alphabet three", "alpha one"), vm.state.value.entries.map { it.rawText })
        assertTrue(vm.state.value.isSearching)
    }

    @Test
    fun clearingTheQuery_showsEverythingWithoutWaiting() = runTest(dispatcher) {
        seed("alpha", "beta")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onQueryChange("alpha")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.entries.size)

        vm.onQueryChange("")
        runCurrent()

        assertEquals(2, vm.state.value.entries.size)
    }

    @Test
    fun selection_toggleSelectAllAndClear() = runTest(dispatcher) {
        val ids = seed("one", "two", "three")
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.isSelecting)

        vm.toggleSelection(ids[0])
        assertEquals(setOf(ids[0]), vm.state.value.selectedIds)
        assertTrue(vm.state.value.isSelecting)
        assertFalse(vm.state.value.allSelected)

        vm.toggleSelection(ids[0])
        assertFalse(vm.state.value.isSelecting)

        vm.toggleSelectAll()
        assertEquals(ids.toSet(), vm.state.value.selectedIds)
        assertTrue(vm.state.value.allSelected)

        vm.toggleSelectAll()
        assertTrue(vm.state.value.selectedIds.isEmpty())

        vm.selectAll()
        vm.clearSelection()
        assertFalse(vm.state.value.isSelecting)
    }

    @Test
    fun selection_ignoresUnknownIds_andDropsRowsHiddenBySearch() = runTest(dispatcher) {
        val ids = seed("alpha", "beta")
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleSelection(999)
        assertTrue(vm.state.value.selectedIds.isEmpty())

        vm.selectAll()
        vm.onQueryChange("alpha")
        advanceUntilIdle()

        assertEquals(setOf(ids[0]), vm.state.value.selectedIds)
    }

    @Test
    fun selectedShareText_isOldestFirst_andOnlySelected() = runTest(dispatcher) {
        val ids = seed("old one", "middle", "new one")
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleSelection(ids[2])
        vm.toggleSelection(ids[0])

        assertEquals("old one\n\nnew one", vm.selectedShareText())
    }

    @Test
    fun deleteSelected_removesRows_andClearsSelection() = runTest(dispatcher) {
        val ids = seed("keep", "drop one", "drop two")
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleSelection(ids[1])
        vm.toggleSelection(ids[2])

        vm.deleteSelected()
        advanceUntilIdle()

        assertEquals(listOf("keep"), dao.snapshot.map { it.rawText })
        assertEquals(listOf("keep"), vm.state.value.entries.map { it.rawText })
        assertTrue(vm.state.value.selectedIds.isEmpty())
        assertEquals(1, vm.state.value.totalCount)
    }

    @Test
    fun deleteSelected_withNothingSelected_doesNothing() = runTest(dispatcher) {
        seed("one")
        val vm = viewModel()
        advanceUntilIdle()

        vm.deleteSelected()
        advanceUntilIdle()

        assertEquals(1, dao.snapshot.size)
    }

    @Test
    fun deleteAll_emptiesHistory() = runTest(dispatcher) {
        seed("one", "two")
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectAll()

        vm.deleteAll()
        advanceUntilIdle()

        assertTrue(dao.snapshot.isEmpty())
        assertTrue(vm.state.value.entries.isEmpty())
        assertTrue(vm.state.value.selectedIds.isEmpty())
    }

    @Test
    fun settingsToggles_writeThroughAndShowInState() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(HistorySettings(), vm.state.value.settings)

        vm.setEnabled(false)
        vm.setKeepAudio(true)
        vm.setRetentionDays(0)
        advanceUntilIdle()

        assertEquals(HistorySettings(enabled = false, keepAudio = true, retentionDays = 0), vm.state.value.settings)
    }

    @Test
    fun shorteningRetention_prunesOlderEntriesRightAway() = runTest(dispatcher) {
        repository.insert(historyEntry(now - TimeUnit.DAYS.toMillis(10), "old"))
        repository.insert(historyEntry(now - TimeUnit.DAYS.toMillis(1), "recent"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.setRetentionDays(7)
        advanceUntilIdle()

        assertEquals(listOf("recent"), vm.state.value.entries.map { it.rawText })
    }

    @Test
    fun exportTo_passesJsonToSink_andReportsSuccess() = runTest(dispatcher) {
        seed("exported text")
        val vm = viewModel()
        val messages = collectMessages(vm)
        advanceUntilIdle()
        var written = ""

        vm.exportTo { written = it }
        advanceUntilIdle()

        assertTrue(written.contains("exported text"))
        assertEquals(listOf(Message.EXPORTED), messages)
    }

    @Test
    fun exportTo_failure_isReportedNotThrown() = runTest(dispatcher) {
        val vm = viewModel()
        val messages = collectMessages(vm)
        advanceUntilIdle()

        vm.exportTo { error("disk full") }
        advanceUntilIdle()

        assertEquals(listOf(Message.EXPORT_FAILED), messages)
    }

    @Test
    fun resolvesEachAppOnce_andExposesIt() = runTest(dispatcher) {
        repository.insert(historyEntry(now - 2_000, "a", appPackage = "com.example.chat"))
        repository.insert(historyEntry(now - 1_000, "b", appPackage = "com.example.chat"))
        val requested = mutableListOf<String>()
        val info = HistoryAppInfo(label = "Chat", icon = null)
        val vm = viewModel { pkg ->
            requested += pkg
            info
        }

        advanceUntilIdle()

        assertEquals(listOf("com.example.chat"), requested)
        assertEquals(mapOf("com.example.chat" to info), vm.state.value.apps)
    }

    private fun TestScope.collectMessages(vm: HistoryViewModel): List<Message> {
        val collected = mutableListOf<Message>()
        // Unconfined: a background collector would otherwise starve behind advanceUntilIdle().
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { collected += it } }
        return collected
    }
}
