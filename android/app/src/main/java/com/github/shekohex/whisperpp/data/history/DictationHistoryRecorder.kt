package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.core.text.WordCounter
import com.github.shekohex.whisperpp.data.HistorySettings
import com.github.shekohex.whisperpp.domain.dictation.CompletionStats
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryRecorder
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import com.github.shekohex.whisperpp.domain.dictation.HistoryTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Writes every take into [TranscriptHistoryRepository] as soon as it has audio and follows it through
 * the pipeline, so a failure never loses a recording. Updates run in call order on [scope] (the app
 * scope, which outlives the keyboard service). With history disabled nothing is stored and the take's
 * file is deleted when its run ends; otherwise the file stays while the take is unresolved, and after
 * completion only while "keep recordings" is on.
 */
class DictationHistoryRecorder(
    private val repository: TranscriptHistoryRepository,
    private val audio: HistoryAudioStore,
    private val settings: Flow<HistorySettings>,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : HistoryRecorder {
    private class Ticket(val file: File) : HistoryTicket {
        @Volatile var rowId: Long? = null

        /** History was off (or the file is not ours to keep): delete it once the run ends. */
        @Volatile var discardAtEnd = false
    }

    private val worker = SerialWorker(scope)

    override fun begin(take: HistoryRecorder.Take): HistoryTicket {
        val ticket = Ticket(take.file)
        worker.submit {
            if (!settings.first().enabled || !audio.contains(take.file)) {
                ticket.discardAtEnd = true
                return@submit
            }
            val path = take.file.absolutePath
            try {
                audio.repairWavHeader(take.file)
                ticket.rowId = repository.insert(newEntry(take, path))
            } finally {
                // Referenced now; if the insert failed the file stays for orphan recovery.
                audio.release(path)
            }
        }
        return ticket
    }

    override fun transcribing(ticket: HistoryTicket) = step(ticket) { it.copy(status = HistoryStatus.TRANSCRIBING, failureReason = null) }

    override fun transcribed(ticket: HistoryTicket, rawText: String) = step(ticket) {
        it.copy(rawText = rawText.trim(), wordCount = WordCounter.count(rawText), status = HistoryStatus.TRANSCRIBED, failureReason = null)
    }

    override fun enhancing(ticket: HistoryTicket) = step(ticket) { it.copy(status = HistoryStatus.ENHANCING) }

    override fun complete(
        ticket: HistoryTicket,
        rawText: String,
        enhancedText: String?,
        stats: CompletionStats,
        enhancementFailed: Boolean,
    ) {
        val t = ticket as Ticket
        worker.submit {
            val id = t.rowId
            if (id == null) {
                discardIfOwned(t)
                return@submit
            }
            val raw = rawText.trim()
            val enhanced = enhancedText?.trim()?.takeIf { it.isNotEmpty() && it != raw }
            repository.complete(id, keepAudio = settings.first().keepAudio) {
                it.copy(
                    rawText = raw,
                    enhancedText = enhanced,
                    enhancementModelId = stats.enhancementModelId.takeIf { enhanced != null },
                    wordCount = WordCounter.count(enhanced ?: raw),
                    failureReason = HistoryFailureReason.ENHANCEMENT_FAILED.takeIf { enhancementFailed },
                )
            }
        }
    }

    override fun fail(ticket: HistoryTicket, reason: HistoryFailureReason, rawText: String?, enhancedText: String?) {
        val t = ticket as Ticket
        worker.submit {
            val id = t.rowId
            if (id == null) {
                discardIfOwned(t)
                return@submit
            }
            repository.update(id) { current ->
                val raw = rawText?.trim()?.takeIf { it.isNotEmpty() } ?: current.rawText
                val enhanced = enhancedText?.trim()?.takeIf { it.isNotEmpty() && it != raw } ?: current.enhancedText
                current.copy(
                    rawText = raw,
                    enhancedText = enhanced,
                    wordCount = WordCounter.count(enhanced ?: raw),
                    status = HistoryStatus.FAILED,
                    failureReason = reason,
                )
            }
        }
    }

    private fun step(ticket: HistoryTicket, change: (TranscriptHistoryEntry) -> TranscriptHistoryEntry) {
        val t = ticket as Ticket
        worker.submit {
            val id = t.rowId ?: return@submit
            repository.update(id, change)
        }
    }

    private fun discardIfOwned(ticket: Ticket) {
        if (ticket.discardAtEnd) ticket.file.delete()
    }

    private fun newEntry(take: HistoryRecorder.Take, path: String) = TranscriptHistoryEntry(
        createdAt = clock(),
        rawText = "",
        providerId = take.stats.providerId,
        sttModelId = take.stats.sttModelId,
        language = take.stats.languageCode,
        appPackage = take.appPackage,
        durationMs = take.durationMs.coerceAtLeast(0L),
        wordCount = 0,
        audioPath = path,
        status = HistoryStatus.RECORDED,
        modeId = take.modeId,
    )
}
