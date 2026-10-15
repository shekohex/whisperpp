package com.github.shekohex.whisperpp.updater

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.github.shekohex.whisperpp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

sealed class InstallResult {
    data object Success : InstallResult()
    data object PermissionRequired : InstallResult()
    data class Preparing(val progress: Float) : InstallResult()
    data class Installing(val progress: Float) : InstallResult()
    data class UserActionRequired(val sessionId: Int) : InstallResult()
    data class Error(val reason: InstallFailure) : InstallResult()
}

val InstallResult?.isInstalling: Boolean
    get() = this is InstallResult.Preparing || this is InstallResult.Installing || this is InstallResult.UserActionRequired

enum class InstallFailure {
    STALE_DOWNLOAD, FILE_MISSING, CHECKSUM_MISMATCH, INVALID_APK, WRONG_PACKAGE, WRONG_VERSION,
    NOT_NEWER, SIGNATURE_MISMATCH, PERMISSION_SETTINGS_UNAVAILABLE, INSTALLER_UNAVAILABLE,
    PREPARATION_FAILED, BLOCKED, ABORTED, CONFLICT, INSUFFICIENT_STORAGE, UNKNOWN,
}

interface UpdateInstallation {
    val installResult: StateFlow<InstallResult?>
    fun install(apkPath: String, update: UpdateCheckResult.Available)
    fun canInstallPackages(): Boolean
    fun openInstallPermissionSettings(): Boolean
    fun launchPendingUserAction(force: Boolean = false): Boolean
    fun reset()
}

