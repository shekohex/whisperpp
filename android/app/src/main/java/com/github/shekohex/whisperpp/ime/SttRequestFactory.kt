package com.github.shekohex.whisperpp.ime

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.core.text.Vocabulary
import com.github.shekohex.whisperpp.data.PreferenceKeys.ADD_TRAILING_SPACE
import com.github.shekohex.whisperpp.data.PreferenceKeys.POSTPROCESSING
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest

/**
 * Builds the batch [TranscriptionRequest] for a provider/model/language from today's settings; shared
 * by the keyboard (a new dictation) and History (retrying a saved recording) so both send the same thing.
 */
class SttRequestFactory(
    /** Maps the stored post-processing preference (null when unset) to a script conversion. */
    private val scriptConversion: (String?) -> ScriptConversion,
    private val audioMediaType: String,
    /** Dictionary vocabulary; sent only to models with `capabilities.vocabulary`. */
    private val vocabulary: suspend () -> List<String>,
) {
    suspend fun create(
        prefs: Preferences,
        provider: ServiceProvider,
        modelId: String,
        languageCode: String,
    ): TranscriptionRequest {
        val basePrompt = provider.prompt.ifEmpty { prefs[PROMPT] ?: "" }
        val model = provider.models.find { it.id == modelId }
        val acceptsVocabulary = model?.capabilities?.vocabulary == true
        val terms = if (acceptsVocabulary) vocabulary() else emptyList()
        return TranscriptionRequest(
            provider = provider,
            modelId = modelId,
            languageCode = languageCode,
            prompt = if (acceptsVocabulary) Vocabulary.appendToPrompt(basePrompt, terms) else basePrompt,
            temperature = provider.temperature,
            timeoutMs = provider.effectiveTimeoutMs(model),
            mediaType = audioMediaType,
            scriptConversion = scriptConversion(prefs[POSTPROCESSING]),
            addTrailingSpace = prefs[ADD_TRAILING_SPACE] ?: false,
            useContext = prefs[USE_CONTEXT] ?: false,
            vocabulary = Vocabulary.normalize(terms),
        )
    }
}
