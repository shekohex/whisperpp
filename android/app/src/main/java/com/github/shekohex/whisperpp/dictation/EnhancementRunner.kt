package com.github.shekohex.whisperpp.dictation

import com.github.shekohex.whisperpp.core.network.NetworkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Retry policy for enhancements: attempts (also across runs on the same instance) start at least
 * [Config.minSpacingMs] apart, failures are retried with exponential backoff only for rate limits
 * and 5xx (honouring `Retry-After`), and the whole run (attempts + waits) stays within the total
 * budget. Each attempt is cancellable (G-01).
 */
class EnhancementRunner(
    private val config: Config = Config(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    data class Config(
        val totalBudgetMs: Long = 10_000L,
        val minSpacingMs: Long = 1_000L,
        val initialBackoffMs: Long = 1_000L,
        val maxAttempts: Int = 3,
        val transientFailureClassifier: TransientFailureClassifier = DefaultTransientFailureClassifier,
    ) {
        init {
            require(totalBudgetMs > 0)
            require(minSpacingMs >= 0)
            require(initialBackoffMs >= 0)
            require(maxAttempts >= 1)
        }
    }

    // Start of the most recent attempt of any run, for the spacing between consecutive requests.
    @Volatile
    private var lastAttemptAt: Long? = null

    fun interface TransientFailureClassifier {
        fun isTransient(t: Throwable): Boolean
    }

    /**
     * Runs [enhance] (given the time left for that attempt) until it succeeds, fails permanently or
     * the budget ([budgetMs], default [Config.totalBudgetMs]) is spent.
     */
    suspend fun run(
        rawText: String,
        budgetMs: Long = config.totalBudgetMs,
        enhance: suspend (attemptTimeoutMs: Long) -> String,
    ): EnhancementOutcome {
        val skipReason = skipReason(rawText)
        if (skipReason != null) {
            return EnhancementOutcome.Skipped(skipReason)
        }

        val deadline = clock() + budgetMs.coerceAtLeast(1)
        var attempts = 0
        while (true) {
            awaitSpacing(deadline)
            val remaining = deadline - clock()
            if (remaining <= 0) return EnhancementOutcome.Failed(FailureReason.Timeout, attempts)
            attempts += 1
            lastAttemptAt = clock()
            val failure = when (val result = attempt(enhance, remaining)) {
                is AttemptResult.Success -> return EnhancementOutcome.Succeeded(result.text, attempts)
                is AttemptResult.Failure -> result
            }
            val reason = failure.reason
            if (reason !is FailureReason.Transient || attempts >= config.maxAttempts) {
                return EnhancementOutcome.Failed(reason, attempts)
            }
            val wait = backoffMs(attempts, failure.retryAfterMs)
            if (clock() + wait >= deadline) return EnhancementOutcome.Failed(reason, attempts)
            delay(wait)
        }
    }

    /** Waits until [Config.minSpacingMs] passed since the previous attempt (never past [deadline]). */
    private suspend fun awaitSpacing(deadline: Long) {
        val last = lastAttemptAt ?: return
        val wait = minOf(last + config.minSpacingMs, deadline) - clock()
        if (wait > 0) delay(wait)
    }

    private sealed interface AttemptResult {
        data class Success(val text: String) : AttemptResult
        data class Failure(val reason: FailureReason, val retryAfterMs: Long? = null) : AttemptResult
    }

    private fun backoffMs(attempts: Int, retryAfterMs: Long?): Long {
        val exponential = config.initialBackoffMs shl (attempts - 1).coerceAtMost(MAX_BACKOFF_SHIFT)
        return maxOf(config.minSpacingMs, exponential, retryAfterMs ?: 0L)
    }

    private suspend fun attempt(enhance: suspend (Long) -> String, timeoutMs: Long): AttemptResult {
        return try {
            AttemptResult.Success(withTimeout(timeoutMs) { enhance(timeoutMs) })
        } catch (_: TimeoutCancellationException) {
            AttemptResult.Failure(FailureReason.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkError.Timeout) {
            AttemptResult.Failure(FailureReason.Timeout)
        } catch (t: Throwable) {
            val retryAfter = (t as? NetworkError.RateLimited)?.retryAfterMs
            if (config.transientFailureClassifier.isTransient(t)) {
                AttemptResult.Failure(FailureReason.Transient(t), retryAfter)
            } else {
                AttemptResult.Failure(FailureReason.NonTransient(t))
            }
        }
    }

    private fun skipReason(rawText: String): SkipReason? {
        if (rawText.isBlank()) return SkipReason.Empty
        val allPunctuationOrWhitespace = rawText.all { it.isWhitespace() || it.isUnicodePunctuation() }
        if (allPunctuationOrWhitespace) return SkipReason.PunctuationOnly
        return null
    }

    private fun Char.isUnicodePunctuation(): Boolean {
        return when (Character.getType(this.code)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            -> true
            else -> false
        }
    }

    private companion object {
        const val MAX_BACKOFF_SHIFT = 10

        /** Rate limits and server errors are worth another try; auth, 4xx and bad input are not. */
        val DefaultTransientFailureClassifier = TransientFailureClassifier { t ->
            when (t) {
                is NetworkError.RateLimited -> true
                is NetworkError.Http -> t.status >= 500
                else -> false
            }
        }
    }
}

sealed interface EnhancementOutcome {
    data class Succeeded(val enhancedText: String, val attempts: Int) : EnhancementOutcome
    data class Skipped(val reason: SkipReason) : EnhancementOutcome
    data class Failed(val reason: FailureReason, val attempts: Int) : EnhancementOutcome
}

enum class SkipReason {
    Empty,
    PunctuationOnly,
}

sealed interface FailureReason {
    object Timeout : FailureReason
    data class Transient(val error: Throwable) : FailureReason
    data class NonTransient(val error: Throwable) : FailureReason
}
