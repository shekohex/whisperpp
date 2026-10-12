package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.domain.modes.OutputMode
import kotlinx.coroutines.CancellationException
import java.io.File

/** Final reporting of a run whose text reached its destination: audio retention, analytics, history. */
internal class CompletionReporter(
    private val deps: DictationSession.Deps,
    private val isCurrent: (DictationRun) -> Boolean,
    private val emit: (SessionEvent) -> Unit,
) {
    suspend fun retainAudio(r: DictationRun, file: File) {
        val history = deps.history ?: return
        val path = try {
            history.retainAudio(file)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (isCurrent(r)) r.audioPath = path else path?.let(history::discardAudio)
    }

    /** Text reached the field: analytics event plus the history entry, which takes over the kept audio. */
    fun complete(r: DictationRun, raw: String, finalText: String) {
        val stats = CompletionStats(
            providerId = r.plan?.request?.provider?.id.orEmpty(),
            sttModelId = r.plan?.request?.modelId.orEmpty(),
            languageCode = r.plan?.request?.languageCode?.takeIf { it.isNotBlank() && it != AUTO_LANGUAGE },
            transcriptionLatencyMs = r.transcriptionLatencyMs,
            enhancementProviderId = r.enhancementProviderId,
            enhancementModelId = r.enhancementModelId,
            enhancementLatencyMs = r.enhancementLatencyMs,
        )
        emit(
            SessionEvent.Completed(
                rawText = raw,
                finalText = finalText,
                durationMs = r.durationMs,
                focus = r.focus,
                runId = r.id,
                stats = stats,
                inserted = r.options.outputMode != OutputMode.CLIPBOARD,
                autoSend = r.options.autoSend,
            )
        )
        deps.history?.record(
            HistoryRecorder.Entry(
                rawText = raw,
                enhancedText = finalText.takeIf { it != raw },
                stats = stats,
                appPackage = r.focus.packageName,
                durationMs = r.durationMs,
                audioPath = r.audioPath,
            )
        )
        r.audioPath = null
    }

    private companion object {
        const val AUTO_LANGUAGE = "auto"
    }
}
