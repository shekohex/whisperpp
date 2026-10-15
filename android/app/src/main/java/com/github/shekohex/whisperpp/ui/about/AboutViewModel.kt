package com.github.shekohex.whisperpp.ui.about

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.shekohex.whisperpp.BuildConfig
import com.github.shekohex.whisperpp.data.PreferenceKeys.UPDATE_CHANNEL
import com.github.shekohex.whisperpp.data.PreferenceKeys.VERBOSE_NETWORK_LOGS_ENABLED
import com.github.shekohex.whisperpp.di.AppContainer
import com.github.shekohex.whisperpp.ui.settings.common.PreferencesViewModel
import com.github.shekohex.whisperpp.updater.DownloadState
import com.github.shekohex.whisperpp.updater.InstallResult
import com.github.shekohex.whisperpp.updater.UpdateChannel
import com.github.shekohex.whisperpp.updater.UpdateCheckResult
import com.github.shekohex.whisperpp.updater.UpdateManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** Version, update channel / check / download (via [UpdateManager]) and advanced toggles. */
class AboutViewModel(
    container: AppContainer,
    appContext: Context,
    val versionName: String,
) : PreferencesViewModel(container.settingsDataStore) {

    private val updateManager = UpdateManager(appContext, container.updaterHttpClient, container.updateInstaller)

    val checkResult: StateFlow<UpdateCheckResult?> = updateManager.checkResult
    val downloadState: StateFlow<DownloadState> = updateManager.downloadState
    val isChecking: StateFlow<Boolean> = updateManager.isChecking
    val installResult: StateFlow<InstallResult?> = updateManager.installResult

    /** Stored channel name; `null` until preferences load. */
    val channel: StateFlow<String?> = container.settingsDataStore.data
        .map { it[UPDATE_CHANNEL] ?: BuildConfig.UPDATE_CHANNEL }
        .stateInScreen(null)

    val verboseLogs: StateFlow<Boolean?> = container.settingsDataStore.data
        .map { it[VERBOSE_NETWORK_LOGS_ENABLED] ?: false }
        .stateInScreen(null)

    private var autoCheckDone = false

    init {
        updateManager.observeExistingDownload(viewModelScope)
    }

    fun setChannel(nightly: Boolean) {
        updateManager.resetForChannel(viewModelScope)
        set(UPDATE_CHANNEL, if (nightly) CHANNEL_NIGHTLY else CHANNEL_STABLE)
    }

    fun setVerboseLogs(enabled: Boolean) = set(VERBOSE_NETWORK_LOGS_ENABLED, enabled)

    fun checkForUpdate() {
        updateManager.checkForUpdate(viewModelScope, UpdateChannel.fromString(channel.value ?: BuildConfig.UPDATE_CHANNEL))
    }

    /** Runs one check when opened from the update notification (`showUpdate`). */
    fun checkOnceOnOpen(channelName: String) {
        if (autoCheckDone) return
        autoCheckDone = true
        updateManager.checkForUpdate(viewModelScope, UpdateChannel.fromString(channelName))
    }

    fun download() = updateManager.downloadUpdate(viewModelScope)

    fun cancelDownload() = updateManager.cancelDownload(viewModelScope)

    fun install(path: String) = updateManager.installUpdate(path)

    fun openInstallPermissionSettings() = updateManager.openInstallPermissionSettings()

    fun canInstallPackages(): Boolean = updateManager.canInstallPackages()

    fun launchPendingUserAction() { updateManager.launchPendingUserAction(force = true) }

    fun resumeInstallation() {
        if (installResult.value == InstallResult.PermissionRequired && canInstallPackages()) {
            (downloadState.value as? DownloadState.Ready)?.let { install(it.filePath) }
        }
        updateManager.launchPendingUserAction()
    }

    companion object {
        const val CHANNEL_STABLE = "stable"
        const val CHANNEL_NIGHTLY = "nightly"
    }
}
