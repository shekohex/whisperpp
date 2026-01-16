package com.github.shekohex.whisperpp.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class DownloadCompletedReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            // Nothing to do here specifically, as the app starting will trigger
            // UpdateManager.observeExistingDownload via SettingsScreen.
            return
        }

        if (action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val pendingResult = goAsync()
        val repository = UpdateRepository(context)
        val downloader = UpdateDownloader(context, repository)

        scope.launch {
            try {
                val state = repository.updateState.first()
                if (state.downloadId == downloadId && state.expectedSignature != null && state.destinationPath != null) {
                    val file = File(state.destinationPath)
                    if (file.exists()) {
                        downloader.verifyAndComplete(file, state.expectedSignature)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
