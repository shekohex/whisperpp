package com.github.shekohex.whisperpp.updater

import kotlin.coroutines.cancellation.CancellationException

/** WorkManager-free core of [UpdateCheckWorker.doWork], so the result mapping is unit-testable. */
class UpdateCheckRunner(
    private val source: UpdateCheckSource,
    private val persistError: suspend (String?) -> Unit,
    private val notifyAvailable: (String) -> Unit
) {
    enum class Outcome { SUCCESS, RETRY }

    suspend fun run(channel: UpdateChannel): Outcome {
        val result = try {
            source.checkForUpdate(channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckResult.Error(UpdateChecker.classifyException(e))
        }

        return when (result) {
            is UpdateCheckResult.Available -> {
                persistError(null)
                notifyAvailable(result.release.version)
                Outcome.SUCCESS
            }
            is UpdateCheckResult.UpToDate -> {
                persistError(null)
                Outcome.SUCCESS
            }
            is UpdateCheckResult.Error -> if (result.isTransient) {
                Outcome.RETRY
            } else {
                persistError(result.message)
                Outcome.SUCCESS
            }
        }
    }
}
