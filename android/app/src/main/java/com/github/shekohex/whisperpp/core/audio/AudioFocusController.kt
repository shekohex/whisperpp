package com.github.shekohex.whisperpp.core.audio

import android.media.AudioManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Holds transient exclusive audio focus while recording so media apps pause and resume (§4.3).
 * Only releases focus it actually acquired. Call from the main thread.
 */
class AudioFocusController(
    private val gateway: AudioFocusGateway,
    private val pauseMediaEnabled: StateFlow<Boolean>,
) {
    private var held = false
    private var lost = false

    private val _focusLoss = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits when another app takes focus permanently or transiently (e.g. a phone call). */
    val focusLoss: SharedFlow<Unit> = _focusLoss.asSharedFlow()

    val isHeld: Boolean
        get() = held

    /** Returns true when focus is held after the call. Disabled setting → no request, returns false. */
    fun acquire(): Boolean {
        if (!pauseMediaEnabled.value) return false
        if (held && !lost) return true
        val granted = gateway.request(::onFocusChange)
        if (granted) {
            held = true
            lost = false
        }
        return granted
    }

    fun release() {
        if (!held) return
        held = false
        lost = false
        gateway.abandon()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                if (!held) return
                lost = true
                _focusLoss.tryEmit(Unit)
            }
            AudioManager.AUDIOFOCUS_GAIN -> lost = false
        }
    }
}
