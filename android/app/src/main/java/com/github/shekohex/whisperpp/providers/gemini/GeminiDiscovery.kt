package com.github.shekohex.whisperpp.providers.gemini

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.DiscoveredModel
import com.github.shekohex.whisperpp.domain.providers.DiscoveryIdentity
import com.github.shekohex.whisperpp.domain.providers.ModelDiscovery
import com.github.shekohex.whisperpp.providers.ModelClassifier
import com.github.shekohex.whisperpp.providers.VERIFY_TIMEOUT_MS
import com.github.shekohex.whisperpp.providers.apiKeyOrThrow
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import com.github.shekohex.whisperpp.providers.providerCall
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/**
 * `GET {base}/models?pageSize=200` (base `https://generativelanguage.googleapis.com/v1beta`),
 * following `nextPageToken`. The key goes in `x-goog-api-key` like [GeminiChat], never the URL.
 */
class GeminiDiscovery(
    private val client: OkHttpClient,
    private val provider: ServiceProvider,
    private val credentials: Credentials,
) : ModelDiscovery {
    override val identity: DiscoveryIdentity = DiscoveryIdentity.of(provider)

    override suspend fun listModels(): Result<List<DiscoveredModel>> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val base = provider.baseUrlOrThrow()
        val models = mutableListOf<DiscoveredModel>()
        var pageToken: String? = null
        var pages = 0
        do {
            val request = Request.Builder()
                .url(modelsUrl(base, pageToken))
                .get()
                .apply { if (apiKey != null) header("x-goog-api-key", apiKey) }
                .build()
            val page = parsePage(client.executeForBody(request, VERIFY_TIMEOUT_MS, apiKey).body)
            models += page.models
            pageToken = page.nextPageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        models.distinctBy { it.id }
    }

    internal data class Page(val models: List<DiscoveredModel>, val nextPageToken: String?)

    internal companion object {
        private const val PAGE_SIZE = 200
        private const val MAX_PAGES = 10

        fun modelsUrl(base: HttpUrl, pageToken: String?): HttpUrl {
            val segments = base.pathSegments.filter { it.isNotBlank() }
            // Users may paste a `…/models/{id}:generateContent` URL; list from its parent.
            val modelsIndex = segments.indexOf("models")
            val prefix = if (modelsIndex >= 0) segments.take(modelsIndex) else segments
            return base.newBuilder()
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .apply { prefix.forEach { addPathSegment(it) } }
                .addPathSegment("models")
                .addQueryParameter("pageSize", PAGE_SIZE.toString())
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build()
        }

        fun parsePage(body: String): Page {
            val root = try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw NetworkError.InvalidResponse("models JSON", e)
            }
            val entries = root.optJSONArray("models")
            val models = if (entries == null) {
                emptyList()
            } else {
                (0 until entries.length()).mapNotNull { i -> entries.optJSONObject(i)?.let(::toModel) }
            }
            val next = root.optString("nextPageToken").trim().takeIf { it.isNotEmpty() }
            return Page(models, next)
        }

        private fun toModel(obj: JSONObject): DiscoveredModel? {
            val id = obj.optString("name").trim().removePrefix("models/").trim()
            if (id.isEmpty()) return null
            val methodsJson = obj.optJSONArray("supportedGenerationMethods")
            val methods = if (methodsJson == null) {
                emptyList()
            } else {
                (0 until methodsJson.length()).map { methodsJson.optString(it) }
            }
            val thinking = if (obj.has("thinking")) obj.optBoolean("thinking") else null
            val capabilities = ModelClassifier.classifyGemini(id, methods, thinking) ?: return null
            val raw = buildMap {
                put("methods", methods.joinToString(","))
                obj.optString("version").takeIf { it.isNotBlank() }?.let { put("version", it) }
                obj.optLong("inputTokenLimit", 0L).takeIf { it > 0 }?.let { put("inputTokenLimit", it.toString()) }
                obj.optLong("outputTokenLimit", 0L).takeIf { it > 0 }?.let { put("outputTokenLimit", it.toString()) }
            }
            val displayName = obj.optString("displayName").trim().ifEmpty { id }
            return DiscoveredModel(id = id, displayName = displayName, capabilities = capabilities, raw = raw)
        }
    }
}
