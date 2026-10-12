package com.github.shekohex.whisperpp.ime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.core.text.TranscriptFilter
import com.github.shekohex.whisperpp.core.text.WordReplacements
import com.github.shekohex.whisperpp.data.PreferenceKeys.FILTER_FILLERS_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.FORMAT_PARAGRAPHS_ENABLED
import com.github.shekohex.whisperpp.data.dictionary.DictionaryRepository
import com.github.shekohex.whisperpp.domain.dictation.DictationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Filter → word replacements (WP-2.4) with the mode's formatting switches over the global ones; the
 * session adds the "Add trailing space" space afterwards. Reads the latest values synchronously.
 */
class TranscriptPostProcessor(
    private val filterOptions: () -> TranscriptFilter.Options,
    private val replacements: () -> WordReplacements,
) {
    fun process(text: String, options: DictationOptions): String {
        val global = filterOptions()
        val effective = global.copy(
            removeFillers = options.removeFillers ?: global.removeFillers,
            formatParagraphs = options.formatParagraphs ?: global.formatParagraphs,
        )
        val filtered = TranscriptFilter.apply(text, effective)
        return replacements().apply(filtered)
    }

    companion object {
        /** Keeps the dictionary rules and filter switches warm for the lifetime of [scope]. */
        fun create(
            scope: CoroutineScope,
            dataStore: DataStore<Preferences>,
            dictionaryRepository: DictionaryRepository,
        ): TranscriptPostProcessor {
            val wordReplacements = dictionaryRepository.replacements
                .map(WordReplacements::compile)
                .flowOn(Dispatchers.Default)
                .stateIn(scope, SharingStarted.Eagerly, WordReplacements.EMPTY)
            val transcriptFilterOptions = dataStore.data
                .map { prefs ->
                    TranscriptFilter.Options(
                        removeFillers = prefs[FILTER_FILLERS_ENABLED] ?: false,
                        formatParagraphs = prefs[FORMAT_PARAGRAPHS_ENABLED] ?: false,
                    )
                }
                .stateIn(scope, SharingStarted.Eagerly, TranscriptFilter.Options())
            return TranscriptPostProcessor({ transcriptFilterOptions.value }, { wordReplacements.value })
        }
    }
}
