package com.github.shekohex.whisperpp.providers.deepgram

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.core.text.Vocabulary
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import com.github.shekohex.whisperpp.providers.realtime.CloudRealtimeTranscription
import com.github.shekohex.whisperpp.providers.realtime.DeepgramDialect
import com.github.shekohex.whisperpp.providers.realtime.RealtimeText
import com.github.shekohex.whisperpp.providers.realtime.StreamingConfig
import com.github.shekohex.whisperpp.providers.realtime.StreamingDialect
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Deepgram: live streaming over `/v1/listen` (see [DeepgramDialect]) and, as the fallback, a batch
 * `POST /v1/listen` with the raw recording as the body.
 */
internal class DeepgramTranscription(
    client: OkHttpClient,
    provider: ServiceProvider,
    model: ModelConfig,
    credentials: Credentials,
) : CloudRealtimeTranscription(client, provider, model, credentials) {
    override fun dialect(config: StreamingConfig): StreamingDialect = DeepgramDialect(config)

    override suspend fun batchText(audio: File, req: TranscriptionRequest, apiKey: String): String {
        val request = buildRequest(audio, req, apiKey)
        val response = client.executeForBody(request, req.timeoutMs, apiKey, callTimeoutMs = 0)
        return parseTranscript(response.body)
    }

    override fun verifyRequest(apiKey: String): Request {
        val url = RealtimeText.v1Url(provider.baseUrlOrThrow(), "projects")
        return Request.Builder().url(url).get().header("Authorization", "Token $apiKey").build()
    }

    internal fun buildRequest(audio: File, req: TranscriptionRequest, apiKey: String): Request {
        val modelId = model.id.trim()
        val url = RealtimeText.v1Url(provider.baseUrlOrThrow(), "listen").newBuilder().apply {
            addQueryParameter("model", modelId)
            addQueryParameter("smart_format", "true")
            StreamingConfig.languageOrNull(req.languageCode)?.let { addQueryParameter("language", it) }
            if (DeepgramDialect.supportsKeyterms(modelId)) {
                Vocabulary.normalize(req.vocabulary).take(MAX_KEYTERMS).forEach { addQueryParameter("keyterm", it) }
            }
        }.build()
        val body = audio.asRequestBody((req.mediaType.ifBlank { "audio/wav" }).toMediaTypeOrNull())
        return Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Token $apiKey")
            .build()
    }

    private fun parseTranscript(body: String): String {
        try {
            return JSONObject(body)
                .getJSONObject("results")
                .getJSONArray("channels")
                .getJSONObject(0)
                .getJSONArray("alternatives")
                .getJSONObject(0)
                .optString("transcript")
                .trim()
        } catch (e: JSONException) {
            throw NetworkError.InvalidResponse("Deepgram transcription JSON", e)
        }
    }

    private companion object {
        const val MAX_KEYTERMS = 100
    }
}
