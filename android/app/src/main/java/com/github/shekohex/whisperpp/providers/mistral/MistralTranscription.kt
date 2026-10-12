package com.github.shekohex.whisperpp.providers.mistral

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import com.github.shekohex.whisperpp.providers.realtime.CloudRealtimeTranscription
import com.github.shekohex.whisperpp.providers.realtime.MistralDialect
import com.github.shekohex.whisperpp.providers.realtime.RealtimeText
import com.github.shekohex.whisperpp.providers.realtime.StreamingConfig
import com.github.shekohex.whisperpp.providers.realtime.StreamingDialect
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Mistral Voxtral: live streaming (see [MistralDialect]) and, as the fallback, the OpenAI-style
 * multipart `POST /v1/audio/transcriptions`. Mistral rejects unknown form fields, so only `file`,
 * `model` and an optional `language` are sent (no `prompt`/`response_format`).
 */
internal class MistralTranscription(
    client: OkHttpClient,
    provider: ServiceProvider,
    model: ModelConfig,
    credentials: Credentials,
) : CloudRealtimeTranscription(client, provider, model, credentials) {
    override fun dialect(config: StreamingConfig): StreamingDialect = MistralDialect(config)

    override suspend fun batchText(audio: File, req: TranscriptionRequest, apiKey: String): String {
        val request = buildRequest(audio, req, apiKey)
        val response = client.executeForBody(request, req.timeoutMs, apiKey, callTimeoutMs = 0)
        try {
            return JSONObject(response.body).getString("text").trim()
        } catch (e: JSONException) {
            throw NetworkError.InvalidResponse("Mistral transcription JSON", e)
        }
    }

    override fun verifyRequest(apiKey: String): Request {
        val url = RealtimeText.v1Url(provider.baseUrlOrThrow(), "models")
        return Request.Builder().url(url).get().header("Authorization", "Bearer $apiKey").build()
    }

    internal fun buildRequest(audio: File, req: TranscriptionRequest, apiKey: String): Request {
        val file = audio.asRequestBody((req.mediaType.ifBlank { "audio/wav" }).toMediaTypeOrNull())
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", file)
            .addFormDataPart("model", model.id.trim())
        StreamingConfig.languageOrNull(req.languageCode)?.let { body.addFormDataPart("language", it) }
        return Request.Builder()
            .url(RealtimeText.v1Url(provider.baseUrlOrThrow(), "audio", "transcriptions"))
            .post(body.build())
            .header("Authorization", "Bearer $apiKey")
            .build()
    }
}
