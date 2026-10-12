package com.github.shekohex.whisperpp.data.autolearn

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.github.shekohex.whisperpp.data.dictionary.DictionaryBackup
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnCandidate
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnDecision
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnDictionaryMutation
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnMutationSummary
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewProposal
import com.github.shekohex.whisperpp.domain.autolearn.LearningAction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Atomic read-modify-write of the dictionary (replacements + vocabulary). */
fun interface AutoLearnDictionaryEditor {
    suspend fun edit(transform: (DictionaryBackup) -> DictionaryBackup)
}

/**
 * Accepted review proposals waiting for the user ("Daily"/"Manually", §4.11 step 5), and the
 * apply/undo path into the dictionary for every schedule.
 */
class AutoLearnProposalStore(
    private val dataStore: DataStore<Preferences>,
    private val dictionary: AutoLearnDictionaryEditor,
    private val clock: () -> Long = System::currentTimeMillis,
    private val gson: Gson = Gson(),
) {
    val proposals: Flow<List<AutoLearnReviewProposal>> = dataStore.data
        .map { read(it) }
        .distinctUntilChanged()

    val count: Flow<Int> = proposals.map { it.size }.distinctUntilChanged()

    suspend fun all(): List<AutoLearnReviewProposal> = proposals.first()

    /** Stores [proposals], skipping duplicates of stored ones; returns how many were added. */
    suspend fun add(proposals: List<AutoLearnReviewProposal>): Int {
        if (proposals.isEmpty()) return 0
        var added = 0
        edit { current ->
            val known = current.mapTo(HashSet()) { it.key() }
            val additions = proposals.filter { it.learningAction != LearningAction.REJECT && known.add(it.key()) }
            added = additions.size
            current + additions
        }
        return added
    }

    /** Changes what a stored proposal will learn (the review sheet's Replacement / Vocabulary / Both chips). */
    suspend fun updateAction(proposalId: String, action: LearningAction) {
        edit { current ->
            current.map { proposal ->
                if (proposal.id != proposalId || !isActionAllowed(proposal, action)) proposal else proposal.copy(learningAction = action)
            }
        }
    }

    suspend fun dismiss(proposalIds: Collection<String>) {
        if (proposalIds.isEmpty()) return
        val ids = proposalIds.toSet()
        edit { current -> current.filterNot { it.id in ids } }
    }

    /** Applies the stored proposals with [proposalIds] and removes them from the store. */
    suspend fun applyStored(proposalIds: Collection<String>): AutoLearnMutationSummary {
        val ids = proposalIds.toSet()
        val selected = all().filter { it.id in ids }
        val summary = applyProposals(selected)
        dismiss(ids)
        return summary
    }

    /** Writes [proposals] into the dictionary; the summary lets [undo] revert them. */
    suspend fun applyProposals(proposals: List<AutoLearnReviewProposal>): AutoLearnMutationSummary {
        val now = clock()
        var summary = AutoLearnMutationSummary(appliedAt = now, corrections = emptyList())
        if (proposals.isEmpty()) return summary
        dictionary.edit { current ->
            val outcome = AutoLearnDictionaryMutation.apply(current, proposals, now)
            summary = outcome.summary
            outcome.dictionary
        }
        return summary
    }

    /** Reverts [summary] when still inside its undo window; returns whether anything was undone. */
    suspend fun undo(summary: AutoLearnMutationSummary): Boolean {
        if (!summary.canUndo(clock())) return false
        dictionary.edit { current -> AutoLearnDictionaryMutation.undo(current, summary) }
        return true
    }

    suspend fun clear() {
        dataStore.edit { it.remove(PreferenceKeys.AutoLearn.PROPOSALS_JSON) }
    }

    private suspend fun edit(transform: (List<AutoLearnReviewProposal>) -> List<AutoLearnReviewProposal>) {
        dataStore.edit { prefs ->
            val current = read(prefs)
            val updated = transform(current)
            if (updated != current) prefs[PreferenceKeys.AutoLearn.PROPOSALS_JSON] = gson.toJson(updated)
        }
    }

    private fun read(prefs: Preferences): List<AutoLearnReviewProposal> {
        val raw = prefs[PreferenceKeys.AutoLearn.PROPOSALS_JSON]?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val decoded = try {
            gson.fromJson<List<AutoLearnReviewProposal?>>(raw, PROPOSAL_LIST_TYPE).orEmpty()
        } catch (_: Exception) {
            return emptyList()
        }
        return decoded.mapNotNull { it?.sanitized() }
    }

    private fun AutoLearnReviewProposal.sanitized(): AutoLearnReviewProposal? {
        // Gson leaves missing fields null despite the Kotlin types.
        val action = (learningAction as LearningAction?)?.takeIf { it != LearningAction.REJECT } ?: return null
        val corrected = (correctedVocabularyTerm as String?)?.takeIf { it.isNotBlank() } ?: return null
        val proposal = AutoLearnReviewProposal(
            id = (id as String?)?.takeIf { it.isNotBlank() } ?: return null,
            candidateId = (candidateId as String?).orEmpty(),
            originalText = (originalText as String?).orEmpty(),
            correctedText = (correctedText as String?).orEmpty(),
            learningAction = action,
            incorrectTextToReplace = incorrectTextToReplace,
            correctedVocabularyTerm = corrected,
        )
        return proposal.takeIf { isActionAllowed(it, action) }
    }

    private fun AutoLearnReviewProposal.key(): String =
        listOf(learningAction.name, incorrectTextToReplace.orEmpty().lowercase(), correctedVocabularyTerm.lowercase())
            .joinToString("\u0000")

    companion object {
        private val PROPOSAL_LIST_TYPE = object : TypeToken<List<AutoLearnReviewProposal?>>() {}.type

        /** Replacement actions need a source text; rejection is never stored. */
        fun isActionAllowed(proposal: AutoLearnReviewProposal, action: LearningAction): Boolean = when (action) {
            LearningAction.REJECT -> false
            LearningAction.ADD_VOCABULARY_ONLY -> true
            LearningAction.ADD_REPLACEMENT_ONLY, LearningAction.ADD_REPLACEMENT_AND_VOCABULARY ->
                !proposal.incorrectTextToReplace.isNullOrBlank()
        }

        /** Accepted [decisions] joined with their queue [candidates]; rejections and unknown IDs are dropped. */
        fun proposalsFrom(decisions: List<AutoLearnDecision>, candidates: List<AutoLearnCandidate>): List<AutoLearnReviewProposal> {
            val byId = candidates.associateBy { it.id }
            return decisions.mapNotNull { decision ->
                val candidate = byId[decision.candidateId] ?: return@mapNotNull null
                val corrected = decision.correctedVocabularyTerm ?: return@mapNotNull null
                if (decision.learningAction == LearningAction.REJECT) return@mapNotNull null
                AutoLearnReviewProposal(
                    candidateId = candidate.id,
                    originalText = candidate.originalText,
                    correctedText = candidate.correctedText,
                    learningAction = decision.learningAction,
                    incorrectTextToReplace = decision.incorrectTextToReplace,
                    correctedVocabularyTerm = corrected,
                )
            }
        }
    }
}
