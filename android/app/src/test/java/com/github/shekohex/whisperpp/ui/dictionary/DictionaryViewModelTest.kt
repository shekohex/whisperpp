package com.github.shekohex.whisperpp.ui.dictionary

import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.data.dictionary.DictionaryRepository
import com.github.shekohex.whisperpp.domain.autolearn.InMemoryPreferencesStore
import com.github.shekohex.whisperpp.ui.dictionary.DictionaryViewModel.AddResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryViewModelTest {
    private val repository = DictionaryRepository(InMemoryPreferencesStore())
    private lateinit var viewModel: DictionaryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = DictionaryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun parseRule_splitsCommaListTrimsAndDedupesIgnoringCase() {
        val rule = DictionaryViewModel.parseRule(" whisper pp , Whisper PP,whisper plus plus,, ", " Whisper++ ")

        assertEquals(Replacement(listOf("whisper pp", "whisper plus plus"), "Whisper++"), rule)
        assertNull(DictionaryViewModel.parseRule(" , ", "x"))
        assertNull(DictionaryViewModel.parseRule("a", "  "))
    }

    @Test
    fun addReplacement_storesRule_andRejectsDuplicateIgnoringCase() = runTest {
        assertEquals(AddResult.ADDED, viewModel.addReplacement("vois ink, voice ink", "VoiceInk"))
        assertEquals(AddResult.DUPLICATE, viewModel.addReplacement("VOICE INK, Vois Ink", "VoiceInk"))
        assertEquals(AddResult.INVALID, viewModel.addReplacement("", "VoiceInk"))

        assertEquals(listOf(Replacement(listOf("vois ink", "voice ink"), "VoiceInk")), repository.replacements.first())
    }

    @Test
    fun addReplacement_movesExistingOriginalToNewRule() = runTest {
        viewModel.addReplacement("gpt, g p t", "GPT")
        viewModel.addReplacement("G P T", "GPT-4")

        assertEquals(
            listOf(Replacement(listOf("gpt"), "GPT"), Replacement(listOf("G P T"), "GPT-4")),
            repository.replacements.first(),
        )
    }

    @Test
    fun updateReplacement_editsInPlace() = runTest {
        viewModel.addReplacement("a", "A")
        viewModel.addReplacement("b", "B")
        val old = repository.replacements.first().first()

        assertEquals(AddResult.ADDED, viewModel.updateReplacement(old, "a, aa", "AA"))

        assertEquals(
            listOf(Replacement(listOf("a", "aa"), "AA"), Replacement(listOf("b"), "B")),
            repository.replacements.first(),
        )
    }

    @Test
    fun deleteReplacement_thenUndo_restoresRule() = runTest {
        viewModel.addReplacement("vois ink", "VoiceInk")
        val rule = repository.replacements.first().single()

        val removed = viewModel.removeReplacement(rule)
        assertEquals(emptyList<Replacement>(), repository.replacements.first())

        viewModel.undo(removed)
        assertEquals(listOf(rule), repository.replacements.first())
    }

    @Test
    fun addVocabulary_parsesCommaList_dedupesIgnoringCase_andUndoesRemoval() = runTest {
        assertEquals(AddResult.ADDED, viewModel.addVocabulary("Kotlin, Compose, kotlin"))
        assertEquals(AddResult.DUPLICATE, viewModel.addVocabulary("KOTLIN"))
        assertEquals(AddResult.ADDED, viewModel.addVocabulary("compose, Gradle"))
        assertEquals(AddResult.INVALID, viewModel.addVocabulary(" , "))
        assertEquals(listOf("Kotlin", "Compose", "Gradle"), repository.vocabulary.first())

        val removed = viewModel.removeVocabulary("compose")
        assertEquals(listOf("Kotlin", "Gradle"), repository.vocabulary.first())
        assertEquals(DictionaryViewModel.Removed.Term("Compose"), removed)

        viewModel.undo(removed)
        assertEquals(listOf("Kotlin", "Gradle", "Compose"), repository.vocabulary.first())
    }
}
