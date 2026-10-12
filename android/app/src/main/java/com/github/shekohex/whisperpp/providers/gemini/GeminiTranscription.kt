package com.github.shekohex.whisperpp.providers.gemini

import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.core.network.executeForBody
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Gemini speech-to-text: the recording goes inline (base64) in a `generateContent` call next to a
 * transcription instruction. The input file is never deleted here (G-09).
 */
class GeminiTranscription(
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
        if (audio.length() > MAX_INLINE_AUDIO_BYTES) {
            throw NetworkError.InvalidResponse(
                detail = "audio too large for inline Gemini transcription",
                userMessageRes = R.string.gemini_error_audio_too_large,
            )
        }
        val body = withContext(Dispatchers.IO) { buildBody(audio, req) }
        val request = Request.Builder()
            .url(GeminiChat.generateContentUrl(provider.baseUrlOrThrow(), model.id))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .apply { if (apiKey != null) header("x-goog-api-key", apiKey) }
            .build()
        // Upload time grows with the recording: the timeout bounds socket inactivity, not the call.
        val response = client.executeForBody(request, req.timeoutMs, apiKey, callTimeoutMs = 0)
        Transcript(text = postProcess(parseTranscript(response.body), req.scriptConversion))
    }

    /** `GET {base}/models`: succeeds only with a valid key and a reachable endpoint. */
    override suspend fun verify(): Result<Unit> = providerCall {
        val apiKey = provider.apiKeyOrThrow(credentials)
        val request = Request.Builder()
            .url(provider.baseUrlOrThrow().withPathSuffix("models"))
            .get()
            .apply { if (apiKey != null) header("x-goog-api-key", apiKey) }
            .build()
        client.executeForBody(request, VERIFY_TIMEOUT_MS, apiKey)
    }

    internal fun buildBody(audio: File, req: TranscriptionRequest): JSONObject {
        val data = audio.readBytes().toByteString().base64()
        val parts = JSONArray()
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", req.mediaType).put("data", data)))
            .put(JSONObject().put("text", instruction(req)))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", req.temperature.toDouble())
                    .put("responseMimeType", "text/plain"),
            )
    }

    internal companion object {
        /** Gemini's inline request limit is 20 MB; base64 and the JSON envelope need headroom. */
        const val MAX_INLINE_AUDIO_BYTES = 18L * 1024 * 1024

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val BASE_INSTRUCTION = "Transcribe this audio verbatim. Output only the transcript text, no commentary."

        fun instruction(req: TranscriptionRequest): String = buildString {
            append(BASE_INSTRUCTION)
            languageName(req.languageCode)?.let { append(" The speech is in ").append(it).append('.') }
            val vocabulary = req.fullPrompt.trim()
            if (vocabulary.isNotEmpty()) append("\nPreferred spellings: ").append(vocabulary)
        }

        private fun languageName(code: String): String? {
            val trimmed = code.trim()
            if (trimmed.isEmpty() || trimmed.equals("auto", ignoreCase = true)) return null
            return Locale.forLanguageTag(trimmed).getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() } ?: trimmed
        }

        /** Joins the answer parts (thought summaries excluded); no candidate or no text means silence. */
        fun parseTranscript(body: String): String {
            val candidates = try {
                JSONObject(body).optJSONArray("candidates")
            } catch (e: JSONException) {
                throw NetworkError.InvalidResponse("generateContent JSON", e)
            }
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
            val text = buildString {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.has("text") && !part.optBoolean("thought", false)) append(part.optString("text"))
                }
            }
            return text.trim().trim('"', '“', '”').trim()
        }
    }
}
