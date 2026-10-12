package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import kotlinx.coroutines.flow.Flow
import java.io.File

/** The slice of [AudioRecorder] the session drives; a seam so the state machine is unit-testable. */
interface DictationRecorder {
    /** Errors raised while capturing (after a successful start). */
    val errors: Flow<AudioRecorder.Event.Error>

    suspend fun start(file: File, onPcm: ((ByteArray) -> Unit)?): Result<Unit>

    /** Finalizes the file; the result carries the dropped realtime chunk count (F-05). */
    suspend fun stop(): Result<AudioRecorder.Event.Stopped>

    suspend fun pause()

    suspend fun resume()

    /**
     * Stops capture and deletes the file; also aborts a start that is still in flight. With [keepFile]
     * the capture is finalized instead, so a take torn down mid-recording can still be saved.
     */
    fun cancel(keepFile: Boolean = false)
}
