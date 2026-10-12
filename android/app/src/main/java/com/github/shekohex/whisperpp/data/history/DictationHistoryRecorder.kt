package com.github.shekohex.whisperpp.data.history

import android.util.Log
import com.github.shekohex.whisperpp.core.text.WordCounter
import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.domain.dictation.HistoryRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "HistoryRecorder"

/** Writes completed dictations into [TranscriptHistoryRepository] when history is enabled. */
class DictationHistoryRecorder(
    private val repository: TranscriptHistoryRepository,
    private val audio: HistoryAudioStore,
    private val settings: Flow<HistorySettings>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HistoryRecorder {
    override suspend fun retainAudio(file: File): String? {
        val current = settings.first()
        if (!current.enabled || !current.keepAudio) return null
        return withContext(ioDispatcher) { audio.retain(file, "${clock()}_${file.name}") }
    }

    override fun record(entry: HistoryRecorder.Entry) {
        scope.launch {
            if (!settings.first().enabled) {
                entry.audioPath?.let(audio::delete)
                return@launch
            }
            val raw = entry.rawText.trim()
            val enhanced = entry.enhancedText?.trim()?.takeIf { it.isNotEmpty() && it != raw }
            try {
                repository.insert(
                    TranscriptHistoryEntry(
                        createdAt = clock(),
                        rawText = raw,
                        enhancedText = enhanced,
                        providerId = entry.stats.providerId,
                        sttModelId = entry.stats.sttModelId,
                        enhancementModelId = entry.stats.enhancementModelId.takeIf { enhanced != null },
                        language = entry.stats.languageCode,
                        appPackage = entry.appPackage,
                        durationMs = entry.durationMs.coerceAtLeast(0L),
                        wordCount = WordCounter.count(enhanced ?: raw),
                        audioPath = entry.audioPath,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Exception type only: messages could echo bound values.
                Log.e(TAG, "Failed to store history entry: ${e.javaClass.simpleName}")
                entry.audioPath?.let(audio::delete)
            }
        }
    }

    override fun discardAudio(path: String) {
        scope.launch(ioDispatcher) { audio.delete(path) }
    }
}
