package com.github.shekohex.whisperpp.providers.gemini

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.core.network.executeForEvents
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.EnhancementProvider
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import com.github.shekohex.whisperpp.providers.VERIFY_TIMEOUT_MS
import com.github.shekohex.whisperpp.providers.apiKeyOrThrow
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import com.github.shekohex.whisperpp.providers.providerCall
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** `POST {base}/models/{model}:generateContent` with `x-goog-api-key`. */
class GeminiChat(
    private val client: OkHttpClient,
    private val provider: ServiceProvider,
    private val model: ModelConfig,
    private val credentials: Credentials,
) : EnhancementProvider {
    override val id: String = provider.id

    override suspend fun enhance(req: EnhancementRequest): Result<String> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val body = JSONObject()
            .put("contents", userContents(req.composePrompt()))
            .put("generationConfig", generationConfig(req.thinking, req.temperature.toDouble()))
        val response = client.executeForBody(buildRequest(body, apiKey), req.timeoutMs, apiKey)
        parseText(response.body)
    }

    /**
     * `:streamGenerateContent?alt=sse`: thought parts are ignored and only `finishReason == "STOP"`
     * succeeds — a stream that ends any other way is a failure, never a partial result.
     */
    override suspend fun enhanceStreaming(
        req: EnhancementRequest,
        onPartial: suspend (String) -> Unit,
    ): Result<String> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val body = JSONObject()
            .put("contents", userContents(req.composePrompt()))
            .put("generationConfig", generationConfig(req.thinking, req.temperature.toDouble()))
        val request = Request.Builder()
            .url(streamGenerateContentUrl(provider.baseUrlOrThrow(), model.id))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .apply { if (apiKey != null) header("x-goog-api-key", apiKey) }
            .build()
        val text = StringBuilder()
        var finishReason: String? = null
        client.executeForEvents(request, req.timeoutMs, apiKey)
            .takeWhile { data ->
                val chunk = try {
                    JSONObject(data)
                } catch (e: JSONException) {
                    throw NetworkError.InvalidResponse("streamGenerateContent JSON", e)
                }
                if (chunk.has("error")) throw NetworkError.InvalidResponse("streamGenerateContent error")
                chunk.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
                    throw NetworkError.InvalidResponse("blocked prompt")
                }
                val candidate = chunk.optJSONArray("candidates")?.optJSONObject(0)
                if (candidate != null) {
                    var appended = false
                    val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            if (part.has("text") && !part.optBoolean("thought", false)) {
                                text.append(part.getString("text"))
                                appended = true
                            }
                        }
                    }
                    if (appended) onPartial(text.toString())
                    val finish = candidate.optString("finishReason")
                    if (finish.isNotEmpty()) finishReason = finish
                }
                finishReason == null
            }
            .collect()
        if (finishReason == null) throw NetworkError.InvalidResponse("stream ended without finishReason")
        if (finishReason != "STOP") throw NetworkError.InvalidResponse("finishReason $finishReason")
        text.toString().trim().ifEmpty { throw NetworkError.InvalidResponse("empty candidate") }
    }

    override suspend fun verify(): Result<Unit> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val body = JSONObject()
            .put("contents", userContents("Reply with OK"))
            .put("generationConfig", JSONObject().put("temperature", 0).put("maxOutputTokens", 16))
        client.executeForBody(buildRequest(body, apiKey), VERIFY_TIMEOUT_MS, apiKey)
    }

    private fun buildRequest(body: JSONObject, apiKey: String?): Request {
        return Request.Builder()
            .url(generateContentUrl(provider.baseUrlOrThrow(), model.id))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .apply { if (apiKey != null) header("x-goog-api-key", apiKey) }
            .build()
    }

    private fun generationConfig(thinking: ThinkingConfig, temperature: Double): JSONObject =
        JSONObject().apply {
            when (thinking) {
                is ThinkingConfig.Enabled -> put("thinkingConfig", JSONObject().apply {
                    if (thinking.budgetTokens != null) {
                        put("thinkingBudget", thinking.budgetTokens)
                    } else {
                        put("thinkingLevel", thinking.level.uppercase())
                    }
                })
                ThinkingConfig.Disabled -> put("temperature", temperature)
            }
        }

    private fun userContents(text: String): JSONArray = JSONArray().put(
        JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", text)))
    )

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun generateContentUrl(base: HttpUrl, modelId: String): HttpUrl {
            val path = base.encodedPath.trim().trimEnd('/')
            if (path.endsWith(":generateContent") && path.contains("/models/")) {
                return base
            }
            val normalizedModel = modelId.trim().removePrefix("models/").trimStart('/')
            return base.newBuilder()
                .addPathSegments("models")
                .addPathSegment("$normalizedModel:generateContent")
                .build()
        }

        fun streamGenerateContentUrl(base: HttpUrl, modelId: String): HttpUrl {
            val path = base.encodedPath.trim().trimEnd('/')
            val generate = when {
                path.endsWith(":streamGenerateContent") -> base
                path.endsWith(":generateContent") -> base
                else -> generateContentUrl(base, modelId)
            }
            return toStreamUrl(generate).newBuilder()
                .setQueryParameter("alt", "sse")
                .build()
        }

        private fun toStreamUrl(url: HttpUrl): HttpUrl {
            val normalized = url.newBuilder().encodedPath(url.encodedPath.trimEnd('/').ifEmpty { "/" }).build()
            val last = normalized.pathSegments.last()
            if (last.endsWith(":streamGenerateContent")) return normalized
            return normalized.newBuilder()
                .removePathSegment(normalized.pathSegments.lastIndex)
                .addPathSegment(last.removeSuffix(":generateContent") + ":streamGenerateContent")
                .build()
        }

        fun parseText(body: String): String {
            val text = try {
                val parts = JSONObject(body)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                buildString {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        // Thought summaries are not part of the answer.
                        if (part.has("text") && !part.optBoolean("thought", false)) append(part.getString("text"))
                    }
                }
            } catch (e: JSONException) {
                throw NetworkError.InvalidResponse("generateContent JSON", e)
            }
            return text.trim().ifEmpty { throw NetworkError.InvalidResponse("empty candidate") }
        }
    }
}
