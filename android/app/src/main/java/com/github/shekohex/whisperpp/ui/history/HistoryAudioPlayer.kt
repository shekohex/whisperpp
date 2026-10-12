package com.github.shekohex.whisperpp.ui.history

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException

/** Play/pause for one kept recording. Call [release] when done; the instance is unusable afterwards. */
class HistoryAudioPlayer(file: File) {
    data class State(
        val isReady: Boolean = false,
        val isPlaying: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val hasError: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var released = false
    private val player = MediaPlayer()

    init {
        try {
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { mp ->
                _state.update { it.copy(isReady = true, durationMs = mp.duration.coerceAtLeast(0)) }
            }
            player.setOnCompletionListener { mp ->
                _state.update { it.copy(isPlaying = false, positionMs = mp.duration.coerceAtLeast(0)) }
            }
            player.setOnErrorListener { _, _, _ ->
                _state.update { it.copy(isPlaying = false, hasError = true) }
                true
            }
            player.prepareAsync()
        } catch (_: IOException) {
            _state.value = State(hasError = true)
        } catch (_: IllegalStateException) {
            _state.value = State(hasError = true)
        }
    }

    fun toggle() {
        val current = _state.value
        if (released || !current.isReady || current.hasError) return
        try {
            if (current.isPlaying) {
                player.pause()
                _state.update { it.copy(isPlaying = false, positionMs = player.currentPosition) }
            } else {
                // Restart from the top after the clip played to its end.
                if (current.positionMs >= current.durationMs) player.seekTo(0)
                player.start()
                _state.update { it.copy(isPlaying = true) }
            }
        } catch (_: IllegalStateException) {
            _state.update { it.copy(isPlaying = false, hasError = true) }
        }
    }

    fun pause() {
        if (_state.value.isPlaying) toggle()
    }

    /** Refreshes [State.positionMs] from the player; cheap, call while playing. */
    fun refreshPosition() {
        if (released || !_state.value.isPlaying) return
        try {
            _state.update { it.copy(positionMs = player.currentPosition) }
        } catch (_: IllegalStateException) {
            _state.update { it.copy(isPlaying = false, hasError = true) }
        }
    }

    fun release() {
        if (released) return
        released = true
        player.release()
        _state.update { it.copy(isPlaying = false, isReady = false) }
    }
}
