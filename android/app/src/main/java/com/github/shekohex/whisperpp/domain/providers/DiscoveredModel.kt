package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One entry of a provider's model listing (§4.5.2). [capabilities] come from
 * [com.github.shekohex.whisperpp.providers.ModelClassifier]; all flags false means "unclassified".
 * [raw] keeps a few listing fields (owner, token limits, methods) for display only.
 */
data class DiscoveredModel(
    val id: String,
    val displayName: String,
    val capabilities: ModelCapabilities,
    val raw: Map<String, String> = emptyMap(),
) {
    val isClassified: Boolean
        get() = capabilities.stt || capabilities.text || capabilities.realtime
}

/**
 * Last successful listing for one provider. [identity] is what was listed; a cache whose identity
 * differs from the provider's current one (endpoint or type edited) is stale and ignored.
 */
data class DiscoveredCache(
    val fetchedAtMillis: Long,
    val models: List<DiscoveredModel>,
    val identity: DiscoveryIdentity? = null,
) {
    fun contains(modelId: String): Boolean = models.any { it.id == modelId.trim() }
}

/** Provider type + normalized endpoint a listing came from. */
data class DiscoveryIdentity(val providerType: ProviderType, val endpoint: String) {
    companion object {
        fun of(provider: ServiceProvider): DiscoveryIdentity =
            DiscoveryIdentity(provider.type, normalizeEndpoint(provider.endpoint))

        /** Scheme/host case and trailing slashes do not make a different endpoint. */
        fun normalizeEndpoint(endpoint: String): String {
            val trimmed = endpoint.trim().trimEnd('/')
            val url = trimmed.toHttpUrlOrNull() ?: return trimmed.lowercase()
            return url.toString().trimEnd('/')
        }
    }
}
