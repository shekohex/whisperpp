package com.github.shekohex.whisperpp.domain.autolearn

import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnProposalStore
import com.github.shekohex.whisperpp.data.dictionary.DictionaryBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLearnProposalStoreTest {

    private var now = 1_000L
    private val dictionary = FakeDictionaryEditor()
    private val store = AutoLearnProposalStore(InMemoryPreferencesStore(), dictionary, clock = { now })

    private fun proposal(action: LearningAction, incorrect: String?, corrected: String, id: String = corrected) =
        AutoLearnReviewProposal(
            id = id,
            candidateId = "c-$id",
            originalText = "ctx $incorrect",
            correctedText = "ctx $corrected",
            learningAction = action,
            incorrectTextToReplace = incorrect,
            correctedVocabularyTerm = corrected,
        )

    @Test
    fun apply_addsReplacementsAndVocabulary_withDedupe() = runBlocking {
        dictionary.dictionary = DictionaryBackup(
            replacements = listOf(Replacement(listOf("voice ink"), "VoiceInk"), Replacement(listOf("teh"), "the")),
            vocabulary = listOf("voiceink"),
        )

        val summary = store.applyProposals(
            listOf(
                proposal(LearningAction.ADD_REPLACEMENT_AND_VOCABULARY, "vois ink", "VoiceInk"),
                proposal(LearningAction.ADD_REPLACEMENT_AND_VOCABULARY, "john smith", "John Smyth"),
                proposal(LearningAction.ADD_REPLACEMENT_ONLY, "TEH", "thee"),
                proposal(LearningAction.ADD_VOCABULARY_ONLY, null, "Shekohex"),
            )
        )

        assertEquals(
            listOf(
                Replacement(listOf("voice ink", "vois ink"), "VoiceInk"),
                Replacement(listOf("teh"), "the"),
                Replacement(listOf("john smith"), "John Smyth"),
            ),
            dictionary.dictionary.replacements,
        )
        assertEquals(listOf("voiceink", "John Smyth", "Shekohex"), dictionary.dictionary.vocabulary)
        assertEquals(1, summary.createdCount)
        assertEquals(1, summary.updatedCount)
        assertEquals(2, summary.vocabularyCount)
    }

    @Test
    fun apply_refusesReplacementCycles() = runBlocking {
        dictionary.dictionary = DictionaryBackup(replacements = listOf(Replacement(listOf("colour"), "color")))
        val summary = store.applyProposals(listOf(proposal(LearningAction.ADD_REPLACEMENT_ONLY, "color", "colour")))
        assertFalse(summary.hasChanges)
        assertEquals(listOf(Replacement(listOf("colour"), "color")), dictionary.dictionary.replacements)
    }

    @Test
    fun undo_revertsExactlyWhatWasAdded_withinWindow() = runBlocking {
        val before = DictionaryBackup(
            replacements = listOf(Replacement(listOf("voice ink"), "VoiceInk")),
            vocabulary = listOf("Kotlin"),
        )
        dictionary.dictionary = before
        val summary = store.applyProposals(
            listOf(
                proposal(LearningAction.ADD_REPLACEMENT_AND_VOCABULARY, "vois ink", "VoiceInk"),
                proposal(LearningAction.ADD_REPLACEMENT_AND_VOCABULARY, "john smith", "John Smyth"),
            )
        )

        now += AutoLearnMutationSummary.UNDO_WINDOW_MS
        assertTrue(store.undo(summary))
        assertEquals(before, dictionary.dictionary)
    }

    @Test
    fun undo_afterWindow_isRefused() = runBlocking {
        val summary = store.applyProposals(listOf(proposal(LearningAction.ADD_VOCABULARY_ONLY, null, "Shekohex")))
        now += AutoLearnMutationSummary.UNDO_WINDOW_MS + 1
        assertFalse(store.undo(summary))
        assertEquals(listOf("Shekohex"), dictionary.dictionary.vocabulary)
    }

    @Test
    fun storedProposals_addDedupeUpdateApplyDismiss() = runBlocking {
        val first = proposal(LearningAction.ADD_REPLACEMENT_AND_VOCABULARY, "vois ink", "VoiceInk", id = "p1")
        val second = proposal(LearningAction.ADD_VOCABULARY_ONLY, null, "Shekohex", id = "p2")
        assertEquals(2, store.add(listOf(first, second, first.copy(id = "dup"))))
        assertEquals(2, store.count.first())

        store.updateAction("p1", LearningAction.ADD_REPLACEMENT_ONLY)
        store.updateAction("p2", LearningAction.ADD_REPLACEMENT_ONLY) // no source text: ignored
        assertEquals(
            listOf(LearningAction.ADD_REPLACEMENT_ONLY, LearningAction.ADD_VOCABULARY_ONLY),
            store.all().map { it.learningAction },
        )

        val summary = store.applyStored(listOf("p1"))
        assertEquals(1, summary.createdCount)
        assertEquals(0, summary.vocabularyCount)
        assertEquals(listOf("p2"), store.all().map { it.id })

        store.dismiss(listOf("p2"))
        assertEquals(0, store.count.first())
    }

    @Test
    fun proposalsFrom_dropsRejectionsAndUnknownIds() {
        val candidates = listOf(AutoLearnCandidate("a", "teh cat", "the cat", 0L))
        val decisions = listOf(
            AutoLearnDecision("a", LearningAction.ADD_REPLACEMENT_ONLY, "teh", "the"),
            AutoLearnDecision("a", LearningAction.REJECT, null, null),
            AutoLearnDecision("zzz", LearningAction.ADD_VOCABULARY_ONLY, null, "x"),
        )
        val proposals = AutoLearnProposalStore.proposalsFrom(decisions, candidates)
        assertEquals(1, proposals.size)
        assertEquals("teh cat", proposals.single().originalText)
    }
}
