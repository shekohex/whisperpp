package com.github.shekohex.whisperpp.data

import com.github.shekohex.whisperpp.domain.providers.DiscoveredCache
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.domain.providers.DiscoveryIdentity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Gson leaves missing fields null; entries without a usable id are dropped. */
@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
internal fun parseDiscoveryCacheJson(json: String?, gson: Gson): Map<String, DiscoveredCache> {
    if (json.isNullOrBlank()) return emptyMap()
    val type = object : TypeToken<Map<String, DiscoveredCache>>() {}.type
    val parsed: Map<String, DiscoveredCache>? = try {
        gson.fromJson(json, type)
    } catch (e: Exception) {
        // The cache is disposable: an unreadable one is rebuilt by the next refresh.
        null
    }
    // A cache written by a build with different (minified) field names, or a reflective
    // mismatch, surfaces as ClassCastException while mapping. Treat it the same as unreadable.
    return try {
        mapDiscoveryCache(parsed.orEmpty())
    } catch (e: RuntimeException) {
        emptyMap()
    }
}

@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
private fun mapDiscoveryCache(parsed: Map<String, DiscoveredCache>): Map<String, DiscoveredCache> {
    return parsed.mapNotNull { (providerId, cache) ->
        if (providerId == null || cache == null) return@mapNotNull null
        val models = (cache.models ?: emptyList()).mapNotNull { model ->
            val id = (model?.id ?: "").trim()
            if (id.isEmpty()) return@mapNotNull null
            DiscoveredModel(
                id = id,
                displayName = model.displayName ?: id,
                capabilities = model.capabilities ?: ModelCapabilities(),
                raw = model.raw ?: emptyMap(),
            )
        }
        // Gson leaves missing or unknown fields null; such an identity is treated as absent.
        val identity = cache.identity?.let { raw ->
            val type = raw.providerType as ProviderType?
            val endpoint = raw.endpoint as String?
            if (type != null && endpoint != null) DiscoveryIdentity(type, endpoint) else null
        }
        providerId to DiscoveredCache(cache.fetchedAtMillis, models, identity)
    }.toMap()
}
