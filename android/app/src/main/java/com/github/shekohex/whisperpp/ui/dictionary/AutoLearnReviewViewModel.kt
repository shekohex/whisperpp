package com.github.shekohex.whisperpp.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnProposalStore
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnCoordinator
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnMutationSummary
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewProposal
import com.github.shekohex.whisperpp.domain.autolearn.LearningAction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/** Review sheet (§4.11 step 5): per-proposal action chips, dismiss, "Apply all" and its undo. */
class AutoLearnReviewViewModel(
    private val coordinator: AutoLearnCoordinator,
) : ViewModel() {

    /** The sheet's chip choices; [DISMISS] drops the proposal without learning anything. */
    enum class Choice(val action: LearningAction?) {
        REPLACEMENT(LearningAction.ADD_REPLACEMENT_ONLY),
        VOCABULARY(LearningAction.ADD_VOCABULARY_ONLY),
        BOTH(LearningAction.ADD_REPLACEMENT_AND_VOCABULARY),
        DISMISS(null),
    }

    val proposals: StateFlow<List<AutoLearnReviewProposal>?> = coordinator.proposals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    suspend fun choose(proposal: AutoLearnReviewProposal, choice: Choice) {
        val action = choice.action
        if (action == null) {
            coordinator.dismissProposals(listOf(proposal.id))
        } else {
            coordinator.updateProposalAction(proposal.id, action)
        }
    }

    /** Applies every remaining proposal; the coordinator emits `Learned` for the snackbar. */
    suspend fun applyAll(): AutoLearnMutationSummary? {
        val ids = coordinator.proposals.first().map { it.id }
        if (ids.isEmpty()) return null
        return coordinator.applyProposals(ids).takeIf { it.hasChanges }
    }

    suspend fun undo(summary: AutoLearnMutationSummary): Boolean = coordinator.undo(summary)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun choiceOf(action: LearningAction): Choice = when (action) {
            LearningAction.ADD_REPLACEMENT_ONLY -> Choice.REPLACEMENT
            LearningAction.ADD_VOCABULARY_ONLY -> Choice.VOCABULARY
            LearningAction.ADD_REPLACEMENT_AND_VOCABULARY -> Choice.BOTH
            LearningAction.REJECT -> Choice.DISMISS
        }

        fun isAllowed(proposal: AutoLearnReviewProposal, choice: Choice): Boolean =
            choice.action?.let { AutoLearnProposalStore.isActionAllowed(proposal, it) } ?: true
    }
}
