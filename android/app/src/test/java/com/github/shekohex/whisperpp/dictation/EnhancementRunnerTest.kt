package com.github.shekohex.whisperpp.dictation

import com.github.shekohex.whisperpp.core.network.NetworkError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnhancementRunnerTest {

    private fun TestScope.runner(config: EnhancementRunner.Config = EnhancementRunner.Config()) =
        EnhancementRunner(config, clock = { testScheduler.currentTime })

    @Test
    fun punctuationOnly_skip_doesNotCallEnhancer() = runTest {
        var called = 0
        val outcome = runner().run("...!!!\n\t") {
            called += 1
            "should not run"
        }

        assertEquals(EnhancementOutcome.Skipped(SkipReason.PunctuationOnly), outcome)
        assertEquals(0, called)
    }

    @Test
    fun blank_skip_doesNotCallEnhancer() = runTest {
        var called = 0
        val outcome = runner().run("  \n\t") {
            called += 1
            "should not run"
        }

        assertEquals(EnhancementOutcome.Skipped(SkipReason.Empty), outcome)
        assertEquals(0, called)
    }

    @Test
    fun hangingAttempt_isCancelledWhenTheBudgetRunsOut() = runTest {
        var cancelled = false
        val outcome = runner().run("hello", budgetMs = 5_000) {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }

        assertEquals(EnhancementOutcome.Failed(FailureReason.Timeout, 1), outcome)
        assertTrue(cancelled)
        assertEquals(5_000, testScheduler.currentTime)
    }

    @Test
    fun rateLimited_retriesWithExponentialBackoffAndMinimumSpacing() = runTest {
        val startedAt = mutableListOf<Long>()
        val outcome = runner().run("hello", budgetMs = 30_000) {
            startedAt += testScheduler.currentTime
            if (startedAt.size < 3) throw NetworkError.RateLimited("api.example.com", retryAfterMs = null)
            "fixed"
        }

        assertEquals(EnhancementOutcome.Succeeded("fixed", 3), outcome)
        assertEquals(listOf(0L, 1_000L, 3_000L), startedAt)
    }

    @Test
    fun rateLimited_honoursRetryAfter() = runTest {
        val startedAt = mutableListOf<Long>()
        val outcome = runner().run("hello", budgetMs = 30_000) {
            startedAt += testScheduler.currentTime
            if (startedAt.size == 1) throw NetworkError.RateLimited("api.example.com", retryAfterMs = 4_000)
            "fixed"
        }

        assertEquals(EnhancementOutcome.Succeeded("fixed", 2), outcome)
        assertEquals(listOf(0L, 4_000L), startedAt)
    }

    @Test
    fun serverError_isRetried_untilMaxAttempts() = runTest {
        var called = 0
        val outcome = runner().run("hello", budgetMs = 30_000) {
            called += 1
            throw NetworkError.Http(503, "api.example.com", null)
        }

        assertTrue(outcome is EnhancementOutcome.Failed)
        outcome as EnhancementOutcome.Failed
        assertEquals(3, called)
        assertEquals(3, outcome.attempts)
        assertTrue(outcome.reason is FailureReason.Transient)
    }

    @Test
    fun backoffBeyondBudget_stopsWithoutWaiting() = runTest {
        var called = 0
        val outcome = runner().run("hello", budgetMs = 1_500) {
            called += 1
            throw NetworkError.RateLimited("api.example.com", retryAfterMs = 10_000)
        }

        assertTrue(outcome is EnhancementOutcome.Failed)
        assertEquals(1, called)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun clientErrors_areNotRetried() = runTest {
        listOf(
            NetworkError.Http(400, "api.example.com", "bad request"),
            NetworkError.Unauthorized(401, "api.example.com"),
            IllegalArgumentException("bad input"),
        ).forEach { error ->
            var called = 0
            val outcome = runner().run("hello") {
                called += 1
                throw error
            }

            assertTrue(outcome is EnhancementOutcome.Failed)
            outcome as EnhancementOutcome.Failed
            assertEquals(1, outcome.attempts)
            // Coroutines may recover the stack trace into a copy of the exception; compare by type.
            assertEquals(error::class, (outcome.reason as FailureReason.NonTransient).error::class)
            assertEquals(1, called)
        }
    }

    @Test
    fun attemptTimeout_isTheRemainingBudget() = runTest {
        val timeouts = mutableListOf<Long>()
        runner().run("hello", budgetMs = 10_000) { timeoutMs ->
            timeouts += timeoutMs
            if (timeouts.size == 1) throw NetworkError.Http(500, "api.example.com", null)
            "ok"
        }

        assertEquals(listOf(10_000L, 9_000L), timeouts)
    }

    @Test
    fun cancellingTheCaller_cancelsTheAttemptAndPropagates() = runTest {
        val started = CompletableDeferred<Unit>()
        var attemptCancelled = false
        val run = async {
            runner().run("hello") {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    attemptCancelled = true
                }
            }
        }
        started.await()
        run.cancel()
        runCurrent()

        assertTrue(run.isCancelled)
        assertTrue(attemptCancelled)
    }

    @Test
    fun serverErrors_backOffExponentially() = runTest {
        val startedAt = mutableListOf<Long>()
        val outcome = runner(EnhancementRunner.Config(maxAttempts = 4)).run("hello", budgetMs = 30_000) {
            startedAt += testScheduler.currentTime
            if (startedAt.size < 4) throw NetworkError.Http(502, "api.example.com", null)
            "fixed"
        }

        assertEquals(EnhancementOutcome.Succeeded("fixed", 4), outcome)
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), startedAt)
    }

    @Test
    fun consecutiveRuns_areSpacedByAtLeastOneSecond() = runTest {
        val runner = runner()
        val startedAt = mutableListOf<Long>()
        runner.run("first") { startedAt += testScheduler.currentTime; "1" }
        runner.run("second") { startedAt += testScheduler.currentTime; "2" }
        testScheduler.advanceTimeBy(5_000)
        runner.run("third") { startedAt += testScheduler.currentTime; "3" }

        assertEquals(listOf(0L, 1_000L, 6_000L), startedAt)
    }

    @Test
    fun spacingWait_neverExceedsTheBudget() = runTest {
        val runner = runner()
        runner.run("first") { "1" }
        var called = false
        val outcome = runner.run("second", budgetMs = 500) { called = true; "2" }

        assertEquals(EnhancementOutcome.Failed(FailureReason.Timeout, 0), outcome)
        assertEquals(false, called)
        assertEquals(500, testScheduler.currentTime)
    }
}
