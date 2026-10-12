package com.github.shekohex.whisperpp.data.history

/** History side of the settings backup (`history` category, text only). */
interface HistoryBackupSource {
    suspend fun exportBackup(): HistoryBackup

    /** [replace] wipes the current history first; otherwise duplicates are skipped. Returns entries added. */
    suspend fun importBackup(backup: HistoryBackup, replace: Boolean): Int
}
