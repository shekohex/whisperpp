package com.github.shekohex.whisperpp

import android.content.Context
import android.util.Log
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.ThinkingType
import kotlinx.coroutines.CancellationException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SmartFixer(private val context: Context) {
    private val TAG = "SmartFixer"
    private var inFlightCall: Call? = null
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("HTTP_SmartFixer", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
        redactHeader("x-goog-api-key")
    }

    fun setNetworkLoggingEnabled(enabled: Boolean) {
        loggingInterceptor.level = if (enabled) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun getDefaultPrompt(): String {
        return try {
            context.resources.openRawResource(R.raw.smart_fix_prompt).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load default prompt", e)
            ""
        }
    }

    suspend fun fix(
        text: String, 
        contextInformation: String?,
        provider: ServiceProvider,
        modelId: String,
        temperature: Float,
        promptTemplate: String
    ): String {
        if (text.isBlank()) return text
        if (provider.endpoint.isBlank() || provider.apiKey.isBlank() || modelId.isBlank()) {
            val error = "Smart Fix configuration incomplete (Endpoint, API Key, or Model missing)"
            Log.w(TAG, error)
            return text
        }

        Log.d(TAG, "Starting Smart Fix using Provider: ${provider.name} (${provider.type}), Model: $modelId")

        var finalPromptTemplate = promptTemplate
        if (finalPromptTemplate.isBlank()) {
            finalPromptTemplate = getDefaultPrompt()
        }

        val fullPrompt = buildString {
            append(finalPromptTemplate)
            append("\n\n")
            if (!contextInformation.isNullOrBlank()) {
                append("<CONTEXT_INFORMATION>\n")
                append(contextInformation)
                append("\n</CONTEXT_INFORMATION>\n\n")
            }
            append("<TRANSCRIPT>\n")
            append(text)
            append("\n</TRANSCRIPT>")
        }

        return try {
            if (provider.type == ProviderType.OPENAI || provider.type == ProviderType.CUSTOM) {
                callOpenAI(provider, modelId, temperature, fullPrompt)
            } else if (provider.type == ProviderType.GEMINI) {
                callGoogle(provider, modelId, temperature, fullPrompt)
            } else {
                text
            }
        } catch (_: CancellationException) {
            text
        }
    }

    fun cancel() {
        inFlightCall?.cancel()
        inFlightCall = null
    }

    private fun buildSafeHttpError(provider: ServiceProvider, statusCode: Int, request: Request): String {
        val providerName = provider.name.ifBlank { provider.type.name }
        val host = provider.endpoint.toHttpUrlOrNull()?.host ?: request.url.host
        return "$providerName request failed (HTTP $statusCode, host: $host)"
    }

    private fun executeRequest(request: Request, provider: ServiceProvider): String {
        val call = client.newCall(request)
        inFlightCall = call

        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(buildSafeHttpError(provider, response.code, request))
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            if (call.isCanceled()) {
                throw CancellationException("Smart Fix request cancelled")
            }
            throw e
        } finally {
            if (inFlightCall === call) {
                inFlightCall = null
            }
        }
    }

    private fun callOpenAI(provider: ServiceProvider, model: String, temperature: Float, prompt: String): String {
        Log.d(TAG, "Calling OpenAI at ${provider.endpoint} with model $model")
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            
            // Thinking / Reasoning Effort
            val modelConfig = provider.models.find { it.id == model }
            if (provider.thinkingEnabled && modelConfig?.isThinking == true) {
                put("reasoning_effort", provider.thinkingLevel.lowercase())
            } else {
                put("temperature", temperature)
            }
        }

        val request = Request.Builder()
            .url(provider.endpoint)
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val body = executeRequest(request, provider)
        val jsonResponse = JSONObject(body)
        return jsonResponse.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content").trim()
    }

    private fun callGoogle(provider: ServiceProvider, model: String, temperature: Float, prompt: String): String {
        Log.d(TAG, "Calling Google Gemini at ${provider.endpoint} with model $model")
        val apiAction = "generateContent"
        
        var url = provider.endpoint
        if (!url.contains(":$apiAction") && model.isNotEmpty()) {
            url = if (url.endsWith("/")) "${url}models/${model}:$apiAction" 
                  else "${url}/models/${model}:$apiAction"
        }

        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                val modelConfig = provider.models.find { it.id == model }
                if (provider.thinkingEnabled && modelConfig?.isThinking == true) {
                    put("thinkingConfig", JSONObject().apply {
                        if (provider.thinkingType == ThinkingType.BUDGET) {
                            put("thinkingBudget", provider.thinkingBudget)
                        } else {
                            put("thinkingLevel", provider.thinkingLevel.uppercase())
                        }
                    })
                } else {
                    put("temperature", temperature)
                }
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", provider.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val body = executeRequest(request, provider)

        val jsonResponse = JSONObject(body)
        val result = StringBuilder()

        val candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0)
        val contentObj = candidate.getJSONObject("content")
        val parts = contentObj.getJSONArray("parts")

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                result.append(part.getString("text"))
            }
        }

        return result.toString().trim()
    }
}
