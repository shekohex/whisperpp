package com.github.shekohex.whisperpp.providers.openai

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.EnhancementProvider
import com.github.shekohex.whisperpp.domain.providers.EnhancementRequest
import com.github.shekohex.whisperpp.domain.providers.ThinkingConfig
import com.github.shekohex.whisperpp.domain.providers.isOpenAiReasoningModel
import com.github.shekohex.whisperpp.providers.VERIFY_TIMEOUT_MS
import com.github.shekohex.whisperpp.providers.apiKeyOrThrow
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import com.github.shekohex.whisperpp.providers.providerCall
import com.github.shekohex.whisperpp.providers.withPathSuffix
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * `POST {base}/chat/completions` (OpenAI, Groq, OpenRouter, vLLM, custom). Reasoning models get no
 * `temperature` and `max_completion_tokens` instead of `max_tokens` ([isOpenAiReasoningModel]).
 */
class OpenAiCompatibleChat(
    private val client: OkHttpClient,
    private val provider: ServiceProvider,
    private val model: ModelConfig,
    private val credentials: Credentials,
) : EnhancementProvider {
    override val id: String = provider.id
    private val reasoningModel = isOpenAiReasoningModel(model.id)

    override suspend fun enhance(req: EnhancementRequest): Result<String> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val body = JSONObject().apply {
            put("model", model.id)
            put("messages", userMessage(req.composePrompt()))
            when (val thinking = req.thinking) {
                is ThinkingConfig.Enabled -> put("reasoning_effort", thinking.level.lowercase())
                ThinkingConfig.Disabled -> if (!reasoningModel) put("temperature", req.temperature.toDouble())
            }
        }
        val response = client.executeForBody(buildRequest(body, apiKey), req.timeoutMs, apiKey)
        parseContent(response.body)
    }

    override suspend fun verify(): Result<Unit> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val body = JSONObject().apply {
            put("model", model.id)
            put("messages", userMessage("Reply with OK"))
            if (reasoningModel) {
                put("max_completion_tokens", VERIFY_MAX_TOKENS)
            } else {
                put("temperature", 0)
                put("max_tokens", VERIFY_MAX_TOKENS)
            }
        }
        client.executeForBody(buildRequest(body, apiKey), VERIFY_TIMEOUT_MS, apiKey)
    }

    private fun buildRequest(body: JSONObject, apiKey: String?): Request {
        val url = provider.baseUrlOrThrow().withPathSuffix("chat", "completions")
        return Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .apply { if (apiKey != null) header("Authorization", "Bearer $apiKey") }
            .build()
    }

    private fun userMessage(content: String): JSONArray = JSONArray().put(
        JSONObject().put("role", "user").put("content", content)
    )

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val VERIFY_MAX_TOKENS = 16

        fun parseContent(body: String): String {
            val content = try {
                JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "")
            } catch (e: JSONException) {
                throw NetworkError.InvalidResponse("chat completion JSON", e)
            }
            return content.trim().ifEmpty { throw NetworkError.InvalidResponse("empty completion") }
        }
    }
}
