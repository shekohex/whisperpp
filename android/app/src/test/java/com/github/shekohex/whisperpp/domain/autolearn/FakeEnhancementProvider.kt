package com.github.shekohex.whisperpp.domain.autolearn

import com.github.shekohex.whisperpp.domain.providers.EnhancementProvider
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest

/** Returns queued [responses] in order; records every request. */
class FakeEnhancementProvider(responses: List<Result<String>> = emptyList()) : EnhancementProvider {
    private val pending = ArrayDeque(responses)
    val requests = mutableListOf<EnhancementRequest>()

    override val id: String = "fake"

    override suspend fun enhance(req: EnhancementRequest): Result<String> {
        requests += req
        return pending.removeFirstOrNull() ?: Result.failure(IllegalStateException("no response queued"))
    }

    override suspend fun verify(): Result<Unit> = Result.success(Unit)
}
