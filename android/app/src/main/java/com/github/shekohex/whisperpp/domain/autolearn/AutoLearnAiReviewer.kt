package com.github.shekohex.whisperpp.domain.autolearn

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.PromptComposer
import com.github.shekohex.whisperpp.domain.providers.EnhancementProvider
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The text model Auto-Learn reviews with, resolved from settings for each batch. */
data class AutoLearnReviewModel(
    val provider: EnhancementProvider,
    val thinking: ThinkingConfig = ThinkingConfig.Disabled,
)

/**
 * Sends a batch of queued candidates to the configured text model with VoiceInk's review prompt
 * (`res/raw/autolearn_review_prompt.txt`, verbatim) and validates every decision it returns.
 * Only the candidate snippets leave the device. Never logs candidate or response text.
 */
class AutoLearnAiReviewer(
    private val prompt: () -> String,
    private val resolveModel: suspend () -> Result<AutoLearnReviewModel>,
    /** Metadata-only diagnostics (counts, provider id); wired to `android.util.Log` by the container. */
    private val log: (String) -> Unit = {},
) {
    suspend fun review(candidates: List<AutoLearnCandidate>): Result<AutoLearnReviewResult> {
        if (candidates.isEmpty()) return Result.success(AutoLearnReviewResult(emptyList(), emptyList()))
        val model = resolveModel().getOrElse { return Result.failure(it) }
        val request = EnhancementRequest(
            systemPrompt = prompt(),
            userText = buildPayload(candidates),
            thinking = model.thinking,
            timeoutMs = REVIEW_TIMEOUT_MS,
        )
        log("Auto-Learn review started provider=${model.provider.id} candidates=${candidates.size}")
        val response = try {
            model.provider.enhance(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }.getOrElse { return Result.failure(it) }

        val result = parseAndValidate(response, candidates)
        result.onSuccess {
            log("Auto-Learn review done accepted=${it.accepted.size} unresolved=${it.unresolved.size}")
        }.onFailure {
            log("Auto-Learn review response invalid (${response.length} chars)")
        }
        return result
    }

    internal companion object {
        const val REVIEW_TIMEOUT_MS = 60_000L

        private val CODE_FENCE = Regex("^```[a-zA-Z0-9_-]*\\s*\\n?([\\s\\S]*?)\\n?```\\s*$")
        private val REQUIRED_KEYS = setOf(
            "candidateID",
            "learningAction",
            "incorrectTextToReplace",
            "correctedVocabularyTerm",
        )

        /** `{"candidatesForReview":[{candidateID, originalText, correctedText}]}`; IDs are batch indices. */
        fun buildPayload(candidates: List<AutoLearnCandidate>): String {
            val items = JSONArray()
            candidates.forEachIndexed { index, candidate ->
                items.put(
                    JSONObject()
                        .put("candidateID", index)
                        .put("originalText", candidate.originalText)
                        .put("correctedText", candidate.correctedText)
                )
            }
            return JSONObject().put("candidatesForReview", items).toString()
        }

        fun parseAndValidate(response: String, candidates: List<AutoLearnCandidate>): Result<AutoLearnReviewResult> {
            val raw = try {
                parseDecisions(response)
            } catch (e: JSONException) {
                return Result.failure(NetworkError.InvalidResponse("auto-learn review JSON", e))
            } catch (e: IllegalArgumentException) {
                return Result.failure(NetworkError.InvalidResponse("auto-learn review JSON", e))
            }
            return Result.success(validate(raw, candidates))
        }

        /** Model output with reasoning blocks and a wrapping Markdown code fence removed. */
        fun stripWrappers(response: String): String {
            val withoutReasoning = PromptComposer.cleanEnhancementOutput(response).orEmpty()
            val fenced = CODE_FENCE.matchEntire(withoutReasoning.trim())
            return (fenced?.groupValues?.get(1) ?: withoutReasoning).trim()
        }

        internal data class RawDecision(
            val batchIndex: Int,
            val action: LearningAction,
            val incorrectTextToReplace: String?,
            val correctedVocabularyTerm: String?,
        )

        private fun parseDecisions(response: String): List<RawDecision> {
            val payload = stripWrappers(response)
            require(payload.startsWith("[")) { "expected a top-level JSON array" }
            val array = JSONArray(payload)
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val keys = item.keys().asSequence().toSet()
                require(keys == REQUIRED_KEYS) { "decision must contain exactly the four fields" }
                val id = item.get("candidateID")
                require(id is Number && id.toDouble() == id.toInt().toDouble()) { "candidateID must be an integer" }
                val action = LearningAction.fromWireName(item.getString("learningAction"))
                    ?: throw IllegalArgumentException("unknown learningAction")
                RawDecision(
                    batchIndex = id.toInt(),
                    action = action,
                    incorrectTextToReplace = item.optionalString("incorrectTextToReplace"),
                    correctedVocabularyTerm = item.optionalString("correctedVocabularyTerm"),
                )
            }
        }

        private fun JSONObject.optionalString(key: String): String? {
            if (isNull(key)) return null
            val value = get(key)
            require(value is String) { "$key must be a string or null" }
            return value
        }

        fun validate(raw: List<RawDecision>, candidates: List<AutoLearnCandidate>): AutoLearnReviewResult {
            // Unknown batch indices are ignored.
            val byIndex = raw.filter { it.batchIndex in candidates.indices }.groupBy { it.batchIndex }
            val correctedContexts = candidates.map { it.correctedText }
            val decisions = ArrayList<AutoLearnDecision>()
            val unresolved = ArrayList<AutoLearnUnresolvedReview>()

            candidates.forEachIndexed { index, candidate ->
                val matching = byIndex[index]
                if (matching.isNullOrEmpty()) {
                    unresolved += AutoLearnUnresolvedReview(candidate.id, AutoLearnUnresolvedReason.MISSING_DECISION)
                    return@forEachIndexed
                }
                // Several decisions may split adjacent terms, but acceptance never mixes with rejection.
                if (matching.size > 1 && matching.any { it.action == LearningAction.REJECT }) {
                    unresolved += AutoLearnUnresolvedReview(candidate.id, AutoLearnUnresolvedReason.CONFLICTING_DECISIONS)
                    return@forEachIndexed
                }
                val validated = ArrayList<AutoLearnDecision>()
                for (decision in matching) {
                    when (val outcome = validateOne(decision, candidate, correctedContexts)) {
                        is Validation.Valid -> validated += outcome.decision
                        is Validation.Invalid -> {
                            unresolved += AutoLearnUnresolvedReview(candidate.id, outcome.reason)
                            return@forEachIndexed
                        }
                    }
                }
                if (!decisionsAreIndependent(validated, candidate)) {
                    unresolved += AutoLearnUnresolvedReview(candidate.id, AutoLearnUnresolvedReason.CONFLICTING_DECISIONS)
                } else {
                    decisions += validated
                }
            }
            return AutoLearnReviewResult(decisions, unresolved)
        }

        private sealed interface Validation {
            data class Valid(val decision: AutoLearnDecision) : Validation
            data class Invalid(val reason: AutoLearnUnresolvedReason) : Validation
        }

        private fun validateOne(
            decision: RawDecision,
            candidate: AutoLearnCandidate,
            correctedContexts: List<String>,
        ): Validation {
            val reject = Validation.Valid(AutoLearnDecision(candidate.id, LearningAction.REJECT, null, null))
            if (decision.action == LearningAction.REJECT) return reject

            val corrected = decision.correctedVocabularyTerm?.trim()
                ?: return Validation.Invalid(AutoLearnUnresolvedReason.MISSING_REQUIRED_ACTION_VALUES)
            // Batch canonicalization may copy the term from another candidate's correctedText.
            if (corrected.isEmpty() ||
                corrected.length > AutoLearnLimits.MAX_CANDIDATE_CHARACTERS ||
                correctedContexts.none { it.contains(corrected) }
            ) {
                return Validation.Invalid(AutoLearnUnresolvedReason.INVALID_REQUIRED_ACTION_VALUES)
            }
            if (decision.action == LearningAction.ADD_VOCABULARY_ONLY) {
                return Validation.Valid(AutoLearnDecision(candidate.id, decision.action, null, corrected))
            }

            val incorrect = decision.incorrectTextToReplace?.trim()
                ?: return Validation.Invalid(AutoLearnUnresolvedReason.MISSING_REQUIRED_ACTION_VALUES)
            if (incorrect.isEmpty() ||
                incorrect == corrected ||
                incorrect.length > AutoLearnLimits.MAX_CANDIDATE_CHARACTERS ||
                incorrect.contains(',') ||
                !candidate.originalText.contains(incorrect, ignoreCase = true)
            ) {
                return Validation.Invalid(AutoLearnUnresolvedReason.INVALID_REQUIRED_ACTION_VALUES)
            }
            if (incorrect.equals(corrected, ignoreCase = true)) return reject
            return Validation.Valid(AutoLearnDecision(candidate.id, decision.action, incorrect, corrected))
        }

        private fun decisionsAreIndependent(decisions: List<AutoLearnDecision>, candidate: AutoLearnCandidate): Boolean {
            val originals = decisions.mapNotNull { it.incorrectTextToReplace }
            if (!canLocateWithoutOverlap(originals, candidate.originalText, ignoreCase = true)) return false
            val localCorrected = decisions.mapNotNull { it.correctedVocabularyTerm }
                .filter { candidate.correctedText.contains(it) }
            return canLocateWithoutOverlap(localCorrected, candidate.correctedText, ignoreCase = false)
        }

        private fun canLocateWithoutOverlap(terms: List<String>, text: String, ignoreCase: Boolean): Boolean {
            if (terms.size <= 1) return true
            val rangesByTerm = terms.map { term -> occurrences(term, text, ignoreCase) }

            fun assign(termIndex: Int, occupied: List<IntRange>): Boolean {
                if (termIndex == rangesByTerm.size) return true
                return rangesByTerm[termIndex].any { range ->
                    occupied.none { it.first <= range.last && range.first <= it.last } &&
                        assign(termIndex + 1, occupied + listOf(range))
                }
            }
            return assign(0, emptyList())
        }

        private fun occurrences(term: String, text: String, ignoreCase: Boolean): List<IntRange> {
            if (term.isEmpty()) return emptyList()
            val ranges = ArrayList<IntRange>()
            var from = text.indexOf(term, 0, ignoreCase)
            while (from >= 0) {
                ranges += from until from + term.length
                from = text.indexOf(term, from + 1, ignoreCase)
            }
            return ranges
        }
    }
}
