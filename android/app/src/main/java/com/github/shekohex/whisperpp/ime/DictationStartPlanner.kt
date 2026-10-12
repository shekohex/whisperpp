package com.github.shekohex.whisperpp.ime

import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.core.text.Vocabulary
import com.github.shekohex.whisperpp.data.PreferenceKeys.ADD_TRAILING_SPACE
import com.github.shekohex.whisperpp.data.PreferenceKeys.POSTPROCESSING
import com.github.shekohex.whisperpp.data.PreferenceKeys.PROMPT
import com.github.shekohex.whisperpp.data.PreferenceKeys.REALTIME_STT_ENABLED
import com.github.shekohex.whisperpp.data.PreferenceKeys.USE_CONTEXT
import com.github.shekohex.whisperpp.data.RuntimeChannel
import com.github.shekohex.whisperpp.data.RuntimeConfigLoader
import com.github.shekohex.whisperpp.data.ServiceProvider
import com.github.shekohex.whisperpp.data.RuntimeWarning
import com.github.shekohex.whisperpp.data.toNoticeText
import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.dictation.DictationOptions
import com.github.shekohex.whisperpp.domain.dictation.StartDecision
import com.github.shekohex.whisperpp.domain.dictation.StartPlan
import com.github.shekohex.whisperpp.domain.modes.ModeResolution
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
        /** User-facing (localized) summary of resolver warnings for one channel. */
        val describeWarnings: (List<RuntimeWarning>, RuntimeChannel) -> String = { warnings, channel ->
            warnings.toNoticeText(channel)
        },
    )

    class SttSelection(
        val prefs: Preferences,
        val provider: ServiceProvider,
        val modelId: String,
        val warnings: String,
        /** The session's mode (§4.10): language, output and per-run switches. */
        val mode: ModeResolution,
    )

    suspend fun resolveStt(packageName: String?): SttSelection? {
        val snapshot = deps.config.load(packageName)
        val provider = snapshot.provider(snapshot.effective.sttProviderId)
        val modelId = snapshot.effective.sttModelId
        if (provider == null || modelId.isBlank() || provider.models.none { it.id == modelId }) return null
        return SttSelection(
            prefs = snapshot.prefs,
            provider = provider,
            modelId = modelId,
            warnings = deps.describeWarnings(snapshot.effective.warnings, RuntimeChannel.STT),
            mode = snapshot.mode,
        )
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
        val vocabulary = if (acceptsVocabulary) deps.vocabulary() else emptyList()
        val request = TranscriptionRequest(
            provider = provider,
            modelId = selection.modelId,
            languageCode = selection.mode.languageCode,
            prompt = if (acceptsVocabulary) Vocabulary.appendToPrompt(basePrompt, vocabulary) else basePrompt,
            temperature = provider.temperature,
            timeoutMs = provider.timeout.toLong(),
            mediaType = deps.audioMediaType,
            scriptConversion = deps.scriptConversion(prefs[POSTPROCESSING]),
            addTrailingSpace = prefs[ADD_TRAILING_SPACE] ?: false,
            useContext = prefs[USE_CONTEXT] ?: false,
            vocabulary = Vocabulary.normalize(vocabulary),
        )
        return StartDecision.Proceed(
            StartPlan(
                audioFile = deps.recordingFile(),
                request = request,
                streaming = openStreaming(transcription, request, prefs),
                options = selection.mode.toOptions(),
            )
        )
    }

    private fun ModeResolution.toOptions() = DictationOptions(
        modeId = mode.id,
        outputMode = outputMode,
        enhancementEnabled = enhancementEnabled,
        removeFillers = removeFillers,
        formatParagraphs = formatParagraphs,
        autoSend = autoSend,
        contextSelectedText = contextSources.selectedText,
        contextClipboard = contextSources.clipboard,
    )

    /**
     * Streams when the model is flagged realtime (discovery or the user, never URL heuristics) and the
     * user keeps "Use realtime transcription" on (§4.5.1); streaming-only providers ignore the setting.
     * The session falls back to batch on failure.
     */
    private fun openStreaming(
        transcription: TranscriptionProvider,
        request: TranscriptionRequest,
        prefs: Preferences,
    ): StreamingTranscriptionSession? {
        if (!shouldStream(transcription, prefs[REALTIME_STT_ENABLED])) return null
        val session = transcription.openStream(request)
        if (session == null) deps.onStreamingUnsupported()
        return session
    }

    companion object {
        /** [realtimeSetting] is "Use realtime transcription" (null = unset, which means on). */
        internal fun shouldStream(transcription: TranscriptionProvider, realtimeSetting: Boolean?): Boolean =
            transcription.capabilities.realtime && (realtimeSetting != false || transcription.streamingOnly)
    }
}
