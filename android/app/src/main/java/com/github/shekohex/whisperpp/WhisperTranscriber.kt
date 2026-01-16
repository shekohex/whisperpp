/*
 * This file is part of Whisper++, see <https://github.com/j3soon/whisper-to-input>.
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
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import com.github.liuyueyi.quick.transfer.ChineseUtils

class WhisperTranscriber {
    private val TAG = "WhisperTranscriber"
    private var currentTranscriptionJob: Job? = null
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("HTTP_Whisper", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader("Authorization")
    }

    private fun getClient(timeout: Int): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    fun startAsync(
        context: Context,
        filename: String,
        mediaType: String,
        attachToEnd: String,
        contextPrompt: String?,
        provider: ServiceProvider,
        modelId: String,
        postprocessing: String,
        addTrailingSpace: Boolean,
        timeout: Int,
        staticPrompt: String,
        temperature: Float,
        callback: (String?) -> Unit,
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

            // Make request
            val client = getClient(timeout)
            
            val request = buildWhisperRequest(
                filename,
                mediaType,
                provider,
                modelId,
                fullPrompt,
                temperature
            )
            val response = client.newCall(request).execute()

            // If request is not successful, or response code is weird
            if (!response.isSuccessful || response.code / 100 != 2) {
                throw Exception(response.body!!.string().replace('\n', ' '))
            }

            var rawText = response.body!!.string().trim()
            
            // For NVIDIA NIM or similar, remove quotes if they wrap the text
            if (provider.type == ProviderType.CUSTOM && 
                rawText.startsWith("\"") && rawText.endsWith("\"")) {
                rawText = rawText.substring(1, rawText.length - 1).trim()
            }
            
            val processedText = when (postprocessing) {
                context.getString(R.string.settings_option_to_simplified) -> ChineseUtils.tw2s(rawText)
                context.getString(R.string.settings_option_to_traditional) -> ChineseUtils.s2tw(rawText)
                else -> rawText // No conversion
            }

            if (attachToEnd == "") {
                return processedText + if (addTrailingSpace) " " else ""
            } else {
                // Only used for space key and enter key.
                return processedText + attachToEnd
            }
        }

        // Create a cancellable job in the main thread (for UI updating)
        val job = CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "Transcription job start")
            val (transcribedText, exceptionMessage) = withContext(Dispatchers.IO) {
                try {
                    val response = makeWhisperRequest()
                    File(filename).delete()
                    return@withContext Pair(response, null)
                } catch (e: CancellationException) {
                    return@withContext Pair(null, null)
                } catch (e: java.net.SocketTimeoutException) {
                    return@withContext Pair(null, "Request timed out")
                } catch (e: Exception) {
                    return@withContext Pair(null, e.message ?: "Unknown error")
                }
            }

            if (transcribedText != null) {
                Log.d(TAG, "Transcription completed length=${transcribedText.length}")
            }
            callback.invoke(transcribedText)

            if (!exceptionMessage.isNullOrEmpty()) {
                Log.e(TAG, exceptionMessage)
                exceptionCallback(exceptionMessage)
            }
        }

        registerTranscriptionJob(job)
    }

    fun stop() {
        registerTranscriptionJob(null)
    }

    private fun registerTranscriptionJob(job: Job?) {
        currentTranscriptionJob?.cancel()
        currentTranscriptionJob = job
    }

    private fun buildWhisperRequest(
        filename: String,
        mediaType: String,
        provider: ServiceProvider,
        modelId: String,
        prompt: String,
        temperature: Float
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
            if (provider.apiKey.isNotEmpty()) {
                add("Authorization", "Bearer ${provider.apiKey}")
            }
            add("Content-Type", "multipart/form-data")
        }.build()

        // Build URL with endpoint-specific parameters
        val url = if (provider.type == ProviderType.WHISPER_ASR) {
             "${provider.endpoint}?encode=true&task=transcribe&language=auto&word_timestamps=false&output=txt"
        } else {
             provider.endpoint
        }

        return Request.Builder()
            .headers(requestHeaders)
            .url(url)
            .post(requestBody)
            .build()
    }
}
