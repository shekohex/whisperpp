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

    /** Enhancement will run, so live partials stay out of the field: it only ever receives the final text. */
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

    /** Kept recording owned by this run until it is handed to history (or discarded on finish). */
    var audioPath: String? = null
}
