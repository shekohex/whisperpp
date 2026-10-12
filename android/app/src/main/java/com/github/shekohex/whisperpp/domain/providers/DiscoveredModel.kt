package com.github.shekohex.whisperpp.domain.providers

import com.github.shekohex.whisperpp.data.ModelCapabilities

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

/** Last successful listing for one provider. */
data class DiscoveredCache(
    val fetchedAtMillis: Long,
    val models: List<DiscoveredModel>,
) {
    fun contains(modelId: String): Boolean = models.any { it.id == modelId.trim() }
}
