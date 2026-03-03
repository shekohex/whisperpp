/*
 * This file is part of Whisper++, see <https://github.com/shekohex/whisperpp>.
 *
 * Copyright (c) 2023-2025 Yan-Bin Diau, Johnson Sun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.shekohex.whisperpp.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.shekohex.whisperpp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class RecorderManager(context: Context) {
    companion object {
        private const val DEFAULT_SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val HEADER_SIZE = 44

        fun requiredPermissions() = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    data class PcmChunk(
        val data: ByteArray,
        val sampleRate: Int,
    )

    data class StartOptions(
        val sampleRate: Int = DEFAULT_SAMPLE_RATE,
        val onPcmChunk: ((PcmChunk) -> Unit)? = null,
    )

    private var currentSampleRate: Int = DEFAULT_SAMPLE_RATE
    private var pcmChunkChannel: Channel<ByteArray>? = null
    private var pcmChunkJob: Job? = null

    private var onUpdateMicrophoneAmplitude: (Int) -> Unit = { }
    private var microphoneAmplitudeUpdateJob: Job? = null
    private val amplitudeReportPeriod: Long
    private var lastAmplitude = 0

    init {
        this.amplitudeReportPeriod =
            context.resources.getInteger(R.integer.recorder_amplitude_report_period).toLong()
    }

    @SuppressLint("MissingPermission")
    fun start(filename: String, options: StartOptions = StartOptions()) {
        stop() // Ensure previous recording is stopped

        currentSampleRate = options.sampleRate

        val minBufferSize = AudioRecord.getMinBufferSize(currentSampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("RecorderManager", "Invalid buffer size")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                currentSampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 4
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("RecorderManager", "AudioRecord not initialized")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            isPaused.set(false)

            val chunkCallback = options.onPcmChunk
            if (chunkCallback != null) {
                val channel = Channel<ByteArray>(capacity = 8)
                pcmChunkChannel = channel
                pcmChunkJob = CoroutineScope(Dispatchers.Default).launch {
                    for (chunk in channel) {
                        runCatching {
                            chunkCallback(PcmChunk(data = chunk, sampleRate = currentSampleRate))
                        }
                    }
                }
            }

            recordingThread = Thread {
                writeAudioDataToFile(filename, minBufferSize)
            }
            recordingThread?.start()

            startAmplitudeUpdates()

        } catch (e: Exception) {
            Log.e("RecorderManager", "Start recording failed", e)
        }
    }

    private fun writeAudioDataToFile(filename: String, bufferSize: Int) {
        val data = ByteArray(bufferSize)
        val file = File(filename)
        if (file.exists()) file.delete()
        
        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(filename)
            // Write placeholder header
            os.write(ByteArray(HEADER_SIZE))
            
            while (isRecording.get()) {
                if (isPaused.get()) {
                    Thread.sleep(100)
                    continue
                }

                val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                if (read > 0) {
                    os.write(data, 0, read)
                    calculateAmplitude(data, read)
                    pcmChunkChannel?.trySend(data.copyOfRange(0, read))
                }
            }
        } catch (e: IOException) {
            Log.e("RecorderManager", "Write failed", e)
        } finally {
            try {
                os?.close()
                writeWavHeader(filename)
            } catch (e: IOException) {
                Log.e("RecorderManager", "Close failed", e)
            }
        }
    }

    private fun writeWavHeader(filename: String) {
        try {
            val file = File(filename)
            val fileLength = file.length()
            // headerBytes expects Total File Size (Header + Data)
            // fileLength is exactly that.
            val header = RiffWaveHelper.headerBytes(fileLength.toInt(), sampleRate = currentSampleRate)

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        } catch (e: Exception) {
            Log.e("RecorderManager", "Wav header write failed", e)
        }
    }

    private fun calculateAmplitude(data: ByteArray, read: Int) {
        if (read < 2) return
        
        var maxVal = 0
        for (i in 0 until read step 2) {
            if (i + 1 < read) {
                val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
                // Convert to signed short
                val amplitude = Math.abs(sample.toShort().toInt())
                if (amplitude > maxVal) maxVal = amplitude
            }
        }
        lastAmplitude = maxVal
    }

    private fun startAmplitudeUpdates() {
        microphoneAmplitudeUpdateJob?.cancel()
        microphoneAmplitudeUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isRecording.get()) {
                if (!isPaused.get()) {
                    onUpdateMicrophoneAmplitude(lastAmplitude)
                } else {
                    onUpdateMicrophoneAmplitude(0)
                }
                delay(amplitudeReportPeriod)
            }
        }
    }

    fun stop() {
        if (!isRecording.get()) return
        
        isRecording.set(false)
        isPaused.set(false)
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("RecorderManager", "Stop failed", e)
        }
        
        try {
            recordingThread?.join(1000)
        } catch (e: InterruptedException) {
            Log.e("RecorderManager", "Thread join interrupted", e)
        }
        
        audioRecord = null
        recordingThread = null

        pcmChunkChannel?.close()
        pcmChunkChannel = null
        pcmChunkJob?.cancel()
        pcmChunkJob = null
        
        microphoneAmplitudeUpdateJob?.cancel()
    }

    fun pause() {
        runCatching { audioRecord?.stop() }
        isPaused.set(true)
    }

    fun resume() {
        runCatching { audioRecord?.startRecording() }
        isPaused.set(false)
    }

    fun setOnUpdateMicrophoneAmplitude(onUpdateMicrophoneAmplitude: (Int) -> Unit) {
        this.onUpdateMicrophoneAmplitude = onUpdateMicrophoneAmplitude
    }

    fun allPermissionsGranted(context: Context): Boolean {
        for (permission in requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }
}
