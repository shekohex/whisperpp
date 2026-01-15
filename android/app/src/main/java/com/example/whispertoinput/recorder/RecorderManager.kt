/*
 * This file is part of Whisper To Input, see <https://github.com/j3soon/whisper-to-input>.
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

package com.example.whispertoinput.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.whispertoinput.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

private const val MEDIA_RECORDER_CONSTRUCTOR_DEPRECATION_API_LEVEL = 31

class RecorderManager(context: Context) {
    companion object {
        fun requiredPermissions() = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    private var recorder: MediaRecorder? = null
    private var isPaused: Boolean = false
    private var onUpdateMicrophoneAmplitude: (Int) -> Unit = { }
    private var microphoneAmplitudeUpdateJob: Job? = null
    private val amplitudeReportPeriod: Long
    private val context: Context

    init {
        this.context = context
        this.amplitudeReportPeriod =
            context.resources.getInteger(R.integer.recorder_amplitude_report_period).toLong()
    }

    fun start(context: Context, filename: String, useOggFormat: Boolean = false) {
        recorder?.apply {
            try {
                stop()
            } catch (e: IllegalStateException) {
                Log.e("whisper-input", "stop before start failed", e)
            }
            release()
        }

        recorder =
            if (Build.VERSION.SDK_INT >= MEDIA_RECORDER_CONSTRUCTOR_DEPRECATION_API_LEVEL) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

        val file: File = File(filename)
        if (file.exists()) {
            file.delete()
            Log.e("whisper-input", "File should not exist")
        }

        recorder!!.apply {
            setOnErrorListener { _, what, extra ->
                Log.e("whisper-input", "recorder error what=$what extra=$extra")
            }
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            if (useOggFormat) {
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }
            setOutputFile(filename)

            try {
                prepare()
                start()
                isPaused = false
            } catch (e: IOException) {
                Log.e("whisper-input", "prepare failed", e)
            } catch (e: IllegalStateException) {
                Log.e("whisper-input", "start failed", e)
            }
        }

        microphoneAmplitudeUpdateJob?.cancel()
        microphoneAmplitudeUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (recorder != null) {
                val amplitude = if (isPaused) 0 else recorder?.maxAmplitude ?: 0
                onUpdateMicrophoneAmplitude(amplitude)
                delay(amplitudeReportPeriod)
            }
        }
    }

    fun stop() {
        recorder?.apply {
            try {
                stop()
            } catch (e: IllegalStateException) {
                Log.e("whisper-input", "stop failed", e)
            } catch (e: RuntimeException) {
                Log.e("whisper-input", "stop runtime error", e)
            } finally {
                release()
            }
        }
        recorder = null
        isPaused = false

        microphoneAmplitudeUpdateJob?.cancel()
        microphoneAmplitudeUpdateJob = null
    }

    fun pause() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        recorder?.apply {
            try {
                pause()
                isPaused = true
            } catch (e: IllegalStateException) {
                Log.e("whisper-input", "pause failed", e)
            }
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        recorder?.apply {
            try {
                resume()
                isPaused = false
            } catch (e: IllegalStateException) {
                Log.e("whisper-input", "resume failed", e)
            }
        }
    }

    // Assign onUpdateMicrophoneAmplitude callback
    fun setOnUpdateMicrophoneAmplitude(onUpdateMicrophoneAmplitude: (Int) -> Unit) {
        this.onUpdateMicrophoneAmplitude = onUpdateMicrophoneAmplitude
    }

    // Returns whether all of the permissions are granted.
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