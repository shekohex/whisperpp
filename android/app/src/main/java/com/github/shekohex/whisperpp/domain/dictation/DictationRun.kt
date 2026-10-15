package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.dictation.DictationUndoEntry
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel

/** Mutable bookkeeping of one dictation run; owned and only mutated through [DictationSession]. */
internal class DictationRun(val id: Long, val focus: FocusKey) {
    var plan: StartPlan? = null
    var job: Job? = null
    val auxJobs = mutableListOf<Job>()
    var recorderLive = false
    var streaming: StreamingTranscriptionSession? = null
    var pcm: Channel<ByteArray>? = null
    var pcmSender: Job? = null

    // Set on the streaming dispatcher; read after joining [pcmSender].
    @Volatile var streamBroken = false
    var lastPartial: String? = null
    var lastPartialAt: Long? = null
    var composing = false
    var enhancementComposing = false
    var enhancementEditorChanged = false

    /** Enhancement will run, so raw transcription partials stay out of the field. */
    var suppressPartials = false
    var contextPrompt: String? = null

    /** [contextPrompt] plus the mode's extra sources (selected text, clipboard); enhancement only. */
    var enhancementContext: String? = null

    /** Output mode REPLACE_SELECTION: the selection when recording started. */
    var replaceRange: DictationUndoEntry.SelectionSnapshot? = null
    val options: DictationOptions
        get() = plan?.options ?: DictationOptions()
    var durationMs = 0L
    var rawText: String? = null
    var stoppedAt = 0L
    var transcriptionLatencyMs = 0L
    var enhancementProviderId: String? = null
    var enhancementModelId: String? = null
    var enhancementLatencyMs: Long? = null

    /** The take's history row once the recording stopped with audio; history owns the file from then on. */
    var ticket: HistoryTicket? = null
    var resolution = TakeResolution.OPEN

    /** Why the run is being cut short, when the cause is not a plain cancel (recorded if the take is still open). */
    var abortReason: HistoryFailureReason? = null

    /** Enhancement was attempted and fell back to the raw text. */
    var enhancementFailed = false

    /** Live capture is stopped but kept (service teardown mid-recording) so the take can be saved. */
    var salvageOnFinish = false

    enum class TakeResolution { OPEN, COMPLETED, FAILED }

    fun stats(): CompletionStats = CompletionStats(
        providerId = plan?.request?.provider?.id.orEmpty(),
        sttModelId = plan?.request?.modelId.orEmpty(),
        languageCode = plan?.request?.languageCode?.takeIf { it.isNotBlank() && it != AUTO_LANGUAGE },
        transcriptionLatencyMs = transcriptionLatencyMs,
        enhancementProviderId = enhancementProviderId,
        enhancementModelId = enhancementModelId,
        enhancementLatencyMs = enhancementLatencyMs,
    )

    private companion object {
        const val AUTO_LANGUAGE = "auto"
    }
}
