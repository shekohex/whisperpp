package com.github.shekohex.whisperpp.providers.openai

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.data.ProviderType
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
import com.github.shekohex.whisperpp.providers.withPathSuffix
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * `GET {base}/models` on the same base as chat/transcriptions (OpenAI, Groq, OpenRouter, vLLM,
 * LM Studio). Accepts `{"data":[…]}`, `{"models":[…]}` and bare arrays.
 */
class OpenAiCompatibleDiscovery(
    private val client: OkHttpClient,
    private val provider: ServiceProvider,
    private val credentials: Credentials,
) : ModelDiscovery {
    override val identity: DiscoveryIdentity = DiscoveryIdentity.of(provider)

    override suspend fun listModels(): Result<List<DiscoveredModel>> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val request = Request.Builder()
            .url(modelsUrl(provider.baseUrlOrThrow()))
            .get()
            .apply { if (apiKey != null) header("Authorization", "Bearer $apiKey") }
            .build()
        val response = client.executeForBody(request, VERIFY_TIMEOUT_MS, apiKey)
        parseModels(response.body, provider.type)
    }

    internal companion object {
        /** Endpoint suffixes users paste; stripped before `models` is appended. */
        private val KNOWN_SUFFIXES = listOf(
            listOf("chat", "completions"),
            listOf("audio", "transcriptions"),
            listOf("audio", "translations"),
        )

        fun modelsUrl(base: HttpUrl): HttpUrl {
            val clean = base.newBuilder().query(null).fragment(null).build()
            val segments = clean.pathSegments.filter { it.isNotBlank() }
            val suffix = KNOWN_SUFFIXES.firstOrNull { segments.size >= it.size && segments.takeLast(it.size) == it }
                ?: return clean.withPathSuffix("models")
            val root = clean.newBuilder().encodedPath("/").build()
            return root.newBuilder()
                .apply { segments.dropLast(suffix.size).forEach { addPathSegment(it) } }
                .addPathSegment("models")
                .build()
        }

        fun parseModels(body: String, providerType: ProviderType): List<DiscoveredModel> {
            val entries = try {
                when (val root = JSONTokener(body).nextValue()) {
                    is JSONArray -> root
                    is JSONObject -> root.optJSONArray("data") ?: root.optJSONArray("models")
                    else -> null
                }
            } catch (e: JSONException) {
                throw NetworkError.InvalidResponse("models JSON", e)
            } ?: throw NetworkError.InvalidResponse("models list missing")
            return (0 until entries.length())
                .mapNotNull { i -> toModel(entries.opt(i), providerType) }
                .distinctBy { it.id }
        }

        private fun toModel(entry: Any?, providerType: ProviderType): DiscoveredModel? {
            val obj = entry as? JSONObject
            val id = (obj?.optString("id")?.takeIf { it.isNotBlank() } ?: obj?.optString("name") ?: entry as? String)
                ?.trim()
                .orEmpty()
            if (id.isEmpty()) return null
            val capabilities = ModelClassifier.classifyOpenAiCompatible(id, providerType) ?: return null
            val raw = buildMap {
                obj?.optString("owned_by")?.takeIf { it.isNotBlank() }?.let { put("owned_by", it) }
                obj?.optLong("created", 0L)?.takeIf { it > 0 }?.let { put("created", it.toString()) }
                obj?.optLong("context_length", 0L)?.takeIf { it > 0 }?.let { put("context_length", it.toString()) }
            }
            val displayName = obj?.optString("name")?.trim()?.takeIf { it.isNotEmpty() && it != id } ?: id
            return DiscoveredModel(id = id, displayName = displayName, capabilities = capabilities, raw = raw)
        }
    }
}
