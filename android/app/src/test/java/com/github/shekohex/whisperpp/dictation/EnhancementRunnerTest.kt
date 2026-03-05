package com.github.shekohex.whisperpp.dictation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class EnhancementRunnerTest {

    @Test
    fun punctuationOnly_skip_doesNotCallEnhancer() = runBlocking {
        val runner = EnhancementRunner(
            EnhancementRunner.Config(
                balancedTimeoutMs = 50,
                quickRetryTimeoutMs = 25,
            )
        )

        var called = 0
        val outcome = runner.run("...!!!\n\t") {
            called += 1
            "should not run"
        }

        assertTrue(outcome is EnhancementOutcome.Skipped)
        assertEquals(SkipReason.PunctuationOnly, (outcome as EnhancementOutcome.Skipped).reason)
        assertEquals(0, called)
    }

    @Test
    fun blank_skip_doesNotCallEnhancer() = runBlocking {
        val runner = EnhancementRunner()

        var called = 0
        val outcome = runner.run("  \n\t") {
            called += 1
            "should not run"
        }

        assertTrue(outcome is EnhancementOutcome.Skipped)
        assertEquals(SkipReason.Empty, (outcome as EnhancementOutcome.Skipped).reason)
        assertEquals(0, called)
    }

    @Test
    fun timeout_returnsFailed_afterAtMostTwoAttempts() = runBlocking {
        val runner = EnhancementRunner(
            EnhancementRunner.Config(
                balancedTimeoutMs = 30,
                quickRetryTimeoutMs = 20,
            )
        )

        var called = 0
        val outcome = runner.run("hello") {
            called += 1
            delay(200)
            "never"
        }

        assertTrue(outcome is EnhancementOutcome.Failed)
        val failed = outcome as EnhancementOutcome.Failed
        assertEquals(2, failed.attempts)
        assertEquals(2, called)
        assertEquals(FailureReason.Timeout, failed.reason)
    }

    @Test
    fun transientError_retriesExactlyOnce_thenFails() = runBlocking {
        val runner = EnhancementRunner(
            EnhancementRunner.Config(
                balancedTimeoutMs = 50,
                quickRetryTimeoutMs = 50,
            )
        )

        var called = 0
        val outcome = runner.run("hello") {
            called += 1
            throw IOException("HTTP 429")
        }

        assertTrue(outcome is EnhancementOutcome.Failed)
        val failed = outcome as EnhancementOutcome.Failed
        assertEquals(2, called)
        assertEquals(2, failed.attempts)
        assertTrue(failed.reason is FailureReason.Transient)
    }

    @Test
    fun nonTransientError_doesNotRetry() = runBlocking {
        val runner = EnhancementRunner(
            EnhancementRunner.Config(
                balancedTimeoutMs = 50,
                quickRetryTimeoutMs = 50,
            )
        )

        var called = 0
        val outcome = runner.run("hello") {
            called += 1
            throw IllegalArgumentException("bad request")
        }

        assertTrue(outcome is EnhancementOutcome.Failed)
        val failed = outcome as EnhancementOutcome.Failed
        assertEquals(1, called)
        assertEquals(1, failed.attempts)
        assertTrue(failed.reason is FailureReason.NonTransient)
    }

    @Test
    fun cancellation_doesNotThrow() = runBlocking {
        val runner = EnhancementRunner(
            EnhancementRunner.Config(
                balancedTimeoutMs = 50,
                quickRetryTimeoutMs = 50,
            )
        )

        var called = 0
        val outcome = runner.run("hello") {
            called += 1
            throw CancellationException("cancel")
        }

        assertTrue(outcome is EnhancementOutcome.Failed)
        val failed = outcome as EnhancementOutcome.Failed
        assertEquals(1, called)
        assertEquals(1, failed.attempts)
        assertEquals(FailureReason.Cancelled, failed.reason)
    }
}
