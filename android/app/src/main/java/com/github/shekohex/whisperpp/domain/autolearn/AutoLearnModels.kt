package com.github.shekohex.whisperpp.domain.autolearn

import java.util.UUID

/** Field text right after a dictation was inserted ([original]) and after the user edited it ([corrected]). */
data class AutoLearnRevision(
    val original: String,
    val corrected: String,
    val capturedAt: Long,
    val appPackage: String? = null,
)

/** One edited region: the changed words plus up to three unchanged words on each side. */
data class DetectedCorrectionCandidate(
    val id: String = UUID.randomUUID().toString(),
    val originalText: String,
    val correctedText: String,
)

/** A candidate persisted in the pending queue. [claimed] is true while a review batch owns it. */
data class AutoLearnCandidate(
    val id: String,
    val originalText: String,
    val correctedText: String,
    val enqueuedAt: Long,
    val claimed: Boolean = false,
)

/** Reviewer verdict; [wireName] is the JSON value of the prompt's output contract. */
enum class LearningAction(val wireName: String) {
    ADD_REPLACEMENT_AND_VOCABULARY("addReplacementAndVocabulary"),
    ADD_REPLACEMENT_ONLY("addReplacementOnly"),
    ADD_VOCABULARY_ONLY("addVocabularyOnly"),
    REJECT("rejectCorrection"),
    ;

    val addsReplacement: Boolean
        get() = this == ADD_REPLACEMENT_AND_VOCABULARY || this == ADD_REPLACEMENT_ONLY

    val addsVocabulary: Boolean
        get() = this == ADD_REPLACEMENT_AND_VOCABULARY || this == ADD_VOCABULARY_ONLY

    companion object {
        fun fromWireName(value: String): LearningAction? = entries.firstOrNull { it.wireName == value }
    }
}

/** A validated reviewer decision for queue candidate [candidateId]. */
data class AutoLearnDecision(
    val candidateId: String,
    val learningAction: LearningAction,
    val incorrectTextToReplace: String?,
    val correctedVocabularyTerm: String?,
)

enum class AutoLearnUnresolvedReason {
    MISSING_DECISION,
    CONFLICTING_DECISIONS,
    MISSING_REQUIRED_ACTION_VALUES,
    INVALID_REQUIRED_ACTION_VALUES,
}

data class AutoLearnUnresolvedReview(
    val candidateId: String,
    val reason: AutoLearnUnresolvedReason,
)

data class AutoLearnReviewResult(
    val decisions: List<AutoLearnDecision>,
    val unresolved: List<AutoLearnUnresolvedReview>,
) {
    val accepted: List<AutoLearnDecision>
        get() = decisions.filter { it.learningAction != LearningAction.REJECT }
}

/** An accepted decision with its candidate context, waiting to be applied (or reviewed by the user). */
data class AutoLearnReviewProposal(
    val id: String = UUID.randomUUID().toString(),
    val candidateId: String,
    val originalText: String,
    val correctedText: String,
    val learningAction: LearningAction,
    val incorrectTextToReplace: String?,
    val correctedVocabularyTerm: String,
)

/** One dictionary change made by Auto-Learn, recorded precisely enough to revert it. */
data class AutoLearnAppliedCorrection(
    /** Original added to a replacement rule, or null when only vocabulary changed. */
    val addedOriginal: String?,
    val replacement: String,
    /** True when a new rule was created for [replacement] instead of extending an existing one. */
    val createdRule: Boolean,
    /** Vocabulary term that did not exist before, or null. */
    val addedVocabulary: String?,
)

/** Result of applying proposals; [undo][AutoLearnDictionaryMutation.undo] is allowed for [UNDO_WINDOW_MS]. */
data class AutoLearnMutationSummary(
    val appliedAt: Long,
    val corrections: List<AutoLearnAppliedCorrection>,
) {
    val createdCount: Int
        get() = corrections.count { it.addedOriginal != null && it.createdRule }

    val updatedCount: Int
        get() = corrections.count { it.addedOriginal != null && !it.createdRule }

    val vocabularyCount: Int
        get() = corrections.count { it.addedVocabulary != null }

    val hasChanges: Boolean
        get() = corrections.isNotEmpty()

    fun canUndo(now: Long): Boolean = hasChanges && now - appliedAt in 0..UNDO_WINDOW_MS

    companion object {
        const val UNDO_WINDOW_MS = 30_000L
    }
}

enum class AutoLearnReviewSchedule {
    IMMEDIATELY,
    DAILY,
    MANUALLY,
}

/** Why a review was requested; [MANUAL] is the user's "Review now" and ignores the schedule. */
enum class AutoLearnReviewTrigger {
    AUTOMATIC,
    MANUAL,
}

sealed interface AutoLearnEvent {
    data class Learned(val summary: AutoLearnMutationSummary) : AutoLearnEvent
    data class ProposalsReady(val count: Int) : AutoLearnEvent
    data class Failed(val error: Throwable) : AutoLearnEvent
}

internal object AutoLearnLimits {
    const val MAX_DIFF_SEGMENTS = 400
    const val MAX_CANDIDATES_PER_REVISION = 20
    const val MAX_CANDIDATE_CHARACTERS = 256
    const val MAX_CANDIDATE_SEGMENTS = 24
    const val REVIEW_CONTEXT_SEGMENTS_PER_SIDE = 3
    const val MAX_UNSPACED_CANDIDATE_CHARACTERS = 32
    const val DAILY_INTERVAL_MS = 24L * 60 * 60 * 1000
}
