package com.github.shekohex.whisperpp.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class DownloadCompletedReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val pendingResult = goAsync()
        val repository = UpdateRepository(context.applicationContext)

        scope.launch {
            try {
                val state = repository.updateState.first()
                val signature = state.expectedSignature
                val path = state.destinationPath
                // Ignore completions for downloads this updater did not start.
                if (state.downloadId != downloadId || signature == null || path == null) return@launch

                val file = File(path)
                if (file.exists()) {
                    UpdateDownloader(context.applicationContext, repository).verifyAndComplete(file, signature, downloadId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process completed download", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DownloadCompleted"
    }
}
