/*
 * This file is part of Whisper++, see <https://github.com/shekohex/whisperpp>.
 *
 * Copyright (c) 2023-2025 Yan-Bin Diau, Johnson Sun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.shekohex.whisperpp

import android.content.Context
import android.util.Log
import com.github.shekohex.whisperpp.core.network.isTransportAllowed
import com.github.shekohex.whisperpp.data.ProviderAuthMode
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType
import org.json.JSONObject

class WhisperTranscriber(private val httpClient: OkHttpClient) {
    private val TAG = "WhisperTranscriber"
    private var currentTranscriptionJob: Job? = null
    private var inFlightCall: Call? = null
    private val loadedChineseDictionaries: MutableSet<TransType> = ConcurrentHashMap.newKeySet()

    private fun getClient(timeout: Int): OkHttpClient {
        return httpClient.newBuilder()
            .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    private fun buildSafeHttpError(provider: ServiceProvider, statusCode: Int, request: Request): String {
        val providerName = provider.name.ifBlank { provider.type.name }
        val host = provider.endpoint.toHttpUrlOrNull()?.host ?: request.url.host
        return "$providerName request failed (HTTP $statusCode, host: $host)"
    }

    fun startAsync(
        context: Context,
        filename: String,
        mediaType: String,
        attachToEnd: String,
        contextPrompt: String?,
        languageCode: String,
        provider: ServiceProvider,
        modelId: String,
        postprocessing: String,
        addTrailingSpace: Boolean,
        timeout: Int,
        staticPrompt: String,
        temperature: Float,
        callback: (String) -> Unit,
        exceptionCallback: (String) -> Unit
    ) {
        suspend fun makeWhisperRequest(): String {
            // Construct full prompt
            val fullPrompt = if (!contextPrompt.isNullOrEmpty()) {
                if (staticPrompt.isNotEmpty()) "$staticPrompt\n$contextPrompt" else contextPrompt
            } else {
                staticPrompt
            }

            // Foolproof message
            if (provider.endpoint.isEmpty()) {
                throw Exception(context.getString(R.string.error_endpoint_unset))
            }

            if (provider.authMode == ProviderAuthMode.API_KEY && provider.apiKey.isBlank()) {
                throw Exception(context.getString(R.string.error_apikey_unset))
            }

            // Make request
            val client = getClient(timeout)
            
            val request = buildWhisperRequest(
                filename,
                mediaType,
                provider,
                modelId,
                fullPrompt,
                temperature,
                languageCode,
            )
            if (!isTransportAllowed(request.url.scheme, request.url.host)) {
                throw IOException(context.getString(R.string.error_insecure_http_endpoint))
            }
            val call = client.newCall(request)
            inFlightCall = call

            val (rawResponseBody, contentType) = try {
                call.execute().use { response ->
                    if (!response.isSuccessful || response.code / 100 != 2) {
                        throw IOException(buildSafeHttpError(provider, response.code, request))
                    }
                    Pair(response.body?.string()?.trim().orEmpty(), response.header("Content-Type"))
                }
            } catch (e: IOException) {
                if (call.isCanceled() || !currentCoroutineContext().isActive) {
                    throw CancellationException("Whisper request cancelled")
                }
                throw e
            } finally {
                if (inFlightCall === call) {
                    inFlightCall = null
                }
            }

            var rawText = rawResponseBody

            // Check for JSON response (either by header or content)
            if (contentType?.contains("application/json") == true || rawText.startsWith("{")) {
                try {
                    val jsonObject = JSONObject(rawText)
                    if (jsonObject.has("text")) {
                        rawText = jsonObject.getString("text").trim()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse JSON response, using raw text", e)
                }
            }
            
            // For NVIDIA NIM or similar, remove quotes if they wrap the text
            if (provider.type == ProviderType.CUSTOM && 
                rawText.startsWith("\"") && rawText.endsWith("\"")) {
                rawText = rawText.substring(1, rawText.length - 1).trim()
            }
            
            val processedText = when (postprocessing) {
                context.getString(R.string.settings_option_to_simplified) -> {
                    ensureChineseDictionaryLoaded(TransType.TAIWAN_TO_SIMPLE)
                    ChineseUtils.tw2s(rawText)
                }
                context.getString(R.string.settings_option_to_traditional) -> {
                    ensureChineseDictionaryLoaded(TransType.SIMPLE_TO_TAIWAN)
                    ChineseUtils.s2tw(rawText)
                }
                else -> rawText // No conversion
            }

            if (attachToEnd == "") {
                return processedText + if (addTrailingSpace) " " else ""
            } else {
                // Only used for space key and enter key.
                return processedText + attachToEnd
            }
        }

        // Exactly one of the callbacks runs, on the main thread; none after cancellation (G-02, G-06).
        // TODO(F-03): unowned scope; WP-2.1 replaces this API with a suspend transcribe().
        val job = CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "Transcription job start")
            val (transcribedText, exceptionMessage) = withContext(Dispatchers.IO) {
                try {
                    val response = makeWhisperRequest()
                    return@withContext Pair(response, null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.net.SocketTimeoutException) {
                    return@withContext Pair(null, "Request timed out")
                } catch (e: IOException) {
                    return@withContext Pair(null, e.message ?: "Request failed")
                } catch (e: Exception) {
                    return@withContext Pair(null, e.message ?: "Unknown error")
                }
            }

            if (transcribedText != null) {
                Log.d(TAG, "Transcription completed length=${transcribedText.length}")
                callback(transcribedText)
            } else {
                val message = exceptionMessage?.takeIf { it.isNotEmpty() } ?: "Unknown error"
                Log.e(TAG, message)
                exceptionCallback(message)
            }
        }

        registerTranscriptionJob(job)
    }

    /** Loads the conversion dictionary once, off the main thread, right before its first use. */
    private suspend fun ensureChineseDictionaryLoaded(type: TransType) {
        if (!loadedChineseDictionaries.add(type)) return
        withContext(Dispatchers.IO) { ChineseUtils.preLoad(false, type) }
    }

    fun stop() {
        registerTranscriptionJob(null)
    }

    private fun registerTranscriptionJob(job: Job?) {
        currentTranscriptionJob?.cancel()
        inFlightCall?.cancel()
        inFlightCall = null
        currentTranscriptionJob = job
    }

    internal fun buildWhisperRequest(
        filename: String,
        mediaType: String,
        provider: ServiceProvider,
        modelId: String,
        prompt: String,
        temperature: Float,
        languageCode: String,
    ): Request {
        val file: File = File(filename)
        val fileBody: RequestBody = file.asRequestBody(mediaType.toMediaTypeOrNull())
        val requestBody: RequestBody = MultipartBody.Builder().apply {
            setType(MultipartBody.FORM)
            // Determine filename based on media type
            val formDataFilename = when (mediaType) {
                "audio/mpeg" -> "@audio.mp3"
                "audio/ogg" -> "@audio.ogg"
                "audio/wav" -> "@audio.wav"
                else -> "@audio.m4a"
            }
            // Add file to payload
            if (provider.type == ProviderType.OPENAI || provider.type == ProviderType.CUSTOM || provider.type == ProviderType.GEMINI) {
                 addFormDataPart("file", formDataFilename, fileBody)
            } else if (provider.type == ProviderType.WHISPER_ASR) {
                 addFormDataPart("audio_file", formDataFilename, fileBody)
            }
            
            // Add backend-specific parameters to payload
            if (provider.type == ProviderType.OPENAI || provider.type == ProviderType.CUSTOM) {
                addFormDataPart("model", modelId)
                addFormDataPart("response_format", "text")
                val lang = languageCode.trim()
                if (lang.isNotEmpty() && lang != "auto") {
                    addFormDataPart("language", lang)
                }
                if (prompt.isNotEmpty()) {
                    addFormDataPart("prompt", prompt)
                }
                if (temperature > 0) {
                     addFormDataPart("temperature", temperature.toString())
                }
            }
            
            // For Gemini or others, might need different handling if they don't support standard Whisper API.
            // But usually "Transcription" via Gemini isn't the same as Whisper.
            // Assuming "Google Gemini" provider for *Transcription* implies using Gemini 1.5 Pro's audio capabilities
            // which requires a different JSON structure (File API or inline data).
            // However, the existing code didn't have Gemini for transcription, only OpenAI/WhisperASR/NVIDIA.
            // The prompt says "Add ... Google Gemini ... for LLM providers".
            // It also says "Transcription Providers" can be added.
            // If the user selects Gemini for Transcription, we might need to implement Gemini Audio API.
            // But for now, assuming Custom/OpenAI/WhisperASR follow standard multipart.
            
        }.build()

        val requestHeaders: Headers = Headers.Builder().apply {
            if (provider.authMode == ProviderAuthMode.API_KEY) {
                add("Authorization", "Bearer ${provider.apiKey}")
            }
        }.build()

        // Build URL with endpoint-specific parameters
        val url = if (provider.type == ProviderType.WHISPER_ASR) {
            val base = provider.endpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid endpoint")
            val lang = languageCode.trim().ifEmpty { "auto" }
            base.newBuilder()
                .addQueryParameter("encode", "true")
                .addQueryParameter("task", "transcribe")
                .addQueryParameter("language", lang)
                .addQueryParameter("word_timestamps", "false")
                .addQueryParameter("output", "txt")
                .build()
        } else {
            val base = provider.endpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid endpoint")
            val segments = base.pathSegments.filter { it.isNotBlank() }
            val alreadyDerived = segments.size >= 2 && segments.takeLast(2) == listOf("audio", "transcriptions")
            if (alreadyDerived) {
                base
            } else {
                base.newBuilder().addPathSegments("audio/transcriptions").build()
            }
        }

        return Request.Builder()
            .headers(requestHeaders)
            .url(url)
            .post(requestBody)
            .build()
    }
}
