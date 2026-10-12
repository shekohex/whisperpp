package com.github.shekohex.whisperpp.providers.openai

import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.RawResponse
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.core.network.executeRaw
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.Transcript
import com.github.shekohex.whisperpp.domain.providers.TranscriptionProvider
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.providers.ChineseScriptConverter
import com.github.shekohex.whisperpp.providers.VERIFY_TIMEOUT_MS
import com.github.shekohex.whisperpp.providers.apiKeyOrThrow
import com.github.shekohex.whisperpp.providers.baseUrlOrThrow
import com.github.shekohex.whisperpp.providers.providerCall
import com.github.shekohex.whisperpp.providers.withPathSuffix
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Multipart `/audio/transcriptions` (OpenAI, Groq, NIM, custom) and the Whisper ASR webservice
 * (`audio_file` + query parameters). The input file is never deleted here (G-09).
 */
class OpenAiCompatibleTranscription(
    private val client: OkHttpClient,
    private val provider: ServiceProvider,
    private val model: ModelConfig,
    private val credentials: Credentials,
    private val postProcess: suspend (String, ScriptConversion) -> String = ChineseScriptConverter::convert,
) : TranscriptionProvider {
    override val id: String = provider.id
    override val capabilities: ModelCapabilities = model.capabilities

    override suspend fun transcribe(audio: File, req: TranscriptionRequest): Result<Transcript> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val request = buildRequest(audio, req, apiKey)
        // Upload time grows with the recording, so the timeout bounds socket inactivity (as before),
        // not the whole call; cancelling the caller still aborts it.
        val response = client.executeForBody(request, req.timeoutMs, apiKey, callTimeoutMs = 0)
        val text = parseText(response)
        Transcript(text = postProcess(text, req.scriptConversion))
    }

    /** Any answer below 500 other than 401/403/429 proves the server is reachable. */
    override suspend fun verify(): Result<Unit> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val base = provider.baseUrlOrThrow()
        val url = if (provider.type == ProviderType.WHISPER_ASR) base else base.withPathSuffix("models")
        val request = Request.Builder().url(url).get().authorized(apiKey).build()
        val response = client.executeRaw(request, VERIFY_TIMEOUT_MS)
        when {
            response.code == 401 || response.code == 403 -> throw NetworkError.Unauthorized(response.code, url.host)
            response.code == 429 -> throw NetworkError.RateLimited(url.host, null)
            response.code >= 500 -> throw NetworkError.Http(response.code, url.host, null)
        }
    }

    internal fun buildRequest(audio: File, req: TranscriptionRequest, apiKey: String?): Request {
        val base = provider.baseUrlOrThrow()
        val formFilename = when (req.mediaType) {
            "audio/mpeg" -> "@audio.mp3"
            "audio/ogg" -> "@audio.ogg"
            "audio/wav" -> "@audio.wav"
            else -> "@audio.m4a"
        }
        val fileBody = audio.asRequestBody(req.mediaType.toMediaTypeOrNull())
        val language = req.languageCode.trim()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        val url: HttpUrl
        if (provider.type == ProviderType.WHISPER_ASR) {
            body.addFormDataPart("audio_file", formFilename, fileBody)
            url = base.newBuilder()
                .addQueryParameter("encode", "true")
                .addQueryParameter("task", "transcribe")
                .addQueryParameter("language", language.ifEmpty { "auto" })
                .addQueryParameter("word_timestamps", "false")
                .addQueryParameter("output", "txt")
                .build()
        } else {
            body.addFormDataPart("file", formFilename, fileBody)
            body.addFormDataPart("model", model.id)
            body.addFormDataPart("response_format", "text")
            if (language.isNotEmpty() && language != "auto") {
                body.addFormDataPart("language", language)
            }
            val prompt = req.fullPrompt
            if (prompt.isNotEmpty()) {
                body.addFormDataPart("prompt", prompt)
            }
            if (req.temperature > 0) {
                body.addFormDataPart("temperature", req.temperature.toString())
            }
            url = base.withPathSuffix("audio", "transcriptions")
        }
        return Request.Builder().url(url).post(body.build()).authorized(apiKey).build()
    }

    private fun parseText(response: RawResponse): String {
        var text = response.body.trim()
        val declaredJson = response.contentType?.contains("application/json") == true
        if (declaredJson || text.startsWith("{")) {
            try {
                val json = JSONObject(text)
                if (json.has("text")) {
                    text = json.getString("text").trim()
                }
            } catch (e: JSONException) {
                // `response_format=text` servers may still answer with a brace; only trust the header.
                if (declaredJson) throw NetworkError.InvalidResponse("transcription JSON", e)
            }
        }
        // NVIDIA NIM and similar wrap plain text in quotes.
        if (provider.type == ProviderType.CUSTOM && text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length - 1).trim()
        }
        return text
    }

    private fun Request.Builder.authorized(apiKey: String?): Request.Builder = apply {
        if (apiKey != null) header("Authorization", "Bearer $apiKey")
    }
}
