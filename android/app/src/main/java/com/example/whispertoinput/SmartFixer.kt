package com.example.whispertoinput

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SmartFixer(private val context: Context) {
    private val TAG = "SmartFixer"
    private val client = OkHttpClient.Builder()
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

    suspend fun fix(text: String, contextInformation: String?): String {
        val prefs = context.dataStore.data.first()
        val enabled = prefs[SMART_FIX_ENABLED] ?: false
        if (!enabled || text.isBlank()) return text

        Log.d(TAG, "Starting Smart Fix for: \"$text\"")

        val backend = prefs[SMART_FIX_BACKEND] ?: context.getString(R.string.settings_smart_fix_backend_openai)
        val endpoint = prefs[SMART_FIX_ENDPOINT] ?: ""
        val apiKey = prefs[SMART_FIX_API_KEY] ?: ""
        val model = prefs[SMART_FIX_MODEL] ?: ""
        val temperature = prefs[SMART_FIX_TEMPERATURE] ?: 0.0f
        var promptTemplate = prefs[SMART_FIX_PROMPT] ?: ""

        if (promptTemplate.isBlank()) {
            promptTemplate = getDefaultPrompt()
        }

        if (endpoint.isBlank() || apiKey.isBlank() || model.isBlank()) {
            val error = "Smart Fix configuration incomplete (Endpoint, API Key, or Model missing)"
            Log.w(TAG, error)
            return text
        }

        val fullPrompt = buildString {
            append(promptTemplate)
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

        return if (backend == context.getString(R.string.settings_smart_fix_backend_openai)) {
            callOpenAI(endpoint, apiKey, model, temperature, fullPrompt)
        } else {
            callGoogle(endpoint, apiKey, model, temperature, fullPrompt)
        }
    }

    private fun callOpenAI(endpoint: String, apiKey: String, model: String, temperature: Float, prompt: String): String {
        Log.d(TAG, "Calling OpenAI at $endpoint with model $model")
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", temperature)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "OpenAI error response: $body")
                throw IOException("OpenAI error: ${response.code}")
            }
            val jsonResponse = JSONObject(body)
            return jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content").trim()
        }
    }

    private fun callGoogle(endpoint: String, apiKey: String, model: String, temperature: Float, prompt: String): String {
        Log.d(TAG, "Calling Google Gemini at $endpoint with model $model")
        val apiAction = "generateContent"
        
        var url = endpoint
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
                put("temperature", temperature)
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingBudget", 4096)
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Google error response: $body")
                throw IOException("Google error: ${response.code}")
            }
            
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
}
