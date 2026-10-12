package com.github.shekohex.whisperpp.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.data.dictionary.DictionaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/** Word replacements and vocabulary tabs of the Dictionary screen (§4.11). */
class DictionaryViewModel(
    private val repository: DictionaryRepository,
) : ViewModel() {

    enum class AddResult {
        ADDED,
        DUPLICATE,
        INVALID,
    }

    /** Last removal, kept for the snackbar's Undo. */
    sealed interface Removed {
        data class Rule(val rule: Replacement) : Removed
        data class Term(val term: String) : Removed
    }

    val replacements: StateFlow<List<Replacement>?> = repository.replacements.stateInScreen()
    val vocabulary: StateFlow<List<String>?> = repository.vocabulary.stateInScreen()

    /** Adds `original1, original2 → replacement`; an identical existing rule (ignoring case) is a duplicate. */
    suspend fun addReplacement(originalsInput: String, replacementInput: String): AddResult {
        val rule = parseRule(originalsInput, replacementInput) ?: return AddResult.INVALID
        if (repository.replacements.first().any { it.sameAs(rule) }) return AddResult.DUPLICATE
        repository.addReplacement(rule)
        return AddResult.ADDED
    }

    suspend fun updateReplacement(old: Replacement, originalsInput: String, replacementInput: String): AddResult {
        val rule = parseRule(originalsInput, replacementInput) ?: return AddResult.INVALID
        if (old.sameAs(rule)) return AddResult.DUPLICATE
        repository.updateReplacement(old, rule)
        return AddResult.ADDED
    }

    suspend fun removeReplacement(rule: Replacement): Removed {
        repository.removeReplacement(rule)
        return Removed.Rule(rule)
    }

    /** Adds one or more comma-separated terms; [AddResult.DUPLICATE] when all of them already exist. */
    suspend fun addVocabulary(input: String): AddResult {
        val terms = parseList(input)
        if (terms.isEmpty()) return AddResult.INVALID
        val known = repository.vocabulary.first().mapTo(HashSet()) { it.lowercase() }
        val fresh = terms.filterNot { it.lowercase() in known }
        if (fresh.isEmpty()) return AddResult.DUPLICATE
        fresh.forEach { repository.addVocabulary(it) }
        return AddResult.ADDED
    }

    suspend fun removeVocabulary(term: String): Removed {
        val stored = repository.vocabulary.first().firstOrNull { it.equals(term.trim(), ignoreCase = true) } ?: term
        repository.removeVocabulary(stored)
        return Removed.Term(stored)
    }

    suspend fun undo(removed: Removed) {
        when (removed) {
            is Removed.Rule -> repository.addReplacement(removed.rule)
            is Removed.Term -> repository.addVocabulary(removed.term)
        }
    }

    private fun <T> Flow<T>.stateInScreen(): StateFlow<T?> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Trimmed, non-blank, case-insensitively unique items of a comma-separated list. */
        fun parseList(input: String): List<String> = input.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        fun parseRule(originalsInput: String, replacementInput: String): Replacement? {
            val originals = parseList(originalsInput)
            val replacement = replacementInput.trim()
            if (originals.isEmpty() || replacement.isEmpty()) return null
            return Replacement(originals, replacement)
        }

        private fun Replacement.sameAs(other: Replacement): Boolean =
            replacement == other.replacement &&
                originals.map { it.lowercase() }.toSet() == other.originals.map { it.lowercase() }.toSet()
    }
}
