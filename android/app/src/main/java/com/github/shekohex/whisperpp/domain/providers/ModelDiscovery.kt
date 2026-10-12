package com.github.shekohex.whisperpp.domain.providers

/**
 * Lists the models a provider serves (§4.5.2). Failures are
 * [com.github.shekohex.whisperpp.core.network.NetworkError] or [ProviderConfigException]; a
 * successful listing also proves auth + reachability ("Test connection").
 */
interface ModelDiscovery {
    suspend fun listModels(): Result<List<DiscoveredModel>>

    /** Provider type + endpoint this discovery lists; stored with the cache to detect stale listings. */
    val identity: DiscoveryIdentity?
        get() = null
}

/** The provider has no listing endpoint; the editor keeps manual model entry. */
sealed class DiscoveryUnsupported(message: String) : Exception(message) {
    /** Whisper ASR webservice and other non-OpenAI-style servers. */
    data object NoListingEndpoint : DiscoveryUnsupported("No model listing endpoint")
}
