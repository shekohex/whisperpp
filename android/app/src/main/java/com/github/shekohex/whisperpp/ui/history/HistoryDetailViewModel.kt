package com.github.shekohex.whisperpp.ui.history

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** One transcript: loads it, deletes it and re-enhances its raw text (cancellable). */
class HistoryDetailViewModel(
    private val entryId: Long,
    private val repository: TranscriptHistoryRepository,
    private val reEnhancer: HistoryReEnhancer,
    private val appResolver: HistoryAppResolver,
    /** The kept recording for a stored `audioPath`, or null when there is none to play. */
    private val resolveAudio: (String?) -> File? = { null },
    private val playerFactory: (File) -> HistoryAudioPlayer = ::HistoryAudioPlayer,
) : ViewModel() {
    data class UiState(
        val entry: TranscriptHistoryEntry? = null,
        val isLoading: Boolean = true,
        /** The entry does not exist (anymore): the screen leaves. */
        val isGone: Boolean = false,
        val isEnhancing: Boolean = false,
        val app: HistoryAppInfo? = null,
        /** Present when the entry has a playable kept recording. */
        val player: HistoryAudioPlayer? = null,
    )

    sealed interface Message {
        data class Res(@StringRes val textRes: Int) : Message

        /** Already localised, free of transcript text. */
        data class Text(val text: String) : Message
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val messageChannel = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = messageChannel.receiveAsFlow()

    private var enhanceJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val entry = try {
                repository.get(entryId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messageChannel.send(Message.Res(R.string.history_load_failed))
                null
            }
            val player = resolveAudio(entry?.audioPath)?.let(playerFactory)
            _state.update { it.copy(entry = entry, isLoading = false, isGone = entry == null, player = player) }
            entry?.appPackage?.let { pkg ->
                appResolver.resolve(pkg)?.let { app -> _state.update { it.copy(app = app) } }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                enhanceJob?.cancel()
                _state.value.player?.release()
                repository.delete(listOf(entryId))
                _state.update { it.copy(entry = null, isGone = true, player = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messageChannel.send(Message.Res(R.string.history_delete_failed))
            }
        }
    }

    /** Runs the current enhancement setup over the raw text and stores the result. */
    fun reEnhance() {
        val entry = _state.value.entry ?: return
        if (enhanceJob?.isActive == true) return
        _state.update { it.copy(isEnhancing = true) }
        enhanceJob = viewModelScope.launch {
            try {
                when (val result = reEnhancer.reEnhance(entry)) {
                    is HistoryReEnhancer.Result.Enhanced -> apply(result)
                    is HistoryReEnhancer.Result.Failed -> messageChannel.send(Message.Text(result.message))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messageChannel.send(Message.Res(R.string.history_reenhance_failed))
            } finally {
                _state.update { it.copy(isEnhancing = false) }
            }
        }
    }

    fun pauseAudio() {
        _state.value.player?.pause()
    }

    fun cancelReEnhance() {
        enhanceJob?.cancel()
    }

    private suspend fun apply(result: HistoryReEnhancer.Result.Enhanced) {
        if (!repository.updateEnhancedText(entryId, result.text, result.modelId)) {
            _state.update { it.copy(entry = null, isGone = true) }
            return
        }
        _state.update { s ->
            s.copy(entry = s.entry?.copy(enhancedText = result.text, enhancementModelId = result.modelId))
        }
        messageChannel.send(Message.Res(R.string.history_reenhance_done))
    }

    override fun onCleared() {
        _state.value.player?.release()
    }
}
