package com.github.shekohex.whisperpp.domain.providers

/**
 * Text enhancement through one configured provider + model (§4.5). One request per call; retries
 * and backoff belong to the caller ([com.github.shekohex.whisperpp.dictation.EnhancementRunner]).
 */
interface EnhancementProvider {
    val id: String

    suspend fun enhance(req: EnhancementRequest): Result<String>

    /** Full snapshots let retries replace provisional editor text without duplicating it. */
    suspend fun enhanceStreaming(
        req: EnhancementRequest,
        onPartial: suspend (String) -> Unit,
    ): Result<String> = enhance(req)

    /** Minimal round trip ("Reply with OK") proving reachability, auth and the model id. */
    suspend fun verify(): Result<Unit>
}
