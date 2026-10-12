package com.github.shekohex.whisperpp.domain.autolearn

import com.github.shekohex.whisperpp.data.autolearn.AutoLearnPendingQueue
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnProposalStore
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** What one [AutoLearnCoordinator.reviewPendingIfDue] call did. */
sealed interface AutoLearnReviewOutcome {
    data object Disabled : AutoLearnReviewOutcome
    data object NotDue : AutoLearnReviewOutcome
    data object Busy : AutoLearnReviewOutcome
    data object NothingPending : AutoLearnReviewOutcome
    data class Completed(
        val reviewed: Int,
        val learned: AutoLearnMutationSummary?,
        val proposalsAdded: Int,
    ) : AutoLearnReviewOutcome
    data class Failed(val error: Throwable) : AutoLearnReviewOutcome
}

/**
 * Auto-Learn pipeline (§4.11 steps 2–5): revision → [CorrectionDiffEngine] → [AutoLearnPendingQueue]
 * → [AutoLearnAiReviewer] → dictionary ("Immediately") or [AutoLearnProposalStore] ("Daily" /
 * "Manually"). Off unless [AutoLearnSettings.enabled]. One review run at a time; a run drains the
 * queue in batches and releases the current batch back to the queue when it fails.
 */
class AutoLearnCoordinator(
    private val settings: Flow<AutoLearnSettings>,
    private val markReviewed: suspend (Long) -> Unit,
    private val queue: AutoLearnPendingQueue,
    private val reviewer: AutoLearnAiReviewer,
    private val proposalStore: AutoLearnProposalStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val runLock = Mutex()
    private val eventFlow = MutableSharedFlow<AutoLearnEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Learned (show "Learned N corrections" + Undo), ProposalsReady (badge), Failed (surface error). */
    val events: SharedFlow<AutoLearnEvent> = eventFlow.asSharedFlow()

    /** Candidates waiting for AI review ("Corrections to review: N"). */
    val pendingCount: Flow<Int> = queue.pendingCount

    /** Reviewed proposals waiting for the user. */
    val proposals: Flow<List<AutoLearnReviewProposal>> = proposalStore.proposals
    val proposalCount: Flow<Int> = proposalStore.count

    /** Diffs [revision] and queues its candidates; returns how many were queued (0 when disabled). */
    suspend fun submitRevision(revision: AutoLearnRevision): Int {
        if (!settings.first().enabled) return 0
        val candidates = CorrectionDiffEngine.candidates(revision)
        if (candidates.isEmpty()) return 0
        val added = queue.enqueue(candidates)
        log("Auto-Learn queued $added candidates")
        return added
    }

    /** Fire-and-forget for the IME: queue [revision], then review if the schedule says so. */
    fun onRevisionCaptured(revision: AutoLearnRevision): Job = scope.launch {
        if (submitRevision(revision) > 0) reviewPendingIfDue(AutoLearnReviewTrigger.AUTOMATIC)
    }

    /** [reviewPendingIfDue] on the coordinator scope (e.g. "Review now" or app start). */
    fun requestReview(trigger: AutoLearnReviewTrigger): Job = scope.launch { reviewPendingIfDue(trigger) }

    /** Runs a review when enabled and due; [AutoLearnReviewTrigger.MANUAL] ignores the schedule. */
    suspend fun reviewPendingIfDue(trigger: AutoLearnReviewTrigger): AutoLearnReviewOutcome {
        if (!runLock.tryLock()) return AutoLearnReviewOutcome.Busy
        try {
            val current = settings.first()
            if (!current.enabled) return AutoLearnReviewOutcome.Disabled
            if (!isDue(current, trigger, clock())) return AutoLearnReviewOutcome.NotDue
            // No run is active while we hold the lock, so any claim is stale (e.g. process death).
            queue.releaseAll()
            return runReview(current.reviewSchedule)
        } finally {
            runLock.unlock()
        }
    }

    /** Applies stored proposals chosen in the review sheet. */
    suspend fun applyProposals(proposalIds: Collection<String>): AutoLearnMutationSummary {
        val summary = proposalStore.applyStored(proposalIds)
        if (summary.hasChanges) eventFlow.tryEmit(AutoLearnEvent.Learned(summary))
        return summary
    }

    suspend fun updateProposalAction(proposalId: String, action: LearningAction) =
        proposalStore.updateAction(proposalId, action)

    suspend fun dismissProposals(proposalIds: Collection<String>) = proposalStore.dismiss(proposalIds)

    /** Reverts a [AutoLearnEvent.Learned] summary within its 30 s window. */
    suspend fun undo(summary: AutoLearnMutationSummary): Boolean = proposalStore.undo(summary)

    private suspend fun runReview(schedule: AutoLearnReviewSchedule): AutoLearnReviewOutcome {
        var reviewed = 0
        var proposalsAdded = 0
        val learned = ArrayList<AutoLearnAppliedCorrection>()
        var failure: Throwable? = null

        while (true) {
            val batch = queue.claimBatch(AutoLearnPendingQueue.BATCH_SIZE)
            if (batch.isEmpty()) break
            val ids = batch.map { it.id }
            var finished = false
            try {
                val result = reviewer.review(batch).getOrElse { error ->
                    failure = error
                    null
                } ?: break
                val accepted = AutoLearnProposalStore.proposalsFrom(result.decisions, batch)
                if (schedule == AutoLearnReviewSchedule.IMMEDIATELY) {
                    learned += proposalStore.applyProposals(accepted).corrections
                } else {
                    proposalsAdded += proposalStore.add(accepted)
                }
                // Unresolved candidates are discarded with the batch, as in VoiceInk.
                queue.remove(ids)
                reviewed += batch.size
                finished = true
            } finally {
                if (!finished) withContext(NonCancellable) { queue.release(ids) }
            }
        }

        if (reviewed > 0) markReviewed(clock())
        val summary = learned.takeIf { it.isNotEmpty() }?.let { AutoLearnMutationSummary(clock(), it) }
        summary?.let { eventFlow.tryEmit(AutoLearnEvent.Learned(it)) }
        if (proposalsAdded > 0) eventFlow.tryEmit(AutoLearnEvent.ProposalsReady(proposalStore.all().size))
        log("Auto-Learn review run reviewed=$reviewed learned=${learned.size} proposals=$proposalsAdded")

        failure?.let { error ->
            eventFlow.tryEmit(AutoLearnEvent.Failed(error))
            return AutoLearnReviewOutcome.Failed(error)
        }
        if (reviewed == 0) return AutoLearnReviewOutcome.NothingPending
        return AutoLearnReviewOutcome.Completed(reviewed, summary, proposalsAdded)
    }

    internal companion object {
        fun isDue(settings: AutoLearnSettings, trigger: AutoLearnReviewTrigger, now: Long): Boolean {
            if (trigger == AutoLearnReviewTrigger.MANUAL) return true
            return when (settings.reviewSchedule) {
                AutoLearnReviewSchedule.IMMEDIATELY -> true
                AutoLearnReviewSchedule.DAILY -> {
                    val elapsed = now - settings.lastReviewAt
                    // A clock moved backwards should not block reviews for a day.
                    elapsed >= AutoLearnLimits.DAILY_INTERVAL_MS || elapsed < 0
                }
                AutoLearnReviewSchedule.MANUALLY -> false
            }
        }
    }
}
