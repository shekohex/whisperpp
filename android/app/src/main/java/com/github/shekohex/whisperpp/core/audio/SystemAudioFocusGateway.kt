package com.github.shekohex.whisperpp.core.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * [AudioFocusGateway] backed by [AudioManager]; uses [AudioFocusRequest] on API 26+. The listener and
 * request are built once per callback and reused, so re-requesting never registers a second owner and
 * [abandon] always drops the one the system holds.
 */
class SystemAudioFocusGateway(private val audioManager: AudioManager) : AudioFocusGateway {
    private var boundCallback: ((Int) -> Unit)? = null
    private var listener: AudioManager.OnAudioFocusChangeListener? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun request(onFocusChange: (Int) -> Unit): Boolean {
        if (boundCallback !== onFocusChange) {
            // A different owner: abandon whatever the previous listener held before replacing it.
            abandon()
            boundCallback = onFocusChange
            listener = AudioManager.OnAudioFocusChangeListener { change -> onFocusChange(change) }
            focusRequest = null
        }
        val activeListener = requireNotNull(listener)
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest ?: buildRequest(activeListener).also { focusRequest = it })
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                activeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            listener?.let { audioManager.abandonAudioFocus(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildRequest(listener: AudioManager.OnAudioFocusChangeListener): AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(listener)
            .build()
}
