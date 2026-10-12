package com.github.shekohex.whisperpp.ui.dictionary

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.data.autolearn.AutoLearnSettings
import com.github.shekohex.whisperpp.data.dictionary.DictionaryRepository
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnCoordinator
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnReviewSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Dictionary settings sheet: Auto-Learn switches, review model and schedule, export / import. */
class DictionarySettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val coordinator: AutoLearnCoordinator,
    private val contentResolver: ContentResolver,
) : ViewModel() {

    sealed interface Message {
        data object Exported : Message
        data class Imported(val rules: Int, val terms: Int) : Message
        data object ImportFailed : Message
        data object ExportFailed : Message
    }

    val settings: StateFlow<AutoLearnSettings?> = settingsRepository.autoLearnSettings.stateInScreen(null)
    val providers: StateFlow<List<ServiceProvider>> = settingsRepository.providers.stateInScreen(emptyList())
    val pendingCount: StateFlow<Int> = coordinator.pendingCount.stateInScreen(0)
    val proposalCount: StateFlow<Int> = coordinator.proposalCount.stateInScreen(0)

    val reviewing: StateFlow<Boolean> = AutoLearnReviewUiState.reviewing

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoLearnEnabled(enabled) }

    /** Blank ids inherit the enhancement selection. */
    fun setModel(providerId: String?, modelId: String?) =
        viewModelScope.launch { settingsRepository.setAutoLearnModel(providerId, modelId) }

    fun setSchedule(schedule: AutoLearnReviewSchedule) =
        viewModelScope.launch { settingsRepository.setAutoLearnReviewSchedule(schedule) }

    /** Reviews the pending queue now, whatever the schedule. */
    fun reviewNow() = AutoLearnReviewUiState.review(coordinator)

    fun export(uri: Uri) = viewModelScope.launch {
        val ok = try {
            val json = dictionaryRepository.exportJson()
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) } != null
            }
        } catch (e: IOException) {
            Log.w(TAG, "Dictionary export failed", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Dictionary export failed", e)
            false
        }
        _messages.emit(if (ok) Message.Exported else Message.ExportFailed)
    }

    fun import(uri: Uri) = viewModelScope.launch {
        val json = try {
            withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Dictionary import failed", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Dictionary import failed", e)
            null
        }
        val result = json?.let { dictionaryRepository.importJson(it) }
        val imported = result?.getOrNull()
        _messages.emit(
            if (imported == null) Message.ImportFailed else Message.Imported(imported.replacements.size, imported.vocabulary.size),
        )
    }

    private fun <T> Flow<T>.stateInScreen(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    private companion object {
        const val TAG = "DictionarySettings"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
