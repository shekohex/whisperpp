package com.github.shekohex.whisperpp.data.dictionary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.core.text.Vocabulary
import com.github.shekohex.whisperpp.data.PreferenceKeys
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Word replacements and custom vocabulary (§4.11), stored as JSON in the settings DataStore so the
 * settings backup can carry them as the `dictionary` category.
 */
class DictionaryRepository(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
) {
    enum class ImportMode {
        /** Imported rules win on conflicting originals; vocabulary is the union. */
        MERGE,
        OVERWRITE,
    }

    val replacements: Flow<List<Replacement>> = dataStore.data
        .map { parseReplacementsJson(it[PreferenceKeys.DICTIONARY_REPLACEMENTS_JSON], gson) }
        .distinctUntilChanged()

    val vocabulary: Flow<List<String>> = dataStore.data
        .map { parseVocabularyJson(it[PreferenceKeys.DICTIONARY_VOCABULARY_JSON], gson) }
        .distinctUntilChanged()

    /** Adds [rule]; its originals are taken away from any existing rule (one rule per trigger). */
    suspend fun addReplacement(rule: Replacement) {
        val sanitized = sanitizeReplacements(listOf(rule)).firstOrNull() ?: return
        editReplacements { current -> mergeReplacements(current, listOf(sanitized)) }
    }

    suspend fun removeReplacement(rule: Replacement) {
        editReplacements { current -> current.filterNot { it == rule } }
    }

    /** Replaces [old] in place with [new] (or removes it when [new] is invalid). */
    suspend fun updateReplacement(old: Replacement, new: Replacement) {
        val sanitized = sanitizeReplacements(listOf(new)).firstOrNull()
        editReplacements { current ->
            val index = current.indexOf(old)
            if (index < 0) return@editReplacements current
            val others = current.filterIndexed { i, _ -> i != index }
            if (sanitized == null) return@editReplacements others
            val keys = sanitized.originals.map { it.lowercase() }.toSet()
            val trimmed = others.map { it.withoutOriginals(keys) }
            trimmed.toMutableList().apply { add(index.coerceAtMost(size), sanitized) }.filter { it.originals.isNotEmpty() }
        }
    }

    suspend fun addVocabulary(term: String) {
        editVocabulary { current -> current + term }
    }

    suspend fun removeVocabulary(term: String) {
        val key = term.trim().lowercase()
        editVocabulary { current -> current.filterNot { it.lowercase() == key } }
    }

    suspend fun setVocabulary(terms: List<String>) {
        editVocabulary { terms }
    }

    suspend fun snapshot(): DictionaryBackup = readDictionary(dataStore.data.first(), gson)

    fun exportJson(backup: DictionaryBackup): String = gson.toJson(backup)

    suspend fun exportJson(): String = exportJson(snapshot())

    /** Imports a [DictionaryBackup] JSON document; fails without touching storage when invalid. */
    suspend fun importJson(json: String, mode: ImportMode = ImportMode.MERGE): Result<DictionaryBackup> {
        val imported = try {
            parseDictionaryBackup(json, gson)
        } catch (e: JsonParseException) {
            return Result.failure(IllegalArgumentException("Invalid dictionary file", e))
        } catch (e: IllegalStateException) {
            return Result.failure(IllegalArgumentException("Invalid dictionary file", e))
        } ?: return Result.failure(IllegalArgumentException("Invalid dictionary file"))
        dataStore.edit { prefs ->
            val current = readDictionary(prefs, gson)
            val result = when (mode) {
                ImportMode.OVERWRITE -> imported
                ImportMode.MERGE -> mergeDictionary(current, imported)
            }
            writeDictionary(prefs, result, gson)
        }
        return Result.success(imported)
    }

    private suspend fun editReplacements(transform: (List<Replacement>) -> List<Replacement>) {
        dataStore.edit { prefs ->
            val current = parseReplacementsJson(prefs[PreferenceKeys.DICTIONARY_REPLACEMENTS_JSON], gson)
            prefs[PreferenceKeys.DICTIONARY_REPLACEMENTS_JSON] = gson.toJson(sanitizeReplacements(transform(current)))
        }
    }

    private suspend fun editVocabulary(transform: (List<String>) -> List<String>) {
        dataStore.edit { prefs ->
            val current = parseVocabularyJson(prefs[PreferenceKeys.DICTIONARY_VOCABULARY_JSON], gson)
            prefs[PreferenceKeys.DICTIONARY_VOCABULARY_JSON] = gson.toJson(Vocabulary.normalize(transform(current)))
        }
    }
}

