package com.github.shekohex.whisperpp.ime

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.data.PreferenceKeys.ADD_TRAILING_SPACE
import com.github.shekohex.whisperpp.data.PreferenceKeys.LANGUAGE_CODE
import com.github.shekohex.whisperpp.data.PreferenceKeys.POSTPROCESSING
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.ProviderType
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.toNoticeText
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.dictation.OpenAiRealtimeSttClient
import com.github.shekohex.whisperpp.dictation.OpenAiRealtimeStreamingSession
import com.github.shekohex.whisperpp.domain.dictation.StartDecision
import com.github.shekohex.whisperpp.domain.dictation.StartPlan
import com.github.shekohex.whisperpp.domain.dictation.TranscriptionRequest
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.privacy.SecretsStore
import okhttp3.OkHttpClient
import java.io.File

/** Platform-side pre-start checks and config resolution for a dictation run. Never waits on the user. */
class DictationStartPlanner(private val deps: Deps) {
    class Deps(
        val config: RuntimeConfigLoader,
        val secretsStore: SecretsStore,
        val okHttpClient: OkHttpClient,
        val awaitMigrations: suspend () -> Boolean,
        val awaitSendPolicy: suspend () -> Unit,
        val isSendBlocked: () -> Boolean,
        val checkMicPermission: () -> Boolean,
        val recordingFile: () -> File,
        val defaultPostprocessing: () -> String,
        val audioMediaType: String,
        val onBlocked: () -> Unit,
        val onSetupRequired: () -> Unit,
        val onNotice: (String) -> Unit,
        val onStreamingUnsupported: () -> Unit,
    )

    class SttSelection(
        val prefs: Preferences,
        val provider: ServiceProvider,
        val modelId: String,
        val warnings: String,
    )

    suspend fun resolveStt(packageName: String?): SttSelection? {
        val snapshot = deps.config.load(packageName)
        val provider = snapshot.provider(snapshot.effective.sttProviderId)
        val modelId = snapshot.effective.sttModelId
        if (provider == null || modelId.isBlank() || provider.models.none { it.id == modelId }) return null
        return SttSelection(snapshot.prefs, provider, modelId, snapshot.effective.warnings.toNoticeText(RuntimeChannel.STT))
    }

    suspend fun plan(focus: FocusKey): StartDecision {
        // Providers must be read post-migration; the app starts migrations in WhisperApp.onCreate.
        if (!deps.awaitMigrations()) {
            deps.onSetupRequired()
            return StartDecision.Abort
        }
        // Fail closed: never start before the per-app send policy is known.
        deps.awaitSendPolicy()
        if (deps.isSendBlocked()) {
            deps.onBlocked()
            return StartDecision.Abort
        }
        val selection = resolveStt(focus.packageName)
        if (selection == null) {
            deps.onSetupRequired()
            return StartDecision.Abort
        }
        if (selection.warnings.isNotBlank()) deps.onNotice(selection.warnings)
        if (!deps.checkMicPermission()) return StartDecision.PermissionRequired

        val prefs = selection.prefs
        val provider = selection.provider.copy(
            apiKey = deps.secretsStore.getProviderApiKey(selection.provider.id).orEmpty(),
        )
        val request = TranscriptionRequest(
            provider = provider,
            modelId = selection.modelId,
            languageCode = prefs[LANGUAGE_CODE] ?: "auto",
            mediaType = deps.audioMediaType,
            postprocessing = prefs[POSTPROCESSING] ?: deps.defaultPostprocessing(),
            addTrailingSpace = prefs[ADD_TRAILING_SPACE] ?: false,
            timeoutMs = provider.timeout,
            staticPrompt = provider.prompt.ifEmpty { prefs[PROMPT] ?: "" },
            temperature = provider.temperature,
            useContext = prefs[USE_CONTEXT] ?: false,
        )
        return StartDecision.Proceed(
            StartPlan(
                audioFile = deps.recordingFile(),
                request = request,
                streaming = createStreamingSession(provider, selection.modelId),
            )
        )
    }

    /** Realtime partials for models flagged for streaming; batch stays authoritative (G-11, WP-2.5). */
    private fun createStreamingSession(provider: ServiceProvider, modelId: String): StreamingTranscriptionSession? {
        val model = provider.models.find { it.id == modelId }
        if (model?.streamingPartialsSupported != true) return null
        val request = if (provider.type == ProviderType.OPENAI && provider.apiKey.isNotBlank()) {
            OpenAiRealtimeSttClient.buildRequest(provider.endpoint, modelId, provider.apiKey)
        } else {
            null
        }
        if (request == null) {
            deps.onStreamingUnsupported()
            return null
        }
        return OpenAiRealtimeStreamingSession(deps.okHttpClient, request)
    }
}
