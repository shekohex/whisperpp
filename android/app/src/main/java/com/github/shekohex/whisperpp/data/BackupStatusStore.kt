package com.github.shekohex.whisperpp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime summary of the last backup/restore, shown on Home. Replaces passing the status
 * through the previous back-stack entry, which broke once Backup moved under the "More" tab.
 */
class BackupStatusStore {
    data class Status(
        val summary: String = "",
        val repairCount: Int = 0,
    )

    private val state = MutableStateFlow(Status())
    val status: StateFlow<Status> = state.asStateFlow()

    fun update(summary: String, repairCount: Int) {
        state.value = Status(summary, repairCount)
    }
}
