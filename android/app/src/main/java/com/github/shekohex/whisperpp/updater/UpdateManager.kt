package com.github.shekohex.whisperpp.updater

import android.content.Context
import com.github.shekohex.whisperpp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class UpdateManager(context: Context) {
    private val httpClient = HttpClient(OkHttp)
    private val checker = UpdateChecker(httpClient)
    private val repository = UpdateRepository(context)
    private val downloader = UpdateDownloader(context, repository)
    private val installer = UpdateInstaller(context)

    private val _checkResult = MutableStateFlow<UpdateCheckResult?>(null)
    val checkResult: StateFlow<UpdateCheckResult?> = _checkResult.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _installResult = MutableStateFlow<InstallResult?>(null)
    val installResult: StateFlow<InstallResult?> = _installResult.asStateFlow()

    private var currentPlatformInfo: PlatformInfo? = null
    private var currentVersion: String? = null
    private var downloadJob: Job? = null
    private var activeDownloadId: Long = -1L

    init {
        // We can't use a long-running scope here, so we just check once
        // In a real app, this might be better handled by a ViewModel or a persistent Service/Worker
    }

    /**
     * Call this from UI to resume tracking of an ongoing download
     */
    fun observeExistingDownload(scope: CoroutineScope) {
        scope.launch {
            val state = repository.updateState.first()
            if (state.downloadId != -1L && state.expectedSignature != null && state.destinationPath != null) {
                activeDownloadId = state.downloadId
                val file = File(state.destinationPath)
                downloadJob?.cancel()
                downloadJob = scope.launch {
                    downloader.monitorDownload(state.downloadId, state.expectedSignature, file)
                        .collect { _downloadState.value = it }
                }
            }
        }
    }

    fun checkForUpdate(scope: CoroutineScope, channel: UpdateChannel? = null) {
        val effectiveChannel = channel ?: UpdateChannel.fromString(BuildConfig.UPDATE_CHANNEL)

        scope.launch(Dispatchers.IO) {
            _isChecking.value = true
            _checkResult.value = null
            // Only reset download state if we are not already downloading
            if (_downloadState.value is DownloadState.Idle || _downloadState.value is DownloadState.Failed) {
                _downloadState.value = DownloadState.Idle
            }
            _installResult.value = null

            val result = checker.checkForUpdate(effectiveChannel)
            _checkResult.value = result

            if (result is UpdateCheckResult.Available) {
                currentPlatformInfo = result.platformInfo
                currentVersion = result.release.version
            }

            _isChecking.value = false
        }
    }

    fun downloadUpdate(scope: CoroutineScope) {
        val platformInfo = currentPlatformInfo ?: return
        val version = currentVersion ?: return

        downloadJob?.cancel()
        downloadJob = scope.launch {
            downloader.download(platformInfo.url, platformInfo.signature, version)
                .collect { state ->
                    _downloadState.value = state
                }
        }
    }

    fun cancelDownload(scope: CoroutineScope) {
        scope.launch {
            val state = repository.updateState.first()
            if (state.downloadId != -1L) {
                downloader.cancelDownload(state.downloadId)
            }
            downloadJob?.cancel()
            _downloadState.value = DownloadState.Idle
        }
    }

    fun installUpdate(filePath: String) {
        _installResult.value = installer.install(filePath)
    }

    fun openInstallPermissionSettings() {
        installer.openInstallPermissionSettings()
    }

    fun canInstallPackages(): Boolean = installer.canInstallPackages()

    fun cleanup(scope: CoroutineScope) {
        scope.launch {
            downloader.cleanup()
            _downloadState.value = DownloadState.Idle
        }
    }

    fun close() {
        httpClient.close()
    }
}
