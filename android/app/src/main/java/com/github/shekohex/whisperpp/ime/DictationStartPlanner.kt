package com.github.shekohex.whisperpp.ime

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.core.text.Vocabulary
import com.github.shekohex.whisperpp.data.PreferenceKeys.ADD_TRAILING_SPACE
import com.github.shekohex.whisperpp.data.PreferenceKeys.LANGUAGE_CODE
import com.github.shekohex.whisperpp.data.PreferenceKeys.POSTPROCESSING
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.REALTIME_STT_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.toNoticeText
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.dictation.StartDecision
import com.github.shekohex.whisperpp.domain.dictation.StartPlan
import com.github.shekohex.whisperpp.domain.providers.ProviderFactory
import com.github.shekohex.whisperpp.domain.providers.ScriptConversion
import com.github.shekohex.whisperpp.domain.providers.StreamingTranscriptionSession
import com.github.shekohex.whisperpp.domain.providers.TranscriptionProvider
import com.github.shekohex.whisperpp.domain.providers.TranscriptionRequest
import java.io.File

/** Platform-side pre-start checks and config resolution for a dictation run. Never waits on the user. */
class DictationStartPlanner(private val deps: Deps) {
    class Deps(
        val config: RuntimeConfigLoader,
        val providers: ProviderFactory,
        val awaitMigrations: suspend () -> Boolean,
        val awaitSendPolicy: suspend () -> Unit,
        val isSendBlocked: () -> Boolean,
        val checkMicPermission: () -> Boolean,
        val recordingFile: () -> File,
        /** Maps the stored post-processing preference (null when unset) to a script conversion. */
        val scriptConversion: (String?) -> ScriptConversion,
        val audioMediaType: String,
        val onBlocked: () -> Unit,
        val onSetupRequired: () -> Unit,
        val onNotice: (String) -> Unit,
        val onStreamingUnsupported: () -> Unit,
        /** Dictionary vocabulary; sent only to models with `capabilities.vocabulary`. */
        val vocabulary: suspend () -> List<String> = { emptyList() },
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

        // Unsupported provider/model combinations never reach the network (G-04).
        val transcription = deps.providers.transcription(selection.provider, selection.modelId).getOrElse {
            deps.onSetupRequired()
            return StartDecision.Abort
        }

        val prefs = selection.prefs
        val provider = selection.provider
        val basePrompt = provider.prompt.ifEmpty { prefs[PROMPT] ?: "" }
        val acceptsVocabulary = provider.models.find { it.id == selection.modelId }?.capabilities?.vocabulary == true
        val request = TranscriptionRequest(
            provider = provider,
            modelId = selection.modelId,
            languageCode = prefs[LANGUAGE_CODE] ?: "auto",
            prompt = if (acceptsVocabulary) Vocabulary.appendToPrompt(basePrompt, deps.vocabulary()) else basePrompt,
            temperature = provider.temperature,
            timeoutMs = provider.timeout.toLong(),
            mediaType = deps.audioMediaType,
            scriptConversion = deps.scriptConversion(prefs[POSTPROCESSING]),
            addTrailingSpace = prefs[ADD_TRAILING_SPACE] ?: false,
            useContext = prefs[USE_CONTEXT] ?: false,
        )
        return StartDecision.Proceed(
            StartPlan(
                audioFile = deps.recordingFile(),
                request = request,
                streaming = openStreaming(transcription, request, prefs),
            )
        )
    }

    /**
     * Streams when the model is flagged realtime (discovery or the user, never URL heuristics) and the
     * user keeps "Use realtime transcription" on (§4.5.1). The session falls back to batch on failure.
     */
    private fun openStreaming(
        transcription: TranscriptionProvider,
        request: TranscriptionRequest,
        prefs: Preferences,
    ): StreamingTranscriptionSession? {
        if (!transcription.capabilities.realtime || prefs[REALTIME_STT_ENABLED] == false) return null
        val session = transcription.openStream(request)
        if (session == null) deps.onStreamingUnsupported()
        return session
    }
}