internal fun readDictionary(prefs: Preferences, gson: Gson): DictionaryBackup = DictionaryBackup(
    replacements = parseReplacementsJson(prefs[PreferenceKeys.DICTIONARY_REPLACEMENTS_JSON], gson),
    vocabulary = parseVocabularyJson(prefs[PreferenceKeys.DICTIONARY_VOCABULARY_JSON], gson),
)

internal fun writeDictionary(prefs: MutablePreferences, backup: DictionaryBackup, gson: Gson) {
    prefs[PreferenceKeys.DICTIONARY_REPLACEMENTS_JSON] = gson.toJson(sanitizeReplacements(backup.replacements))
    prefs[PreferenceKeys.DICTIONARY_VOCABULARY_JSON] = gson.toJson(Vocabulary.normalize(backup.vocabulary))
}

/** Parses a standalone dictionary file; null when it is not a JSON object. */
internal fun parseDictionaryBackup(json: String, gson: Gson): DictionaryBackup? {
    val decoded = gson.fromJson(json.trim(), DictionaryBackup::class.java) ?: return null
    return sanitizeDictionary(decoded)
}

internal fun sanitizeDictionary(backup: DictionaryBackup): DictionaryBackup = DictionaryBackup(
    replacements = sanitizeReplacements((backup.replacements as List<Replacement?>?).orEmpty().filterNotNull()),
    vocabulary = Vocabulary.normalize((backup.vocabulary as List<String?>?).orEmpty().filterNotNull()),
)

internal fun mergeDictionary(current: DictionaryBackup, imported: DictionaryBackup): DictionaryBackup = DictionaryBackup(
    replacements = mergeReplacements(current.replacements, imported.replacements),
    vocabulary = Vocabulary.normalize(current.vocabulary + imported.vocabulary),
)

/** [incoming] rules win: their originals are removed from [current] rules, emptied rules are dropped. */
internal fun mergeReplacements(current: List<Replacement>, incoming: List<Replacement>): List<Replacement> {
    val keys = incoming.flatMap { rule -> rule.originals.map { it.lowercase() } }.toSet()
    val kept = current.map { it.withoutOriginals(keys) }.filter { it.originals.isNotEmpty() }
    return kept + incoming
}

private fun Replacement.withoutOriginals(keys: Set<String>): Replacement =
    copy(originals = originals.filterNot { it.lowercase() in keys })

internal fun sanitizeReplacements(rules: List<Replacement>): List<Replacement> = rules.mapNotNull { rule ->
    // Gson leaves missing fields null despite the Kotlin types.
    val originals = (rule.originals as List<String?>?).orEmpty()
        .flatMap { it.orEmpty().split(',') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
    val replacement = (rule.replacement as String?).orEmpty().trim()
    if (originals.isEmpty()) null else Replacement(originals, replacement)
}

internal fun parseReplacementsJson(json: String?, gson: Gson): List<Replacement> {
    val raw = json?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    return try {
        val type = object : TypeToken<List<Replacement?>>() {}.type
        sanitizeReplacements(gson.fromJson<List<Replacement?>>(raw, type).orEmpty().filterNotNull())
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun parseVocabularyJson(json: String?, gson: Gson): List<String> {
    val raw = json?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    return try {
        val type = object : TypeToken<List<String?>>() {}.type
        Vocabulary.normalize(gson.fromJson<List<String?>>(raw, type).orEmpty().filterNotNull())
    } catch (_: Exception) {
        emptyList()
    }
}
