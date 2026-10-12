package com.github.shekohex.whisperpp.ui.history

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.text.WordCounter
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryRepository
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One take: loads it, deletes it, re-enhances its raw text and retries a failed transcription from
 * the saved recording (both cancellable, off the main thread). A retry needs no keyboard: it only
 * updates the entry, and the user copies the result from here.
 */
class HistoryDetailViewModel(
    private val entryId: Long,
    private val repository: TranscriptHistoryRepository,
    private val reEnhancer: HistoryReEnhancer,
    private val transcriber: HistoryTranscriber,
    private val appResolver: HistoryAppResolver,
    /** Whether finished takes keep their audio ("Keep recordings"). */
    private val keepAudio: suspend () -> Boolean = { true },
    /** The kept recording for a stored `audioPath`, or null when there is none to play. */
    private val resolveAudio: (String?) -> File? = { null },
    /** Null skips playback (the screen then has no player card); tests pass it to stay off MediaPlayer. */
    private val playerFactory: (File) -> HistoryAudioPlayer? = ::HistoryAudioPlayer,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    data class UiState(
        val entry: TranscriptHistoryEntry? = null,
        val isLoading: Boolean = true,
        /** The entry does not exist (anymore): the screen leaves. */
        val isGone: Boolean = false,
        val isEnhancing: Boolean = false,
        /** The saved recording is being transcribed again. */
        val isRetrying: Boolean = false,
        /** Whether Retry is offered: audio is saved and nothing is working on the take. */
        val canRetry: Boolean = false,
        val app: HistoryAppInfo? = null,
        /** Present when the entry has a playable kept recording. */
        val player: HistoryAudioPlayer? = null,
    ) {
        val isWorking: Boolean get() = isEnhancing || isRetrying
    }

    sealed interface Message {
        data class Res(@StringRes val textRes: Int) : Message

        /** A string resource with one already-localised argument (never transcript text). */
        data class Format(@StringRes val textRes: Int, val argument: String) : Message

        /** Already localised, free of transcript text. */
        data class Text(val text: String) : Message
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val messageChannel = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = messageChannel.receiveAsFlow()

    private var enhanceJob: Job? = null
    private var retryJob: Job? = null
    private var audioFile: File? = null

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
            audioFile = resolveAudio(entry?.audioPath)
            val player = audioFile?.let(playerFactory)
            _state.update {
                it.copy(entry = entry, isLoading = false, isGone = entry == null, player = player, canRetry = canRetry(entry))
            }
            entry?.appPackage?.let { pkg ->
                appResolver.resolve(pkg)?.let { app -> _state.update { it.copy(app = app) } }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                enhanceJob?.cancel()
                retryJob?.cancel()
                _state.value.player?.release()
                repository.delete(listOf(entryId))
                _state.update { it.copy(entry = null, isGone = true, player = null, canRetry = false) }
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
        if (!entry.hasText || _state.value.isWorking) return
        _state.update { it.copy(isEnhancing = true, canRetry = false) }
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
                _state.update { it.copy(isEnhancing = false, canRetry = canRetry(it.entry)) }
            }
        }
    }

    /**
     * Transcribes the saved recording again with today's speech-to-text settings. Success fills the
     * raw text and, when the entry's mode enhances, the enhanced text; failure keeps the take FAILED
     * with the new reason. Cancelling leaves it as it was (or, once the raw text is saved, finished).
     */
    fun retryTranscription() {
        val entry = _state.value.entry ?: return
        if (_state.value.isWorking) return
        val audio = audioFile
        if (audio == null) {
            viewModelScope.launch { messageChannel.send(Message.Res(R.string.history_retry_no_audio)) }
            return
        }
        _state.update { it.copy(isRetrying = true, canRetry = false) }
        retryJob = viewModelScope.launch { runRetry(entry, audio) }
    }

    /** Resolves a failed take whose text is already there (e.g. it was copied): it stops asking for attention. */
    fun markResolved() {
        viewModelScope.launch {
            try {
                val updated = repository.complete(entryId, keepAudio())
                if (updated == null) goneNow() else publish(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messageChannel.send(Message.Res(R.string.history_settings_failed))
            }
        }
    }

    fun pauseAudio() {
        _state.value.player?.pause()
    }

    fun cancelReEnhance() {
        enhanceJob?.cancel()
    }

    fun cancelRetry() {
        retryJob?.cancel()
    }

    private suspend fun runRetry(entry: TranscriptHistoryEntry, audio: File) {
        // Set once the new raw text is stored: from then on a cancel must still finish the take.
        var stored: TranscriptHistoryEntry? = null
        try {
            when (val result = transcriber.transcribe(audio, entry)) {
                is HistoryTranscriber.Result.Text -> {
                    val saved = repository.update(entryId) { it.withRetryText(result) } ?: return goneNow()
                    stored = saved
                    publish(saved)
                    publish(enhanceAndComplete(saved))
                    stored = null
                    messageChannel.send(Message.Res(R.string.history_retry_done))
                }
                HistoryTranscriber.Result.Empty ->
                    failRetry(HistoryFailureReason.EMPTY_TRANSCRIPT, Message.Res(R.string.history_retry_empty))
                is HistoryTranscriber.Result.Failed ->
                    failRetry(result.reason, Message.Format(R.string.history_retry_failed, result.message))
            }
        } catch (e: CancellationException) {
            stored?.let { saved -> withContext(NonCancellable) { finishRaw(saved) } }
            throw e
        } catch (_: Exception) {
            failRetry(HistoryFailureReason.TRANSCRIPTION_FAILED, Message.Res(R.string.history_retry_failed_generic))
        } finally {
            _state.update { it.copy(isRetrying = false, isEnhancing = false, canRetry = canRetry(it.entry)) }
        }
    }

    /** Enhances the retried raw text when the entry's mode does; a failure keeps the raw text. */
    private suspend fun enhanceAndComplete(saved: TranscriptHistoryEntry): TranscriptHistoryEntry {
        if (!transcriber.enhancementEnabled(saved)) return finishRaw(saved)
        _state.update { it.copy(isEnhancing = true) }
        val result = try {
            reEnhancer.reEnhance(saved)
        } finally {
            _state.update { it.copy(isEnhancing = false) }
        }
        return when (result) {
            is HistoryReEnhancer.Result.Enhanced -> repository.complete(entryId, keepAudio()) {
                it.copy(
                    enhancedText = result.text,
                    enhancementModelId = result.modelId,
                    wordCount = WordCounter.count(result.text),
                    failureReason = null,
                )
            } ?: saved
            is HistoryReEnhancer.Result.Failed -> {
                messageChannel.send(Message.Text(result.message))
                finishRaw(saved, HistoryFailureReason.ENHANCEMENT_FAILED)
            }
        }
    }

    private suspend fun finishRaw(saved: TranscriptHistoryEntry, reason: HistoryFailureReason? = null): TranscriptHistoryEntry {
        val done = repository.complete(entryId, keepAudio()) { it.copy(failureReason = reason) } ?: saved
        publish(done)
        return done
    }

    private suspend fun failRetry(reason: HistoryFailureReason, message: Message) {
        val failed = repository.update(entryId) { it.copy(status = HistoryStatus.FAILED, failureReason = reason) }
        if (failed == null) goneNow() else publish(failed)
        messageChannel.send(message)
    }

    private fun TranscriptHistoryEntry.withRetryText(result: HistoryTranscriber.Result.Text) = copy(
        rawText = result.text,
        enhancedText = null,
        enhancementModelId = null,
        wordCount = WordCounter.count(result.text),
        providerId = result.providerId,
        sttModelId = result.sttModelId,
        language = result.languageCode,
        status = HistoryStatus.TRANSCRIBED,
        failureReason = null,
    )

    private suspend fun apply(result: HistoryReEnhancer.Result.Enhanced) {
        if (!repository.updateEnhancedText(entryId, result.text, result.modelId)) {
            goneNow()
            return
        }
        _state.update { s ->
            s.copy(entry = s.entry?.copy(enhancedText = result.text, enhancementModelId = result.modelId))
        }
        messageChannel.send(Message.Res(R.string.history_reenhance_done))
    }

    /** Shows [entry]; when its audio is gone (finished take, keep-recordings off) the player goes too. */
    private fun publish(entry: TranscriptHistoryEntry) {
        if (entry.audioPath == null) {
            _state.value.player?.release()
            audioFile = null
        }
        _state.update { s ->
            s.copy(entry = entry, player = if (entry.audioPath == null) null else s.player)
        }
    }

    private fun goneNow() {
        _state.update { it.copy(entry = null, isGone = true, canRetry = false) }
    }

    private fun canRetry(entry: TranscriptHistoryEntry?): Boolean =
        entry != null && audioFile != null && entry.isRetryable(clock())

    override fun onCleared() {
        _state.value.player?.release()
    }
}
