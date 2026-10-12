package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.core.audio.AudioRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import java.io.File

/** [DictationRecorder] backed by the real [AudioRecorder]. */
class AudioRecorderSource(private val recorder: AudioRecorder) : DictationRecorder {
    override val errors: Flow<AudioRecorder.Event.Error> = recorder.events.filterIsInstance()

    override suspend fun start(file: File, onPcm: ((ByteArray) -> Unit)?): Result<Unit> {
        val options = AudioRecorder.StartOptions(onPcmChunk = onPcm?.let { sink -> { chunk -> sink(chunk.data) } })
        return recorder.start(file, options)
    }

    override suspend fun stop(): Result<AudioRecorder.Event.Stopped> = recorder.stopDetailed()

    override suspend fun pause() = recorder.pause()

    override suspend fun resume() = recorder.resume()

    override fun cancel(keepFile: Boolean) = recorder.cancel(keepFile)
}
