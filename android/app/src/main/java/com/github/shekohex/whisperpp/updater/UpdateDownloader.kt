package com.github.shekohex.whisperpp.updater

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.database.getLongOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import java.security.MessageDigest

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class UpdateDownloader(private val context: Context, private val repository: UpdateRepository) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun download(url: String, expectedSignature: String, version: String): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        val fileName = "whisperpp-$version.apk"
        
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Whisper++ Update")
            setDescription("Downloading version $version")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setRequiresCharging(false)
            }
        }

        val downloadId = downloadManager.enqueue(request)
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        repository.saveDownloadState(downloadId, expectedSignature, version, destinationFile.absolutePath)

        monitorDownload(downloadId, expectedSignature, destinationFile).collect { emit(it) }
    }

    fun monitorDownload(downloadId: Long, expectedSignature: String, destinationFile: File): Flow<DownloadState> = flow {
        kotlinx.coroutines.coroutineScope {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)

                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            emit(DownloadState.Verifying(destinationFile.absolutePath))
                            val result = verifyAndComplete(destinationFile, expectedSignature)
                            emit(result)
                            cursor.close()
                            return@coroutineScope
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIndex)
                            val message = "Download failed: ${getFailureReason(reason)}"
                            emit(DownloadState.Failed(message))
                            repository.clearDownloadState()
                            cursor.close()
                            return@coroutineScope
                        }
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                            val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            
                            val bytesDownloaded = cursor.getLongOrNull(bytesDownloadedIndex) ?: 0L
                            val totalBytes = cursor.getLongOrNull(totalBytesIndex) ?: -1L
                            val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else -1f
                            
                            emit(DownloadState.Downloading(progress, bytesDownloaded, totalBytes))
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            emit(DownloadState.Downloading(-1f, 0, -1)) // Could add a Paused state if needed
                        }
                    }
                } else {
                    emit(DownloadState.Failed("Download not found"))
                    repository.clearDownloadState()
                    cursor?.close()
                    return@coroutineScope
                }
                cursor?.close()
                delay(1000)
            }
        }
    }

    suspend fun verifyAndComplete(file: File, expectedSignature: String): DownloadState {
        return try {
            val actualSignature = computeSha256(file)
            val expectedHash = expectedSignature.removePrefix("sha256:")

            if (actualSignature.equals(expectedHash, ignoreCase = true)) {
                DownloadState.Ready(file.absolutePath)
            } else {
                file.delete()
                repository.clearDownloadState()
                DownloadState.Failed("Signature mismatch")
            }
        } catch (e: Exception) {
            repository.clearDownloadState()
            DownloadState.Failed("Verification failed", e)
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

    private suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
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
