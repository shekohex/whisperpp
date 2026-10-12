package com.github.shekohex.whisperpp.ui.history

import android.content.Context
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.text.Replacement
import com.github.shekohex.whisperpp.core.text.TranscriptFilter
import com.github.shekohex.whisperpp.core.text.WordReplacements
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.toTranscriptionFailureReason
import com.github.shekohex.whisperpp.ime.SttRequestFactory
import com.github.shekohex.whisperpp.ime.SttTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Retry for History: the saved WAV goes through the same request, provider call and post-processing
 * (filler filter, word replacements) as a live dictation, using the settings of the entry's mode today.
 */
class SttHistoryTranscriber(
    private val context: Context,
    private val config: RuntimeConfigLoader,
    private val requests: SttRequestFactory,
    private val transcriber: SttTranscriber,
    private val replacements: suspend () -> List<Replacement>,
) : HistoryTranscriber {
    override suspend fun transcribe(audio: File, entry: TranscriptHistoryEntry): HistoryTranscriber.Result {
        val snapshot = config.load(entry.appPackage, entry.modeId)
        val providerId = snapshot.effective.sttProviderId
        val modelId = snapshot.effective.sttModelId
        val provider = snapshot.provider(providerId)
        if (provider == null || modelId.isBlank() || provider.models.none { it.id == modelId }) {
            return HistoryTranscriber.Result.Failed(
                context.getString(R.string.history_retry_unavailable),
                HistoryFailureReason.TRANSCRIPTION_FAILED,
            )
        }
        val request = requests.create(snapshot.prefs, provider, modelId, snapshot.mode.languageCode)
        val text = transcriber.transcribe(audio, request).getOrElse { error ->
            return HistoryTranscriber.Result.Failed(
                error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.provider_error_unknown),
                error.toTranscriptionFailureReason(),
            )
        }
        val processed = postProcess(text, snapshot.mode.removeFillers, snapshot.mode.formatParagraphs)
        if (processed.isBlank()) return HistoryTranscriber.Result.Empty
        return HistoryTranscriber.Result.Text(
            text = processed,
            providerId = providerId,
            sttModelId = modelId,
            languageCode = snapshot.mode.languageCode.takeIf { it.isNotBlank() && it != AUTO_LANGUAGE },
        )
    }

    override suspend fun enhancementEnabled(entry: TranscriptHistoryEntry): Boolean =
        config.load(entry.appPackage, entry.modeId).mode.enhancementEnabled

    private suspend fun postProcess(text: String, removeFillers: Boolean, formatParagraphs: Boolean): String {
        val rules = replacements()
        return withContext(Dispatchers.Default) {
            val filtered = TranscriptFilter.apply(text, TranscriptFilter.Options(removeFillers, formatParagraphs))
            WordReplacements.compile(rules).apply(filtered).trim()
        }
    }

    private companion object {
        const val AUTO_LANGUAGE = "auto"
    }
}
