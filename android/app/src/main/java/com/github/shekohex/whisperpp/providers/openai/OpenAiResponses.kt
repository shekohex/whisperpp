package com.github.shekohex.whisperpp.providers.openai

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.core.network.executeForEvents
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

/**
 * `POST {base}/responses` for real OpenAI endpoints. Streaming accumulates
 * `response.output_text.delta` and requires an explicit successful `response.completed`; a stream
 * that ends any other way is a failure, never a partial result.
 */
class OpenAiResponses(
    private val client: OkHttpClient,
    private val provider: ServiceProvider,
    private val model: ModelConfig,
    private val credentials: Credentials,
) : EnhancementProvider {
    override val id: String = provider.id
    private val reasoningModel = isOpenAiReasoningModel(model.id)

    override suspend fun enhance(req: EnhancementRequest): Result<String> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val body = enhanceBody(req.composePrompt(), stream = false, req.thinking, req.temperature.toDouble())
        val response = client.executeForBody(buildRequest(body, apiKey), req.timeoutMs, apiKey)
        completedText(parseJson(response.body))
            .trim()
            .ifEmpty { throw NetworkError.InvalidResponse("empty response output") }
    }

    override suspend fun enhanceStreaming(
        req: EnhancementRequest,
        onPartial: suspend (String) -> Unit,
    ): Result<String> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val body = enhanceBody(req.composePrompt(), stream = true, req.thinking, req.temperature.toDouble())
        val accumulated = StringBuilder()
        var completed = false
        var finalText: String? = null
        client.executeForEvents(buildRequest(body, apiKey), req.timeoutMs, apiKey)
            .takeWhile { data ->
                val event = parseJson(data)
                when (event.optString("type")) {
                    "response.output_text.delta" -> {
                        accumulated.append(event.optString("delta"))
                        onPartial(accumulated.toString())
                    }
                    "response.completed" -> {
                        completed = true
                        finalText = completedText(event.optJSONObject("response"))
                    }
                    "response.failed" -> throw NetworkError.InvalidResponse("response.failed")
                    "response.incomplete" -> throw NetworkError.InvalidResponse("response incomplete")
                    "error" -> throw NetworkError.InvalidResponse("responses error event")
                }
                !completed
            }
            .collect()
        if (!completed) throw NetworkError.InvalidResponse("responses stream ended before response.completed")
        finalText.orEmpty()
            .trim()
            .ifEmpty { throw NetworkError.InvalidResponse("empty response output") }
    }

    override suspend fun verify(): Result<Unit> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val body = JSONObject().apply {
            put("model", model.id)
            put("input", "Reply with OK")
            put("store", false)
            put("max_output_tokens", VERIFY_MAX_TOKENS)
        }
        client.executeForBody(buildRequest(body, apiKey), VERIFY_TIMEOUT_MS, apiKey)
    }

    private fun enhanceBody(
        input: String,
        stream: Boolean,
        thinking: ThinkingConfig,
        temperature: Double,
    ): JSONObject = JSONObject().apply {
        put("model", model.id)
        put("input", input)
        put("store", false)
        if (stream) put("stream", true)
        when (thinking) {
            is ThinkingConfig.Enabled -> put("reasoning", JSONObject().put("effort", thinking.level.lowercase()))
            ThinkingConfig.Disabled -> if (!reasoningModel) put("temperature", temperature)
        }
    }

    private fun buildRequest(body: JSONObject, apiKey: String?): Request {
        val url = responsesUrl(provider.baseUrlOrThrow())
        return Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .apply { if (apiKey != null) header("Authorization", "Bearer $apiKey") }
            .build()
    }

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val VERIFY_MAX_TOKENS = 16

        fun parseJson(data: String): JSONObject = try {
            JSONObject(data)
        } catch (e: JSONException) {
            throw NetworkError.InvalidResponse("responses JSON", e)
        }

        /** Requires a `completed` response object and returns its assembled output text, if any. */
        fun completedText(response: JSONObject?): String {
            if (response == null) throw NetworkError.InvalidResponse("response.completed without response")
            when (response.optString("status")) {
                "completed" -> Unit
                "incomplete" -> throw NetworkError.InvalidResponse("response incomplete")
                else -> throw NetworkError.InvalidResponse("unexpected response status")
            }
            return outputText(response.optJSONArray("output") ?: JSONArray())
        }

        /** Joins `message`/`output_text` parts; reasoning items carry no visible text. */
        fun outputText(output: JSONArray): String = buildString {
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                if (item.optString("type") != "message") continue
                val content = item.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    when (part.optString("type")) {
                        "output_text" -> append(part.optString("text"))
                        "refusal" -> throw NetworkError.InvalidResponse("response refusal")
                    }
                }
            }
        }

        /**
         * A pasted native `/chat/completions` URL must not become
         * `/chat/completions/responses`: strip that suffix before appending `responses`.
         */
        fun responsesUrl(base: HttpUrl): HttpUrl {
            val trimmed = if (base.pathSegments.lastOrNull()?.isEmpty() == true) {
                base.newBuilder().removePathSegment(base.pathSegments.lastIndex).build()
            } else {
                base
            }
            val segments = trimmed.pathSegments
            val stripped = if (segments.size >= 2 &&
                segments[segments.size - 2] == "chat" && segments.last() == "completions"
            ) {
                trimmed.newBuilder()
                    .removePathSegment(segments.size - 1)
                    .removePathSegment(segments.size - 2)
                    .build()
            } else {
                trimmed
            }
            return stripped.withPathSuffix("responses")
        }
    }
}
