package com.github.shekohex.whisperpp.dictation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class EnhancementRunner(
    private val config: Config = Config(),
) {
    data class Config(
        val balancedTimeoutMs: Long = 2_500L,
        val quickRetryTimeoutMs: Long = 1_200L,
        val transientFailureClassifier: TransientFailureClassifier = DefaultTransientFailureClassifier,
    ) {
        init {
            require(balancedTimeoutMs > 0)
            require(quickRetryTimeoutMs > 0)
        }
    }

    fun interface TransientFailureClassifier {
        fun isTransient(t: Throwable): Boolean
    }

    suspend fun run(
        rawText: String,
        enhance: suspend (String) -> String,
    ): EnhancementOutcome {
        val skipReason = skipReason(rawText)
        if (skipReason != null) {
            return EnhancementOutcome.Skipped(skipReason)
        }

        var attempts = 0
        val firstAttempt = attempt(rawText, enhance, config.balancedTimeoutMs)
        attempts += 1

        when (firstAttempt) {
            is AttemptResult.Success -> {
                return EnhancementOutcome.Succeeded(firstAttempt.text, attempts)
            }
            is AttemptResult.Failure -> {
                if (firstAttempt.reason == FailureReason.Cancelled) {
                    return EnhancementOutcome.Failed(FailureReason.Cancelled, attempts)
                }
                if (firstAttempt.reason is FailureReason.NonTransient) {
                    return EnhancementOutcome.Failed(firstAttempt.reason, attempts)
                }
            }
        }

        val secondAttempt = attempt(rawText, enhance, config.quickRetryTimeoutMs)
        attempts += 1

        return when (secondAttempt) {
            is AttemptResult.Success -> EnhancementOutcome.Succeeded(secondAttempt.text, attempts)
            is AttemptResult.Failure -> EnhancementOutcome.Failed(secondAttempt.reason, attempts)
        }
    }

    private sealed interface AttemptResult {
        data class Success(val text: String) : AttemptResult
        data class Failure(val reason: FailureReason) : AttemptResult
    }

    private suspend fun attempt(
        rawText: String,
        enhance: suspend (String) -> String,
        timeoutMs: Long,
    ): AttemptResult {
        return try {
            AttemptResult.Success(
                withTimeout(timeoutMs) {
                    enhance(rawText)
                }
            )
        } catch (_: TimeoutCancellationException) {
            AttemptResult.Failure(FailureReason.Timeout)
        } catch (_: CancellationException) {
            AttemptResult.Failure(FailureReason.Cancelled)
        } catch (t: Throwable) {
            val message = safeErrorMessage(t)
            if (config.transientFailureClassifier.isTransient(t)) {
                AttemptResult.Failure(FailureReason.Transient(message))
            } else {
                AttemptResult.Failure(FailureReason.NonTransient(message))
            }
        }
    }

    private fun safeErrorMessage(t: Throwable): String {
        val name = t::class.java.simpleName.ifBlank { "Throwable" }
        val message = t.message?.trim().orEmpty()
        return if (message.isBlank()) name else "$name: $message"
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
        val DefaultTransientFailureClassifier = TransientFailureClassifier { t ->
            if (t is TimeoutCancellationException) return@TransientFailureClassifier true
            val message = t.message?.lowercase().orEmpty()
            if ("429" in message) return@TransientFailureClassifier true
            if ("rate" in message && "limit" in message) return@TransientFailureClassifier true
            t is java.io.IOException
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
    object Cancelled : FailureReason
    data class Transient(val message: String) : FailureReason
    data class NonTransient(val message: String) : FailureReason
}
