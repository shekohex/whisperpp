package com.github.shekohex.whisperpp.ui.dictionary

import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnPendingQueue
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnProposalStore
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnSettings
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnAiReviewer
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnCoordinator
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewModel
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewProposal
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewSchedule
import com.github.shekohex.whisperpp.domain.autolearn.FakeDictionaryEditor
import com.github.shekohex.whisperpp.domain.autolearn.FakeEnhancementProvider
import com.github.shekohex.whisperpp.domain.autolearn.InMemoryPreferencesStore
import com.github.shekohex.whisperpp.domain.autolearn.LearningAction
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnMutationSummary
import com.github.shekohex.whisperpp.ui.dictionary.AutoLearnReviewViewModel.Choice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLearnReviewViewModelTest {
    private var now = 1_000_000L
    private val dictionary = FakeDictionaryEditor()
    private val store = AutoLearnProposalStore(InMemoryPreferencesStore(), dictionary, clock = { now })
    private val coordinator = AutoLearnCoordinator(
        settings = MutableStateFlow(AutoLearnSettings(enabled = true, reviewSchedule = AutoLearnReviewSchedule.MANUALLY)),
        markReviewed = {},
        queue = AutoLearnPendingQueue(InMemoryPreferencesStore(), clock = { now }),
        reviewer = AutoLearnAiReviewer(prompt = { "P" }, resolveModel = { Result.success(AutoLearnReviewModel(FakeEnhancementProvider())) }),
        proposalStore = store,
        scope = CoroutineScope(Dispatchers.Unconfined),
        clock = { now },
    )
    private lateinit var viewModel: AutoLearnReviewViewModel

    private val voiceInk = proposal("1", "vois ink", "VoiceInk")
    private val kotlin = proposal("2", "cot lin", "Kotlin")

    private fun proposal(id: String, incorrect: String, term: String) = AutoLearnReviewProposal(
        id = id,
        candidateId = "c$id",
        originalText = "use $incorrect today",
        correctedText = "use $term today",
        learningAction = LearningAction.ADD_REPLACEMENT_AND_VOCABULARY,
        incorrectTextToReplace = incorrect,
        correctedVocabularyTerm = term,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AutoLearnReviewViewModel(coordinator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun chips_changeAction_orDismiss() = runTest {
        store.add(listOf(voiceInk, kotlin))

        viewModel.choose(voiceInk, Choice.VOCABULARY)
        viewModel.choose(kotlin, Choice.DISMISS)

        val stored = coordinator.proposals.first()
        assertEquals(listOf("1"), stored.map { it.id })
        assertEquals(LearningAction.ADD_VOCABULARY_ONLY, stored.single().learningAction)
    }

    @Test
    fun replacementChoice_isDisabledWithoutSourceText() {
        val vocabularyOnly = voiceInk.copy(incorrectTextToReplace = null, learningAction = LearningAction.ADD_VOCABULARY_ONLY)

        assertFalse(AutoLearnReviewViewModel.isAllowed(vocabularyOnly, Choice.REPLACEMENT))
        assertFalse(AutoLearnReviewViewModel.isAllowed(vocabularyOnly, Choice.BOTH))
        assertTrue(AutoLearnReviewViewModel.isAllowed(vocabularyOnly, Choice.VOCABULARY))
        assertTrue(AutoLearnReviewViewModel.isAllowed(vocabularyOnly, Choice.DISMISS))
        assertEquals(Choice.VOCABULARY, AutoLearnReviewViewModel.choiceOf(vocabularyOnly.learningAction))
    }

    @Test
    fun applyAll_learnsEverything_thenUndoWithinWindowReverts() = runTest {
        store.add(listOf(voiceInk, kotlin))

        val summary = viewModel.applyAll()!!

        assertEquals(2, summary.corrections.size)
        assertTrue(coordinator.proposals.first().isEmpty())
        assertEquals(listOf("VoiceInk", "Kotlin"), dictionary.dictionary.vocabulary)
        assertEquals(
            listOf(Replacement(listOf("vois ink"), "VoiceInk"), Replacement(listOf("cot lin"), "Kotlin")),
            dictionary.dictionary.replacements,
        )

        now += AutoLearnMutationSummary.UNDO_WINDOW_MS - 1
        assertTrue(viewModel.undo(summary))
        assertTrue(dictionary.dictionary.vocabulary.isEmpty())
        assertTrue(dictionary.dictionary.replacements.isEmpty())
    }

    @Test
    fun undo_afterWindow_isRejected_andEmptyApplyReturnsNull() = runTest {
        assertNull(viewModel.applyAll())
        store.add(listOf(voiceInk))
        val summary = viewModel.applyAll()!!

        now += AutoLearnMutationSummary.UNDO_WINDOW_MS + 1

        assertFalse(viewModel.undo(summary))
        assertEquals(listOf("VoiceInk"), dictionary.dictionary.vocabulary)
    }
}
