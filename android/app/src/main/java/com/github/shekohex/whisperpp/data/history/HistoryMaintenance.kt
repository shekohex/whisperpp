package com.github.shekohex.whisperpp.data.history

import com.github.shekohex.whisperpp.data.HistorySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Housekeeping of the history (§4.8): rescues what a dead process left behind, then applies the
 * retention window. [runAtStartup] runs once per process; [runIfDue] is the cheap daily check the
 * keyboard service makes when it starts.
 */
class HistoryMaintenance(
    private val repository: TranscriptHistoryRepository,
    private val settings: Flow<HistorySettings>,
    private val lastRunAt: suspend () -> Long,
    private val markRun: suspend (Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Start of this process: takes older than this and still mid-pipeline belong to a dead one. */
    private val processStartedAt: Long = clock(),
) {
    suspend fun runAtStartup() {
        val current = settings.first()
        repository.reconcileInterrupted(processStartedAt)
        if (current.enabled) repository.recoverOrphans()
        prune(current)
    }

    /** Prunes at most once per day; returns whether it ran. */
    suspend fun runIfDue(): Boolean {
        if (clock() - lastRunAt() < INTERVAL_MS) return false
        prune(settings.first())
        return true
    }

    private suspend fun prune(current: HistorySettings) {
        repository.pruneExpired(current.retentionDays)
        markRun(clock())
    }

    companion object {
        val INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(1)
    }
}
