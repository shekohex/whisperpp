package com.github.shekohex.whisperpp.providers.elevenlabs

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import com.github.shekohex.whisperpp.providers.realtime.CloudRealtimeTranscription
import com.github.shekohex.whisperpp.providers.realtime.ElevenLabsDialect
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
 * ElevenLabs Scribe: live streaming (see [ElevenLabsDialect]) and, as the fallback, a multipart
 * `POST /v1/speech-to-text` with `file` + `model_id`.
 */
internal class ElevenLabsTranscription(
    client: OkHttpClient,
    provider: ServiceProvider,
    model: ModelConfig,
    credentials: Credentials,
) : CloudRealtimeTranscription(client, provider, model, credentials) {
    override fun dialect(config: StreamingConfig): StreamingDialect = ElevenLabsDialect(config)

    override suspend fun batchText(audio: File, req: TranscriptionRequest, apiKey: String): String {
        val request = buildRequest(audio, req, apiKey)
        val response = client.executeForBody(request, req.timeoutMs, apiKey, callTimeoutMs = 0)
        try {
            return JSONObject(response.body).getString("text").trim()
        } catch (e: JSONException) {
            throw NetworkError.InvalidResponse("ElevenLabs transcription JSON", e)
        }
    }

    override fun verifyRequest(apiKey: String): Request {
        val url = RealtimeText.v1Url(provider.baseUrlOrThrow(), "models")
        return Request.Builder().url(url).get().header("xi-api-key", apiKey).build()
    }

    internal fun buildRequest(audio: File, req: TranscriptionRequest, apiKey: String): Request {
        val file = audio.asRequestBody((req.mediaType.ifBlank { "audio/wav" }).toMediaTypeOrNull())
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", file)
            .addFormDataPart("model_id", model.id.trim())
        StreamingConfig.languageOrNull(req.languageCode)?.let { body.addFormDataPart("language_code", it) }
        return Request.Builder()
            .url(RealtimeText.v1Url(provider.baseUrlOrThrow(), "speech-to-text"))
            .post(body.build())
            .header("xi-api-key", apiKey)
            .build()
    }
}
