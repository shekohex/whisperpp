package com.github.shekohex.whisperpp.domain.autolearn

import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.data.dictionary.DictionaryBackup
import java.text.Normalizer

/**
 * Pure dictionary edits for Auto-Learn (port of VoiceInk `WordReplacementStore.apply/undo`).
 * Learning only ever adds: an original that already has a rule is left alone, a learned original
 * joins an existing rule with the same replacement, and vocabulary is deduplicated
 * case-insensitively. [undo] removes exactly what [apply] recorded.
 */
object AutoLearnDictionaryMutation {

    data class Outcome(val dictionary: DictionaryBackup, val summary: AutoLearnMutationSummary)

    fun apply(dictionary: DictionaryBackup, proposals: List<AutoLearnReviewProposal>, now: Long): Outcome {
        val rules = dictionary.replacements.toMutableList()
        val vocabulary = dictionary.vocabulary.toMutableList()
        val corrections = ArrayList<AutoLearnAppliedCorrection>()

        for (proposal in proposals) {
            val action = proposal.learningAction
            if (action == LearningAction.REJECT) continue
            val corrected = normalize(proposal.correctedVocabularyTerm)
            if (corrected.isEmpty()) continue

            var addedOriginal: String? = null
            var createdRule = false
            if (action.addsReplacement) {
                val original = normalize(proposal.incorrectTextToReplace.orEmpty())
                if (canAddReplacement(rules, original, corrected)) {
                    val index = rules.indexOfFirst { it.replacement == corrected }
                    if (index >= 0) {
                        rules[index] = rules[index].copy(originals = rules[index].originals + original)
                    } else {
                        rules += Replacement(listOf(original), corrected)
                        createdRule = true
                    }
                    addedOriginal = original
                }
            }

            var addedVocabulary: String? = null
            if (action.addsVocabulary && vocabulary.none { it.equals(corrected, ignoreCase = true) }) {
                vocabulary += corrected
                addedVocabulary = corrected
            }

            if (addedOriginal != null || addedVocabulary != null) {
                corrections += AutoLearnAppliedCorrection(addedOriginal, corrected, createdRule, addedVocabulary)
            }
        }

        val updated = dictionary.copy(replacements = rules, vocabulary = vocabulary)
        return Outcome(updated, AutoLearnMutationSummary(appliedAt = now, corrections = corrections))
    }

    fun undo(dictionary: DictionaryBackup, summary: AutoLearnMutationSummary): DictionaryBackup {
        var rules = dictionary.replacements
        var vocabulary = dictionary.vocabulary
        for (correction in summary.corrections.asReversed()) {
            correction.addedOriginal?.let { original ->
                val index = rules.indexOfFirst { rule ->
                    rule.replacement == correction.replacement &&
                        rule.originals.any { it.equals(original, ignoreCase = true) }
                }
                if (index >= 0) {
                    val remaining = rules[index].originals.filterNot { it.equals(original, ignoreCase = true) }
                    rules = if (remaining.isEmpty()) {
                        rules.filterIndexed { i, _ -> i != index }
                    } else {
                        rules.mapIndexed { i, rule -> if (i == index) rule.copy(originals = remaining) else rule }
                    }
                }
            }
            correction.addedVocabulary?.let { term ->
                vocabulary = vocabulary.filterNot { it.equals(term, ignoreCase = true) }
            }
        }
        return dictionary.copy(replacements = rules, vocabulary = vocabulary)
    }

    private fun canAddReplacement(rules: List<Replacement>, original: String, corrected: String): Boolean {
        if (original.isEmpty() || original.contains(',') || original == corrected) return false
        val originalKey = original.lowercase()
        if (rules.any { rule -> rule.originals.any { it.lowercase() == originalKey } }) return false
        return !wouldCreateCycle(rules, originalKey, corrected.lowercase())
    }

    /** True when following existing rules from [destinationKey] leads back to [sourceKey]. */
    private fun wouldCreateCycle(rules: List<Replacement>, sourceKey: String, destinationKey: String): Boolean {
        val visited = HashSet<String>()
        var current = destinationKey
        while (visited.add(current)) {
            if (current == sourceKey) return true
            val next = rules.firstOrNull { rule -> rule.originals.any { it.lowercase() == current } } ?: return false
            current = next.replacement.lowercase()
        }
        return false
    }

    private fun normalize(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
}
