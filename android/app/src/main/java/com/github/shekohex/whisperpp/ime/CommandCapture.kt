package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.core.audio.AudioFocusController
import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import com.github.shekohex.whisperpp.core.audio.SoundCues
import java.io.File

/**
 * Command-mode capture on the recorder shared with dictation. Focus order on start: acquire focus →
 * start cue → capture. Releases focus on failure. A start cancelled while the recorder was opening
 * returns stale and leaves the shared focus and recorder alone: by then they may belong to a
 * dictation run (F-08).
 */
class CommandCapture(
    private val recorder: AudioRecorder,
    private val audioFocus: AudioFocusController,
    private val feedback: KeyboardFeedback,
    private val recordingFile: () -> File,
    private val isDictationBusy: () -> Boolean,
    private val onStartFailed: (AudioRecorder.ErrorKind?) -> Unit,
) {
    /** Bumped on every command capture start/cancel; a start that returns stale must not touch shared state. */
    private var generation = 0

    /** The exact file the current command capture records into. */
    private var audioFile: File? = null

    suspend fun start(): Result<Unit> {
        val startGeneration = ++generation
        audioFocus.acquire()
        feedback.perform(cue = SoundCues.Cue.START)
        val file = recordingFile()
        audioFile = file
        val result = recorder.start(file)
        if (startGeneration != generation) {
            if (result.isSuccess && !isDictationBusy()) recorder.cancel()
            return Result.failure(AudioRecorder.RecorderException(AudioRecorder.ErrorKind.CANCELLED, "Command capture cancelled"))
        }
        result.onFailure { error ->
            audioFocus.release()
            val kind = (error as? AudioRecorder.RecorderException)?.kind
            if (kind != AudioRecorder.ErrorKind.CANCELLED) onStartFailed(kind)
        }
        return result
    }

    suspend fun stop(): Result<File> {
        val stopGeneration = generation
        val result = recorder.stop()
        if (stopGeneration == generation) audioFocus.release()
        return result
    }

    fun cancel() {
        generation++
        recorder.cancel()
        audioFocus.release()
    }

    /** Hands the recorded file to the caller, which owns (and deletes) it from here on. */
    fun takeAudioFile(): File? {
        val file = audioFile
        audioFile = null
        return file
    }

    /**
     * Ends the current command run's capture. Any capture still starting is stale from here on, even
     * when the recorder is left alone. The recorder and clients are shared with dictation; a dictation
     * run is never torn down (F-08).
     */
    fun reset() {
        generation++
        audioFile = null
        if (!isDictationBusy()) {
            cancel()
        }
    }
}
