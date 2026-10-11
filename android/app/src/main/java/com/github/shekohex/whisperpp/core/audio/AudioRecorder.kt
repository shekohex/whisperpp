package com.github.shekohex.whisperpp.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 16 kHz mono PCM16 WAV recorder. All [AudioRecord] work runs on a dedicated capture thread;
 * the public API is meant to be called from the main thread and never blocks it.
 */
class AudioRecorder(
    context: Context,
    private val scope: CoroutineScope,
    private val meter: AmplitudeMeter,
) {
    sealed interface Event {
        data class Started(val sampleRate: Int) : Event
        data class Error(val kind: ErrorKind, val message: String) : Event
        data class Stopped(val file: File, val durationMs: Long, val droppedChunks: Int) : Event
    }

    enum class ErrorKind {
        PERMISSION_DENIED,
        MIC_UNAVAILABLE,
        CAPTURE_FAILED,
        WRITE_FAILED,
        STOP_TIMEOUT,
        NOT_RECORDING,
        CANCELLED,
    }

    class RecorderException(val kind: ErrorKind, message: String, cause: Throwable? = null) :
        Exception(message, cause)

    data class PcmChunk(
        val data: ByteArray,
        val sampleRate: Int,
    )

    data class StartOptions(
        val sampleRate: Int = DEFAULT_SAMPLE_RATE,
        val onPcmChunk: ((PcmChunk) -> Unit)? = null,
    )

    private class Session(
        val file: File,
        val record: AudioRecord,
        val output: FileOutputStream,
        val sampleRate: Int,
        val chunkBytes: Int,
        val sink: PcmChunkSink?,
        val clock: RecordingClock,
    ) {
        @Volatile var stopRequested = false
        @Volatile var paused = false
        @Volatile var discard = false
        @Volatile var failure: RecorderException? = null
        var captureJob: Job? = null
        var forwardJob: Job? = null
    }

    private val appContext = context.applicationContext
    private val captureExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AudioRecorder").apply { priority = Thread.MAX_PRIORITY }
    }
    private val captureDispatcher: ExecutorCoroutineDispatcher = captureExecutor.asCoroutineDispatcher()
    private val session = AtomicReference<Session?>(null)
    private val generation = AtomicInteger(0)

    private val _events = MutableSharedFlow<Event>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    val level: StateFlow<Float> = meter.level

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    suspend fun start(file: File, options: StartOptions = StartOptions()): Result<Unit> {
        session.get()?.let {
            Log.w(TAG, "start() while a recording is active; discarding the previous one")
            cancel()
        }
        if (!hasPermission(appContext)) {
            return fail(RecorderException(ErrorKind.PERMISSION_DENIED, "RECORD_AUDIO not granted"))
        }

        val startGeneration = generation.incrementAndGet()
        // Non-cancellable so an opened AudioRecord is always either registered or released.
        return withContext(NonCancellable) {
            val opened = withContext(captureDispatcher) { runCatching { open(file, options.sampleRate, options) } }
            val newSession = opened.getOrElse { error ->
                val recorderError = error as? RecorderException
                    ?: RecorderException(ErrorKind.MIC_UNAVAILABLE, error.message ?: "Recorder start failed", error)
                return@withContext fail(recorderError)
            }
            if (generation.get() != startGeneration) {
                withContext(captureDispatcher) { closeQuietly(newSession, deleteFile = true) }
                return@withContext Result.failure(
                    RecorderException(ErrorKind.CANCELLED, "Recording cancelled while starting")
                )
            }
            register(newSession, options)
            Result.success(Unit)
        }
    }

    private fun register(newSession: Session, options: StartOptions) {
        meter.reset()
        newSession.clock.start()
        session.set(newSession)
        newSession.forwardJob = options.onPcmChunk?.let { callback ->
            launchForwarder(newSession, callback)
        }
        newSession.captureJob = scope.launch(captureDispatcher) { capture(newSession) }
        _isRecording.value = true
        _events.tryEmit(Event.Started(newSession.sampleRate))
    }

    suspend fun stop(): Result<File> = stopDetailed().map { it.file }

    /** Like [stop], but also reports duration and dropped realtime chunks (F-05). */
    suspend fun stopDetailed(): Result<Event.Stopped> {
        generation.incrementAndGet()
        val current = session.getAndSet(null)
            ?: return Result.failure(RecorderException(ErrorKind.NOT_RECORDING, "No active recording"))
        current.stopRequested = true
        _isRecording.value = false
        meter.reset()

        val finished = withTimeoutOrNull(STOP_TIMEOUT_MS) {
            current.captureJob?.join()
            current.forwardJob?.join()
        }
        val durationMs = current.clock.stop()

        if (finished == null) {
            current.captureJob?.cancel()
            current.forwardJob?.cancel()
            return fail(RecorderException(ErrorKind.STOP_TIMEOUT, "Recorder did not stop within ${STOP_TIMEOUT_MS} ms"))
        }
        current.failure?.let { return Result.failure(it) }

        val dropped = current.sink?.droppedChunks ?: 0
        if (dropped > 0) Log.w(TAG, "Realtime PCM stream dropped $dropped chunks")
        val stopped = Event.Stopped(current.file, durationMs, dropped)
        _events.tryEmit(stopped)
        return Result.success(stopped)
    }

    suspend fun pause() {
        val current = session.get() ?: return
        if (current.paused) return
        current.paused = true
        current.clock.pause()
        meter.reset()
    }

    suspend fun resume() {
        val current = session.get() ?: return
        if (!current.paused) return
        current.clock.resume()
        current.paused = false
    }

    /** Stops capture and deletes the file. Safe to call from any lifecycle callback. */
    fun cancel() {
        generation.incrementAndGet()
        val current = session.getAndSet(null) ?: return
        current.discard = true
        current.stopRequested = true
        _isRecording.value = false
        meter.reset()
    }

    /** Cancels any recording and shuts down the capture thread. The instance is unusable afterwards. */
    fun release() {
        cancel()
        captureDispatcher.close()
    }

    @SuppressLint("MissingPermission")
    private fun open(file: File, sampleRate: Int, options: StartOptions): Session {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw RecorderException(ErrorKind.MIC_UNAVAILABLE, "Unsupported recording format ($sampleRate Hz)")
        }
        val chunkBytes = sampleRate * BYTES_PER_SAMPLE * CHUNK_MS / 1000

        var record: AudioRecord? = null
        var output: FileOutputStream? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                maxOf(minBufferSize * 4, chunkBytes * 4),
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw RecorderException(ErrorKind.MIC_UNAVAILABLE, "AudioRecord failed to initialize")
            }

            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
            output = FileOutputStream(file)
            output.write(ByteArray(WAV_HEADER_SIZE))

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw RecorderException(ErrorKind.MIC_UNAVAILABLE, "Microphone is in use by another app")
            }

            return Session(
                file = file,
                record = record,
                output = output,
                sampleRate = sampleRate,
                chunkBytes = chunkBytes,
                sink = options.onPcmChunk?.let { PcmChunkSink() },
                clock = RecordingClock(SystemClock::elapsedRealtime),
            )
        } catch (e: Exception) {
            runCatching { output?.close() }
            runCatching { record?.release() }
            file.delete()
            throw when (e) {
                is RecorderException -> e
                is IOException -> RecorderException(ErrorKind.WRITE_FAILED, "Cannot open recording file", e)
                else -> RecorderException(ErrorKind.MIC_UNAVAILABLE, e.message ?: "Recorder start failed", e)
            }
        }
    }

    private fun launchForwarder(target: Session, callback: (PcmChunk) -> Unit): Job? {
        val sink = target.sink ?: return null
        return scope.launch(Dispatchers.Default) {
            for (chunk in sink.chunks) {
                try {
                    callback(PcmChunk(data = chunk, sampleRate = target.sampleRate))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "PCM chunk consumer failed", e)
                }
            }
        }
    }

    private suspend fun CoroutineScope.capture(target: Session) {
        val buffer = ByteArray(target.chunkBytes)
        var recordStopped = false
        try {
            while (isActive && !target.stopRequested) {
                if (target.paused) {
                    if (!recordStopped) {
                        runCatching { target.record.stop() }
                        recordStopped = true
                    }
                    delay(PAUSE_POLL_MS)
                    continue
                }
                if (recordStopped) {
                    target.record.startRecording()
                    recordStopped = false
                }
                val read = target.record.read(buffer, 0, buffer.size)
                if (read < 0) {
                    throw RecorderException(ErrorKind.CAPTURE_FAILED, "AudioRecord.read failed ($read)")
                }
                if (read == 0) continue
                target.output.write(buffer, 0, read)
                meter.process(buffer, read)
                target.sink?.offer(buffer.copyOf(read))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecorderException) {
            target.failure = e
        } catch (e: IOException) {
            target.failure = RecorderException(ErrorKind.WRITE_FAILED, "Writing audio failed", e)
        } catch (e: Exception) {
            target.failure = RecorderException(ErrorKind.CAPTURE_FAILED, e.message ?: "Capture failed", e)
        } finally {
            val headerError = finish(target)
            if (target.failure == null) target.failure = headerError
        }
        target.failure?.let { error ->
            Log.e(TAG, "Recording failed: ${error.kind}", error)
            _events.tryEmit(Event.Error(error.kind, error.message.orEmpty()))
        }
    }

    /** Releases the recorder and finalizes (or deletes) the file. Returns an error if the WAV header could not be written. */
    private fun finish(target: Session): RecorderException? {
        closeQuietly(target, deleteFile = target.discard)
        if (target.discard) return null
        return try {
            val fileLength = target.file.length()
            RandomAccessFile(target.file, "rw").use { raf ->
                raf.seek(0)
                raf.write(RiffWaveHelper.headerBytes(fileLength.toInt(), sampleRate = target.sampleRate))
            }
            null
        } catch (e: Exception) {
            RecorderException(ErrorKind.WRITE_FAILED, "Writing WAV header failed", e)
        }
    }

    private fun closeQuietly(target: Session, deleteFile: Boolean) {
        runCatching { target.record.stop() }
        runCatching { target.record.release() }
        runCatching { target.output.close() }
        target.sink?.close()
        if (deleteFile) target.file.delete()
    }

    private fun <T> fail(error: RecorderException): Result<T> {
        Log.e(TAG, "Recorder error: ${error.kind}", error)
        _events.tryEmit(Event.Error(error.kind, error.message.orEmpty()))
        return Result.failure(error)
    }

    companion object {
        private const val TAG = "AudioRecorder"
        const val DEFAULT_SAMPLE_RATE = 16000
        const val STOP_TIMEOUT_MS = 2_000L
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
        private const val CHUNK_MS = 20
        private const val PAUSE_POLL_MS = 20L
        private const val WAV_HEADER_SIZE = 44

        fun hasPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
