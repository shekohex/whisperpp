package com.github.shekohex.whisperpp.updater

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerTest {
    private val store = object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
    private val repository = UpdateRepository(store)
    private var checked: UpdateCheckResult = available(66)
    private val downloader = FakeDownloader()
    private val installer = FakeInstaller()

    @Test
    fun newerReleaseDiscardsOldReadyApk_insteadOfInstallingItUnderNewRelease() = runTest {
        repository.saveDownloadState(42, "sha256:65", "nightly-65", "/cache/old.apk", 65)
        checked = available(65)
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.checkForUpdate(this, UpdateChannel.NIGHTLY)
        advanceUntilIdle()
        assertEquals(DownloadState.Ready("/cache/old.apk"), manager.downloadState.value)

        checked = available(66)
        manager.checkForUpdate(this, UpdateChannel.NIGHTLY)
        manager.installUpdate("/cache/old.apk")
        advanceUntilIdle()

        assertEquals(DownloadState.Idle, manager.downloadState.value)
        assertEquals(listOf(42L), downloader.cancelled)
        assertTrue(installer.installed.isEmpty())
        assertEquals(-1L, repository.updateState.first().downloadId)
    }

    @Test
    fun matchingReleaseResumesDownload_andInstallUsesExactRelease() = runTest {
        repository.saveDownloadState(42, "sha256:66", "nightly-66", "/cache/current.apk", 66)
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.checkForUpdate(this, UpdateChannel.NIGHTLY)
        advanceUntilIdle()
        assertEquals(DownloadState.Ready("/cache/current.apk"), manager.downloadState.value)
        manager.installUpdate("/cache/current.apk")
        assertEquals(listOf("/cache/current.apk" to available(66)), installer.installed)
        assertTrue(downloader.cancelled.isEmpty())
    }

    @Test
    fun legacyDownloadWithoutVersionCodeIsNotReused() = runTest {
        repository.saveDownloadState(42, "sha256:66", "nightly-66", "/cache/legacy.apk")
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.checkForUpdate(this)
        advanceUntilIdle()
        assertEquals(DownloadState.Idle, manager.downloadState.value)
        assertEquals(listOf(42L), downloader.cancelled)
    }

    @Test
    fun alreadyInstalledReleaseClearsDownload() = runTest {
        repository.saveDownloadState(42, "sha256:66", "nightly-66", "/cache/current.apk", 66)
        checked = UpdateCheckResult.UpToDate
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.checkForUpdate(this)
        advanceUntilIdle()
        assertEquals(UpdateCheckResult.UpToDate, manager.checkResult.value)
        assertEquals(DownloadState.Idle, manager.downloadState.value)
        assertEquals(-1L, repository.updateState.first().downloadId)
    }

    @Test
    fun failedCheckCannotInstallPreviouslyReadyApk() = runTest {
        repository.saveDownloadState(42, "sha256:66", "nightly-66", "/cache/current.apk", 66)
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.checkForUpdate(this)
        advanceUntilIdle()
        checked = UpdateCheckResult.Error(UpdateError.Network("offline"))
        manager.checkForUpdate(this)
        advanceUntilIdle()
        manager.installUpdate("/cache/current.apk")
        assertTrue(installer.installed.isEmpty())
        assertEquals(InstallResult.Error(InstallFailure.STALE_DOWNLOAD), manager.installResult.value)
    }

    @Test
    fun channelChangeInvalidatesReadyApkImmediately() = runTest {
        repository.saveDownloadState(42, "sha256:66", "nightly-66", "/cache/current.apk", 66)
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.checkForUpdate(this)
        advanceUntilIdle()
        manager.resetForChannel(this)
        manager.installUpdate("/cache/current.apk")
        advanceUntilIdle()
        assertNull(manager.checkResult.value)
        assertEquals(DownloadState.Idle, manager.downloadState.value)
        assertTrue(installer.installed.isEmpty())
    }

    @Test
    fun installerLaunchIsNotSuccess_onlyConfirmedSuccessClearsUpdateCard() = runTest {
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.observeExistingDownload(backgroundScope)
        runCurrent()
        manager.checkForUpdate(this)
        advanceUntilIdle()
        manager.downloadUpdate(this)
        advanceUntilIdle()
        manager.installUpdate("/cache/new.apk")
        runCurrent()
        assertTrue(manager.installResult.value is InstallResult.Installing)
        assertTrue(manager.checkResult.value is UpdateCheckResult.Available)
        installer.installResult.value = InstallResult.Success
        runCurrent()
        assertEquals(UpdateCheckResult.UpToDate, manager.checkResult.value)
        assertEquals(DownloadState.Idle, manager.downloadState.value)
    }

    private fun manager(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = UpdateManager(
        UpdateCheckSource { checked }, repository, downloader, installer, currentVersionCode = 64, ioDispatcher = dispatcher,
    )

    private inner class FakeDownloader : UpdateDownloadSource {
        val cancelled = mutableListOf<Long>()
        override fun monitorDownload(downloadId: Long, expectedSignature: String, destinationFile: File): Flow<DownloadState> =
            flowOf(DownloadState.Ready(destinationFile.path))
        override fun download(url: String, expectedSignature: String, version: String, versionCode: Int): Flow<DownloadState> = flow {
            repository.saveDownloadState(43, expectedSignature, version, "/cache/new.apk", versionCode)
            emit(DownloadState.Ready("/cache/new.apk"))
        }
        override suspend fun cancelDownload(downloadId: Long) {
            cancelled += downloadId
            repository.clearDownloadState()
        }
        override suspend fun cleanup() { repository.clearDownloadState() }
    }

    private class FakeInstaller : UpdateInstallation {
        override val installResult = MutableStateFlow<InstallResult?>(null)
        val installed = mutableListOf<Pair<String, UpdateCheckResult.Available>>()
        override fun install(apkPath: String, update: UpdateCheckResult.Available) {
            installed += apkPath to update
            installResult.value = InstallResult.Installing(0f)
        }
        override fun canInstallPackages() = true
        override fun openInstallPermissionSettings() = true
        override fun launchPendingUserAction(force: Boolean) = true
        override fun reset() { installResult.value = null }
    }

    private fun available(code: Int): UpdateCheckResult.Available {
        val platform = PlatformInfo("sha256:$code", "https://example.com/nightly.apk")
        return UpdateCheckResult.Available(LatestRelease("nightly-$code", code, null, platforms = mapOf("arm64-v8a" to platform)), Abi.ARM64_V8A, platform)
    }
}
