package com.github.shekohex.whisperpp.updater

import android.content.Context
import com.github.shekohex.whisperpp.BuildConfig
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class UpdateManager internal constructor(
    private val checker: UpdateCheckSource,
    private val repository: UpdateRepository,
    private val downloader: UpdateDownloadSource,
    private val installer: UpdateInstallation,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(context: Context, httpClient: HttpClient, installer: UpdateInstallation) : this(
        UpdateChecker(httpClient), UpdateRepository(context), UpdateDownloader(context, UpdateRepository(context)), installer,
    )

    private val _checkResult = MutableStateFlow<UpdateCheckResult?>(null)
    val checkResult: StateFlow<UpdateCheckResult?> = _checkResult.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _installResult = MutableStateFlow<InstallResult?>(null)
    val installResult: StateFlow<InstallResult?> = _installResult.asStateFlow()

    private var currentUpdate: UpdateCheckResult.Available? = null
    private var downloadJob: Job? = null
    private var checkJob: Job? = null
    private var installObservationJob: Job? = null
    private val cacheMutex = Mutex()
    @Volatile private var generation = 0L

    fun observeExistingDownload(scope: CoroutineScope) {
        installObservationJob?.cancel()
        installObservationJob = scope.launch {
            installer.installResult.collect { result ->
                _installResult.value = result
                if (result == InstallResult.Success) {
                    currentUpdate = null
                    _downloadState.value = DownloadState.Idle
                    _checkResult.value = UpdateCheckResult.UpToDate
                }
            }
        }
    }

    fun checkForUpdate(scope: CoroutineScope, channel: UpdateChannel? = null) {
        if (installer.installResult.value.isInstalling) return
        val effectiveChannel = channel ?: UpdateChannel.fromString(BuildConfig.UPDATE_CHANNEL)
        val token = invalidate()
        _isChecking.value = true
        installer.reset()
        _installResult.value = null
        checkJob = scope.launch(ioDispatcher) {
            try {
                val result = checker.checkForUpdate(effectiveChannel)
                cacheMutex.withLock {
                    if (generation != token) return@withLock
                    val saved = repository.updateState.first()
                    if (result == UpdateCheckResult.UpToDate ||
                        (result is UpdateCheckResult.Available && !saved.matches(result, currentVersionCode))
                    ) {
                        discardDownload(saved)
                    }
                    if (generation != token) return@withLock
                    currentUpdate = result as? UpdateCheckResult.Available
                    _checkResult.value = result
                    if (result is UpdateCheckResult.Available && saved.matches(result, currentVersionCode)) {
                        downloadJob = scope.launch(ioDispatcher) {
                            downloader.monitorDownload(saved.downloadId, saved.expectedSignature!!, File(saved.destinationPath!!))
                                .collect { if (generation == token) _downloadState.value = it }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == token) _checkResult.value = UpdateCheckResult.Error(UpdateChecker.classifyException(e))
            } finally {
                if (generation == token) _isChecking.value = false
            }
        }
    }

    fun downloadUpdate(scope: CoroutineScope) {
        val update = currentUpdate ?: return
        if (_isChecking.value || installer.installResult.value.isInstalling || downloadJob?.isActive == true) return
        if (_downloadState.value !is DownloadState.Idle && _downloadState.value !is DownloadState.Failed) return
        val token = generation
        installer.reset()
        _installResult.value = null
        downloadJob = scope.launch(ioDispatcher) {
            downloader.download(update.platformInfo.url, update.platformInfo.signature, update.release.version, update.release.versionCode)
                .collect { if (generation == token) _downloadState.value = it }
        }
    }

    fun cancelDownload(scope: CoroutineScope) {
        if (installer.installResult.value.isInstalling) return
        val token = ++generation
        downloadJob?.cancel()
        downloadJob = scope.launch(ioDispatcher) {
            cacheMutex.withLock {
                discardDownload(repository.updateState.first())
                if (generation == token) _downloadState.value = DownloadState.Idle
            }
        }
    }

    fun installUpdate(filePath: String) {
        val update = currentUpdate
        if (update == null || (_downloadState.value as? DownloadState.Ready)?.filePath != filePath) {
            _installResult.value = InstallResult.Error(InstallFailure.STALE_DOWNLOAD)
            return
        }
        installer.install(filePath, update)
    }

    fun openInstallPermissionSettings() {
        if (!installer.openInstallPermissionSettings()) {
            _installResult.value = InstallResult.Error(InstallFailure.PERMISSION_SETTINGS_UNAVAILABLE)
        }
    }

    /** Permanent failure recorded by the background check, for the UI to show. */
    val lastCheckError: Flow<String?> = repository.lastCheckError

    fun canInstallPackages(): Boolean = installer.canInstallPackages()

    fun launchPendingUserAction(force: Boolean = false) = installer.launchPendingUserAction(force)

    fun resetForChannel(scope: CoroutineScope) {
        if (installer.installResult.value.isInstalling) return
        invalidate()
        _isChecking.value = false
        installer.reset()
        _installResult.value = null
        downloadJob = scope.launch(ioDispatcher) {
            cacheMutex.withLock { discardDownload(repository.updateState.first()) }
        }
    }

    private fun invalidate(): Long {
        generation++
        checkJob?.cancel()
        downloadJob?.cancel()
        currentUpdate = null
        _checkResult.value = null
        _downloadState.value = DownloadState.Idle
        return generation
    }

    private suspend fun discardDownload(saved: PersistedUpdateState) {
        if (saved.downloadId != -1L) downloader.cancelDownload(saved.downloadId)
        downloader.cleanup()
    }
}