class UpdateInstaller(
    private val context: Context,
    private val repository: UpdateRepository,
    private val scope: CoroutineScope,
) : UpdateInstallation {
    private val packages = context.packageManager
    private val sessions = packages.packageInstaller
    private val mutex = Mutex()
    private val _installResult = MutableStateFlow<InstallResult?>(null)
    override val installResult: StateFlow<InstallResult?> = _installResult.asStateFlow()
    private var installJob: Job? = null
    private var pendingUserAction: Intent? = null
    private var confirmationLaunched = false
    @Volatile private var activeSessionId = -1

    init {
        sessions.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onCreated(sessionId: Int) = Unit
            override fun onBadgingChanged(sessionId: Int) = Unit
            override fun onActiveChanged(sessionId: Int, active: Boolean) = Unit
            override fun onFinished(sessionId: Int, success: Boolean) = Unit
            override fun onProgressChanged(sessionId: Int, progress: Float) {
                if (sessionId == activeSessionId && _installResult.value is InstallResult.Installing) {
                    _installResult.value = InstallResult.Installing(progress.coerceIn(0f, 1f))
                }
            }
        }, Handler(Looper.getMainLooper()))
    }

    override fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Opens the "install unknown apps" page for this app; false if no activity could handle it. */
    override fun openInstallPermissionSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    override fun install(apkPath: String, update: UpdateCheckResult.Available) {
        if (installJob?.isActive == true || _installResult.value.isInstalling) return
        if (!canInstallPackages()) {
            _installResult.value = InstallResult.PermissionRequired
            return
        }
        _installResult.value = InstallResult.Preparing(0f)
        installJob = scope.launch {
            mutex.withLock {
                repository.installState.first()?.let { abandon(it.sessionId) }
                repository.clearInstallSession()
                clearPendingAction()
                prepareAndCommit(File(apkPath), update)
            }
        }
    }

    private suspend fun prepareAndCommit(file: File, update: UpdateCheckResult.Available) {
        var sessionId = -1
        var committed = false
        try {
            val failure = validate(file, update)
            if (failure != null) {
                _installResult.value = InstallResult.Error(failure)
                return
            }
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setAppLabel(context.getString(R.string.app_name))
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                }
            }
            sessionId = sessions.createSession(params)
            activeSessionId = sessionId
            repository.saveInstallSession(sessionId, update.release.versionCode)
            sessions.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var count = input.read(buffer)
                        while (count != -1) {
                            output.write(buffer, 0, count)
                            copied += count
                            val progress = (copied.toFloat() / file.length()).coerceIn(0f, 1f)
                            session.setStagingProgress(progress)
                            _installResult.value = InstallResult.Preparing(progress)
                            count = input.read(buffer)
                        }
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, UpdateInstallStatusReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                    data = statusUri(sessionId)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val statusReceiver = PendingIntent.getBroadcast(context, sessionId, callback, flags)
                session.commit(statusReceiver.intentSender)
                committed = true
                _installResult.value = InstallResult.Installing(0f)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            _installResult.value = InstallResult.Error(InstallFailure.BLOCKED)
        } catch (e: Exception) {
            _installResult.value = InstallResult.Error(InstallFailure.PREPARATION_FAILED)
        } finally {
            if (!committed && sessionId != -1) {
                abandon(sessionId)
                repository.clearInstallSession()
                activeSessionId = -1
            }
        }
    }

    private suspend fun validate(file: File, update: UpdateCheckResult.Available): InstallFailure? {
        if (!file.isFile) return InstallFailure.FILE_MISSING
        if (ApkVerifier.verify(file, update.platformInfo.signature) != ApkVerifier.Result.Match) {
            return InstallFailure.CHECKSUM_MISMATCH
        }
        val candidate = packages.getPackageArchiveInfo(file.absolutePath, signatureFlags())
            ?: return InstallFailure.INVALID_APK
        return ApkVerifier.validateIdentity(identity(candidate), identity(installedPackage()), update.release)
    }

    override fun launchPendingUserAction(force: Boolean): Boolean {
        val intent = pendingUserAction ?: return false
        val sessionId = (_installResult.value as? InstallResult.UserActionRequired)?.sessionId ?: return false
        if (confirmationLaunched && !force) return false
        return try {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            confirmationLaunched = true
            true
        } catch (e: ActivityNotFoundException) {
            failConfirmation(sessionId, InstallFailure.INSTALLER_UNAVAILABLE)
            false
        } catch (e: SecurityException) {
            failConfirmation(sessionId, InstallFailure.BLOCKED)
            false
        }
    }

    private fun failConfirmation(sessionId: Int, reason: InstallFailure) {
        scope.launch {
            mutex.withLock {
                if (repository.installState.first()?.sessionId == sessionId) fail(sessionId, reason)
            }
        }
    }

    suspend fun handleStatus(intent: Intent) = mutex.withLock {
        val saved = repository.installState.first() ?: return@withLock
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (intent.action != ACTION_INSTALL_STATUS || sessionId != saved.sessionId || intent.data != statusUri(sessionId)) {
            return@withLock
        }
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirmation == null) {
                    fail(sessionId, InstallFailure.INSTALLER_UNAVAILABLE)
                } else {
                    pendingUserAction = confirmation
                    confirmationLaunched = false
                    repository.saveInstallSession(sessionId, saved.versionCode, pendingUserAction = true)
                    _installResult.value = InstallResult.UserActionRequired(sessionId)
                    UpdateInstallStatusReceiver.showConfirmation(context, sessionId, confirmation)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> finishInstalled(saved)
            else -> fail(sessionId, when (status) {
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallFailure.ABORTED
                PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallFailure.BLOCKED
                PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallFailure.CONFLICT
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                PackageInstaller.STATUS_FAILURE_INVALID -> InstallFailure.INVALID_APK
                PackageInstaller.STATUS_FAILURE_STORAGE -> InstallFailure.INSUFFICIENT_STORAGE
                else -> InstallFailure.UNKNOWN
            })
        }
    }

    suspend fun reconcile() = mutex.withLock {
        val saved = repository.installState.first() ?: return@withLock
        if (installJob?.isActive == true) return@withLock
        if (identity(installedPackage()).versionCode >= saved.versionCode) {
            finishInstalled(saved)
            return@withLock
        }
        val info = sessions.getSessionInfo(saved.sessionId)
        if (info == null || info.installerPackageName != context.packageName || info.appPackageName != context.packageName ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !info.isSealed) ||
            (saved.pendingUserAction && pendingUserAction == null)
        ) {
            fail(saved.sessionId, InstallFailure.ABORTED)
        } else if (!saved.pendingUserAction) {
            activeSessionId = saved.sessionId
            _installResult.value = InstallResult.Installing(info.progress.coerceIn(0f, 1f))
        }
    }

    private suspend fun finishInstalled(saved: PersistedInstallState) {
        val installedCode = identity(installedPackage()).versionCode
        if (installedCode < saved.versionCode) {
            fail(saved.sessionId, InstallFailure.WRONG_VERSION)
            return
        }
        val download = repository.updateState.first()
        repository.clearInstallSession()
        clearPendingAction()
        _installResult.value = InstallResult.Success
        if (download.targetVersionCode == null || download.targetVersionCode <= installedCode) {
            repository.clearDownloadState()
            if (download.downloadId != -1L) {
                try {
                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(download.downloadId)
                } catch (e: Exception) {
                    Log.w("UpdateInstaller", "Could not remove installed update download", e)
                }
            }
        }
    }

    private suspend fun fail(sessionId: Int, reason: InstallFailure) {
        abandon(sessionId)
        repository.clearInstallSession()
        clearPendingAction()
        _installResult.value = InstallResult.Error(reason)
    }

    private fun abandon(sessionId: Int) {
        try {
            sessions.abandonSession(sessionId)
        } catch (e: SecurityException) {
            // The system may already have removed a finished session.
        } catch (e: IllegalArgumentException) {
            // The system may already have removed a finished session.
        }
    }

    private fun clearPendingAction() {
        activeSessionId = -1
        pendingUserAction = null
        confirmationLaunched = false
        UpdateInstallStatusReceiver.dismissConfirmation(context)
    }

    override fun reset() {
        if (!_installResult.value.isInstalling) _installResult.value = null
    }

    @Suppress("DEPRECATION")
    private fun installedPackage(): PackageInfo = packages.getPackageInfo(context.packageName, signatureFlags())

    private fun signatureFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkVerifier.Identity {
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners else info.signatures
        val history = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.signingCertificateHistory else signers
        fun fingerprints(signatures: Array<android.content.pm.Signature>?): Set<String> = signatures.orEmpty().mapTo(mutableSetOf()) {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }
        return ApkVerifier.Identity(
            info.packageName,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong(),
            info.versionName,
            fingerprints(signers),
            fingerprints(history),
        )
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.github.shekohex.whisperpp.INSTALL_STATUS"
        private fun statusUri(sessionId: Int): Uri = Uri.parse("whisperpp-update://install/$sessionId")
    }
}
