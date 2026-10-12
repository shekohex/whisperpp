package com.github.shekohex.whisperpp.providers.gemini

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
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
        val generationConfig = JSONObject().apply {
            when (val thinking = req.thinking) {
                is ThinkingConfig.Enabled -> put("thinkingConfig", JSONObject().apply {
                    if (thinking.budgetTokens != null) {
                        put("thinkingBudget", thinking.budgetTokens)
                    } else {
                        put("thinkingLevel", thinking.level.uppercase())
                    }
                })
                ThinkingConfig.Disabled -> put("temperature", req.temperature.toDouble())
            }
        }
        val body = JSONObject()
            .put("contents", userContents(req.composePrompt()))
            .put("generationConfig", generationConfig)
        val response = client.executeForBody(buildRequest(body, apiKey), req.timeoutMs, apiKey)
        parseText(response.body)
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
