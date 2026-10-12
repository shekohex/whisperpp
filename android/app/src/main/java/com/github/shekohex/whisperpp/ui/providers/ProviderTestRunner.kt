package com.github.shekohex.whisperpp.ui.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.network.NetworkError
import com.github.shekohex.whisperpp.data.ModelCapabilities
import com.github.shekohex.whisperpp.data.ModelConfig
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.domain.providers.DiscoveryIdentity
import com.github.shekohex.whisperpp.domain.providers.Credentials
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import com.github.shekohex.whisperpp.domain.providers.toProviderUserMessage
import com.github.shekohex.whisperpp.providers.ModelClassifier
import com.github.shekohex.whisperpp.providers.realtime.RealtimeHandshakeProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Runs the editor's "Test text", "Test STT" and "Test connection" actions against an unsaved
 * provider form. Network work goes through [ProviderFactory]; no HTTP code lives in composables.
 */
class ProviderTestRunner(
    private val appContext: Context,
    private val providerFactory: ProviderFactory,
    private val repository: SettingsRepository,
) {
    suspend fun text(provider: ServiceProvider, model: ModelConfig, credentials: Credentials): ProviderTestResult {
        val result = providerFactory
            .enhancement(provider, model, credentials)
            .mapCatching { enhancer -> enhancer.verify().getOrThrow() }
            .map { appContext.getString(R.string.provider_test_ok) }
        return toResult(appContext.getString(TEXT_TEST_TITLE), result)
    }

    suspend fun stt(
        provider: ServiceProvider,
        model: ModelConfig,
        credentials: Credentials,
        audioUri: Uri,
    ): ProviderTestResult {
        val title = appContext.getString(STT_TEST_TITLE)
        val (file, mimeType) = try {
            copyToCache(audioUri)
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read test audio", e)
            return ProviderTestResult(title, null, "", appContext.getString(R.string.provider_test_audio_open_failed))
        }
        try {
            val request = TranscriptionRequest(
                provider = provider,
                modelId = model.id,
                languageCode = provider.languageCode,
                timeoutMs = STT_TEST_TIMEOUT_MS,
                mediaType = mimeType.ifBlank { "audio/wav" },
            )
            val result = providerFactory
                .transcription(provider, model, credentials)
                .mapCatching { transcriber -> transcriber.transcribe(file, request).getOrThrow().text }
            return toResult(title, result)
        } finally {
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
        }
    }

    /**
     * A successful listing proves auth + reachability; otherwise falls back to the model's verify().
     * When the provider also has a realtime model, the WebSocket handshake is checked as well.
     */
    suspend fun connection(providerId: String, provider: ServiceProvider, credentials: Credentials): ProviderTestResult {
        val base = reachability(providerId, provider, credentials)
        if (base.error != null) return base
        return realtimeHandshake(base, provider, credentials) ?: base
    }

    private suspend fun reachability(providerId: String, provider: ServiceProvider, credentials: Credentials): ProviderTestResult {
        val title = appContext.getString(R.string.provider_test_connection)
        val listing = providerFactory.discovery(provider, credentials)?.listModels()
        val listingMissing = (listing?.exceptionOrNull() as? NetworkError.Http)?.status in setOf(404, 405, 501)
        if (listing != null && !listingMissing) {
            listing.onSuccess { repository.saveDiscovered(providerId, it, identity = DiscoveryIdentity.of(provider)) }
            return toResult(title, listing.map { appContext.getString(R.string.provider_test_connection_ok_models, it.size) })
        }
        val models = provider.models
        val model = models.firstOrNull { it.capabilities.stt } ?: models.firstOrNull { it.capabilities.text }
            ?: return ProviderTestResult(title, null, "", appContext.getString(R.string.provider_test_connection_no_model))
        val verified = if (model.capabilities.stt) {
            providerFactory.transcription(provider, model, credentials).mapCatching { it.verify().getOrThrow() }
        } else {
            providerFactory.enhancement(provider, model, credentials).mapCatching { it.verify().getOrThrow() }
        }
        return toResult(title, verified.map { appContext.getString(R.string.provider_test_ok) })
    }

    /**
     * Opens and closes the realtime socket of the first realtime-flagged model (OpenAI and gateways
     * only), without audio. Null when the provider has no such model.
     */
    private suspend fun realtimeHandshake(
        base: ProviderTestResult,
        provider: ServiceProvider,
        credentials: Credentials,
    ): ProviderTestResult? {
        if (!ModelClassifier.acceptsRealtimeFlag(provider.type)) return null
        for (model in provider.models.filter { it.capabilities.stt }) {
            val transcriber = providerFactory.transcription(provider, model, credentials).getOrNull() ?: continue
            if (!transcriber.capabilities.realtime) continue
            val request = TranscriptionRequest(
                provider = provider,
                modelId = model.id,
                languageCode = provider.languageCode,
                timeoutMs = STT_TEST_TIMEOUT_MS,
            )
            val session = transcriber.openStream(request) ?: continue
            val outcome = RealtimeHandshakeProbe.probe(session)
            val error = outcome.exceptionOrNull()
                ?: return base.copy(body = appContext.getString(R.string.provider_test_realtime_ok, base.body, model.name))
            return ProviderTestResult(
                title = base.title,
                status = (error as? NetworkError.Http)?.status,
                body = "",
                error = appContext.getString(R.string.provider_test_realtime_failed, model.name, realtimeFailure(error)),
            )
        }
        return null
    }

    private fun realtimeFailure(error: Throwable): String = when (RealtimeHandshakeProbe.classify(error)) {
        RealtimeHandshakeProbe.Problem.UNAUTHORIZED -> appContext.getString(R.string.provider_test_realtime_unauthorized)
        RealtimeHandshakeProbe.Problem.NO_ENDPOINT ->
            appContext.getString(R.string.provider_test_realtime_no_endpoint, (error as NetworkError.Http).status)
        RealtimeHandshakeProbe.Problem.TIMEOUT -> appContext.getString(R.string.provider_test_realtime_timeout)
        RealtimeHandshakeProbe.Problem.OTHER -> error.toProviderUserMessage(appContext)
    }

    /** Result shown when a test is started without a model id. */
    fun missingModel(@StringRes titleRes: Int): ProviderTestResult = ProviderTestResult(
        title = appContext.getString(titleRes),
        status = null,
        body = "",
        error = appContext.getString(R.string.provider_test_model_required),
    )

    private suspend fun copyToCache(uri: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(uri).orEmpty()
        val file = File.createTempFile("provider_test_", null, appContext.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Unable to open audio file")
        } catch (e: CancellationException) {
            file.delete()
            throw e
        } catch (e: Exception) {
            file.delete()
            throw e as? IOException ?: IOException(e.message, e)
        }
        file to mimeType
    }

    private fun toResult(title: String, result: Result<String>): ProviderTestResult = result.fold(
        onSuccess = { ProviderTestResult(title, null, redactSecretsForUi(it), null) },
        onFailure = { error ->
            val status = when (error) {
                is NetworkError.Http -> error.status
                is NetworkError.Unauthorized -> error.status
                is NetworkError.RateLimited -> 429
                else -> null
            }
            ProviderTestResult(
                title = title,
                status = status,
                body = redactSecretsForUi((error as? NetworkError.Http)?.bodySnippet.orEmpty()),
                error = error.toProviderUserMessage(appContext),
            )
        },
    )

    companion object {
        @StringRes
        val TEXT_TEST_TITLE: Int = R.string.provider_test_text_title

        @StringRes
        val STT_TEST_TITLE: Int = R.string.provider_test_stt_title
        private const val TAG = "ProviderTestRunner"
        private const val STT_TEST_TIMEOUT_MS = 30_000L

        /** Test model: the configured one (if any) with the [capabilities] under test. */
        fun testModel(models: List<ModelConfig>, modelId: String, capabilities: ModelCapabilities): ModelConfig =
            models.find { it.id == modelId }?.copy(capabilities = capabilities)
                ?: ModelConfig(id = modelId, name = modelId, capabilities = capabilities)

        fun redactSecretsForUi(value: String): String {
            if (value.isBlank()) return value
            return value
                .replace(Regex("(?i)(Authorization\\s*:\\s*Bearer\\s+)([^\\s]+)"), "${'$'}1REDACTED")
                .replace(Regex("(?i)(x-goog-api-key\\s*:\\s*)([^\\s]+)"), "${'$'}1REDACTED")
                .replace(Regex("([?&]key=)[^&\\s]+"), "${'$'}1REDACTED")
        }
    }
}
