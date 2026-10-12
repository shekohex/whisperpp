package com.github.shekohex.whisperpp.ui.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.BackupStatusStore
import com.github.shekohex.whisperpp.data.ImportAnalysis
import com.github.shekohex.whisperpp.data.RestoreMode
import com.github.shekohex.whisperpp.data.RestoreSummary
import com.github.shekohex.whisperpp.data.SecretsStoreProviderCredentials
import com.github.shekohex.whisperpp.data.SettingsBackupEnvelope
import com.github.shekohex.whisperpp.data.SettingsBackupRepository
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.ui.backup.BackupNotice.Tone
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export / preview / apply for encrypted backups. Owns the [SettingsBackupRepository] so screens no
 * longer build repositories. The restore wizard's dialog steps stay in the screen until WP-3.6.
 */
class BackupRestoreViewModel(
    container: AppContainer,
    private val appContext: Context,
    val appVersionName: String,
) : ViewModel() {

    data class UiState(
        val exportNotice: BackupNotice? = null,
        val importNotice: BackupNotice? = null,
        val importAnalysis: ImportAnalysis? = null,
        val restoreSummary: RestoreSummary? = null,
        val selectedMergeCategories: Set<String> = emptySet(),
        val isExporting: Boolean = false,
        val isAnalyzingImport: Boolean = false,
        val isApplyingImport: Boolean = false,
        /** Opt-in: add the transcript history (text only) to the next export. */
        val includeHistory: Boolean = false,
    ) {
        val isBusy: Boolean get() = isExporting || isAnalyzingImport || isApplyingImport
    }

    private val gson = Gson()
    private val statusStore: BackupStatusStore = container.backupStatusStore
    private val repository: SettingsBackupRepository = SecretsStoreProviderCredentials(container.secretsStore).let {
        SettingsBackupRepository(
            dataStore = container.settingsDataStore,
            credentialSource = it,
            credentialSink = it,
            gson = gson,
            currentAppVersionNameProvider = { appVersionName },
            historySource = container.transcriptHistoryRepository,
        )
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setIncludeHistory(include: Boolean) = _state.update { it.copy(includeHistory = include) }

    fun onExportStarted() = _state.update { it.copy(isExporting = true) }

    fun onExportCancelled() = _state.update { it.copy(isExporting = false) }

    fun export(uri: Uri, password: String, exportedAtUtc: String, onMessage: (Int) -> Unit) {
        val includeHistory = _state.value.includeHistory
        viewModelScope.launch {
            try {
                val envelope = repository.exportEncryptedBackup(
                    password = password,
                    appVersionName = appVersionName,
                    exportedAtUtc = exportedAtUtc,
                    includeHistory = includeHistory,
                )
                withContext(Dispatchers.IO) {
                    val output = appContext.contentResolver.openOutputStream(uri)
                        ?: throw BackupFileException(R.string.backup_error_open_destination)
                    output.use { it.write(gson.toJson(envelope).toByteArray()) }
                }
                val exportedAt = formatBackupDisplayTime(exportedAtUtc)
                _state.update {
                    it.copy(
                        exportNotice = BackupNotice(
                            title = R.string.backup_export_ready_title,
                            message = R.string.backup_export_ready_message,
                            tone = Tone.Success,
                            formatArgs = listOf(exportedAt),
                            extraMessage = if (includeHistory) R.string.backup_history_export_notice else null,
                        ),
                    )
                }
                statusStore.update(appContext.getString(R.string.backup_status_last_export, exportedAt), repairCount = 0)
                onMessage(R.string.backup_toast_exported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Backup export failed", e)
                _state.update {
                    it.copy(
                        exportNotice = BackupNotice(
                            title = R.string.backup_export_failed_title,
                            message = exportErrorMessage(e),
                            tone = Tone.Error,
                        ),
                    )
                }
                onMessage(R.string.backup_toast_export_failed)
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    /** Clears the previous preview/summary before a new file is picked. */
    fun resetImport() = _state.update {
        it.copy(importAnalysis = null, restoreSummary = null, selectedMergeCategories = emptySet(), importNotice = null)
    }

    /** Decrypts and previews [uri]; [onResult] gets `null` on success or the error message to show. */
    fun analyze(uri: Uri, mode: RestoreMode, password: String, onResult: (Int?) -> Unit) {
        _state.update { it.copy(isAnalyzingImport = true) }
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw BackupFileException(R.string.backup_error_read_failed)
                val envelope = gson.fromJson(raw, SettingsBackupEnvelope::class.java)
                    ?: throw BackupFileException(R.string.backup_error_invalid_file)
                val analysis = repository.analyzeEncryptedBackup(envelope = envelope, password = password, restoreMode = mode)
                _state.update {
                    it.copy(
                        importAnalysis = analysis,
                        restoreSummary = null,
                        selectedMergeCategories = analysis.categoryPreviews
                            .filter { preview -> preview.selectable && preview.includedByDefault }
                            .map { preview -> preview.categoryId }
                            .toSet(),
                        importNotice = BackupNotice(
                            title = R.string.backup_preview_ready_title,
                            message = R.string.backup_preview_ready_message,
                            tone = Tone.Info,
                        ),
                    )
                }
                onResult(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Backup preview failed", e)
                val message = importErrorMessage(e)
                _state.update {
                    it.copy(
                        importNotice = BackupNotice(
                            title = R.string.backup_preview_failed_title,
                            message = message,
                            tone = Tone.Error,
                        ),
                    )
                }
                onResult(message)
            } finally {
                _state.update { it.copy(isAnalyzingImport = false) }
            }
        }
    }

    fun toggleCategory(categoryId: String) = _state.update {
        val selected = it.selectedMergeCategories
        it.copy(selectedMergeCategories = if (categoryId in selected) selected - categoryId else selected + categoryId)
    }

    /** Applies the current preview; [onApplied] runs after success so the screen can clear its wizard. */
    fun applyImport(onApplied: () -> Unit, onMessage: (Int) -> Unit) {
        val current = _state.value
        val analysis = current.importAnalysis ?: return
        _state.update { it.copy(isApplyingImport = true) }
        viewModelScope.launch {
            try {
                val summary = repository.applyImportAnalysis(
                    analysis = analysis,
                    // Overwrite restores every default category; only opt-in ones (history) are picked here.
                    includedCategoryIds = current.selectedMergeCategories,
                )
                val repairCount = summary.repairChecklist.size
                val homeStatus = if (repairCount > 0) {
                    appContext.resources.getQuantityString(R.plurals.backup_status_restore_repairs, repairCount, repairCount)
                } else {
                    appContext.getString(R.string.backup_status_restore_complete)
                }
                statusStore.update(homeStatus, repairCount)
                _state.update {
                    it.copy(
                        restoreSummary = summary,
                        importAnalysis = null,
                        selectedMergeCategories = emptySet(),
                        importNotice = if (repairCount > 0) {
                            BackupNotice(
                                title = R.string.backup_restore_complete_followup_title,
                                message = R.string.backup_restore_complete_followup_message,
                                tone = Tone.Warning,
                            )
                        } else {
                            BackupNotice(
                                title = R.string.backup_restore_complete_title,
                                message = R.string.backup_restore_complete_message,
                                tone = Tone.Success,
                            )
                        },
                    )
                }
                onApplied()
                onMessage(if (repairCount > 0) R.string.backup_toast_restored_with_repairs else R.string.backup_toast_restored)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Backup restore failed", e)
                _state.update {
                    it.copy(
                        importNotice = BackupNotice(
                            title = R.string.backup_restore_failed_title,
                            message = R.string.backup_error_apply_failed,
                            tone = Tone.Error,
                        ),
                    )
                }
                onMessage(R.string.backup_toast_restore_failed)
            } finally {
                _state.update { it.copy(isApplyingImport = false) }
            }
        }
    }

    private companion object {
        const val TAG = "BackupRestore"
    }
}
