package com.github.shekohex.whisperpp.updater

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.database.getLongOrNull
import com.github.shekohex.whisperpp.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class UpdateDownloader(private val context: Context, private val repository: UpdateRepository) {
    private companion object {
        val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun download(url: String, expectedSignature: String, version: String): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        if (!url.startsWith("https://")) {
            emit(DownloadState.Failed("Download failed: insecure or invalid download URL", reason = DownloadFailure.INSECURE_URL))
            return@flow
        }

        val fileName = "whisperpp-${version.replace(UNSAFE_FILENAME_CHARS, "_")}.apk"
        val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (destinationDir == null) {
            emit(DownloadState.Failed("Download failed: storage is not available", reason = DownloadFailure.STORAGE_UNAVAILABLE))
            return@flow
        }
        val destinationFile = File(destinationDir, fileName)
        // DownloadManager refuses to overwrite, so drop a stale copy from an earlier attempt.
        if (destinationFile.exists()) destinationFile.delete()

        val downloadId = try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(context.getString(R.string.update_download_notification_title))
                setDescription(context.getString(R.string.update_download_notification_description, version))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setRequiresCharging(false)
                }
            }
            downloadManager.enqueue(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DownloadState.Failed("Download failed: ${e.message ?: "could not start"}", e, DownloadFailure.START_FAILED))
            return@flow
        }

        repository.saveDownloadState(downloadId, expectedSignature, version, destinationFile.absolutePath)

        monitorDownload(downloadId, expectedSignature, destinationFile).collect { emit(it) }
    }

    fun monitorDownload(downloadId: Long, expectedSignature: String, destinationFile: File): Flow<DownloadState> = flow {
        while (currentCoroutineContext().isActive) {
            val snapshot = querySnapshot(downloadId)
            if (snapshot == null) {
                emit(DownloadState.Failed("Download not found", reason = DownloadFailure.NOT_FOUND))
                repository.clearDownloadState()
                return@flow
            }

            when (snapshot.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    emit(DownloadState.Verifying(destinationFile.absolutePath))
                    emit(verifyAndComplete(destinationFile, expectedSignature))
                    return@flow
                }
                DownloadManager.STATUS_FAILED -> {
                    emit(
                        DownloadState.Failed(
                            "Download failed: ${getFailureReason(snapshot.reason)}",
                            reason = failureKind(snapshot.reason),
                        ),
                    )
                    repository.clearDownloadState()
                    return@flow
                }
                DownloadManager.STATUS_PAUSED -> emit(DownloadState.Downloading(-1f, 0, -1))
                else -> {
                    val progress = if (snapshot.totalBytes > 0) {
                        snapshot.bytesDownloaded.toFloat() / snapshot.totalBytes
                    } else {
                        -1f
                    }
                    emit(DownloadState.Downloading(progress, snapshot.bytesDownloaded, snapshot.totalBytes))
                }
            }
            delay(1000)
        }
    }

    private class Snapshot(
        val status: Int,
        val reason: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    )

    private fun querySnapshot(downloadId: Long): Snapshot? {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            Snapshot(
                status = it.intOrDefault(DownloadManager.COLUMN_STATUS, DownloadManager.STATUS_FAILED),
                reason = it.intOrDefault(DownloadManager.COLUMN_REASON, 0),
                bytesDownloaded = it.longOrDefault(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR, 0L),
                totalBytes = it.longOrDefault(DownloadManager.COLUMN_TOTAL_SIZE_BYTES, -1L)
            )
        }
    }

    private fun Cursor.intOrDefault(column: String, default: Int): Int {
        val index = getColumnIndex(column)
        return if (index >= 0) getInt(index) else default
    }

    private fun Cursor.longOrDefault(column: String, default: Long): Long {
        val index = getColumnIndex(column)
        return if (index >= 0) getLongOrNull(index) ?: default else default
    }

    suspend fun verifyAndComplete(file: File, expectedSignature: String): DownloadState {
        if (!file.exists()) {
            repository.clearDownloadState()
            return DownloadState.Failed("Downloaded file is missing", reason = DownloadFailure.FILE_MISSING)
        }
        return when (val result = ApkVerifier.verify(file, expectedSignature)) {
            ApkVerifier.Result.Match -> DownloadState.Ready(file.absolutePath)
            is ApkVerifier.Result.Mismatch -> {
                file.delete()
                repository.clearDownloadState()
                DownloadState.Failed("Checksum mismatch: the downloaded file was discarded", reason = DownloadFailure.CHECKSUM_MISMATCH)
            }
            ApkVerifier.Result.InvalidExpected -> {
                file.delete()
                repository.clearDownloadState()
                DownloadState.Failed("Update metadata has an invalid checksum", reason = DownloadFailure.INVALID_CHECKSUM)
            }
            is ApkVerifier.Result.Unreadable -> {
                repository.clearDownloadState()
                DownloadState.Failed(
                    "Verification failed: ${result.cause.message ?: "cannot read file"}",
                    result.cause,
                    DownloadFailure.VERIFICATION_FAILED,
                )
            }
        }
    }

    private fun getFailureReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
        DownloadManager.ERROR_FILE_ERROR -> "File error"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
        DownloadManager.ERROR_UNKNOWN -> "Unknown error"
        else -> "Error code: $reason"
    }

    private fun failureKind(reason: Int): DownloadFailure = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> DownloadFailure.INSUFFICIENT_SPACE
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> DownloadFailure.STORAGE_UNAVAILABLE
        DownloadManager.ERROR_FILE_ERROR,
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> DownloadFailure.FILE_ERROR
        DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_CANNOT_RESUME,
        DownloadManager.ERROR_TOO_MANY_REDIRECTS,
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> DownloadFailure.NETWORK
        else -> DownloadFailure.UNKNOWN
    }

    suspend fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        repository.clearDownloadState()
    }

    suspend fun cleanup() {
        repository.clearDownloadState()
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles { file -> file.name.startsWith("whisperpp-") && file.name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }
}
