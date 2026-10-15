package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.modes.OutputMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/** Optional enhancement of a committed transcript; failure always keeps the raw text. */
internal class EnhancementStep(
    private val deps: DictationSession.Deps,
    private val state: MutableStateFlow<SessionState>,
    private val currentFocus: () -> FocusKey?,
    private val isCurrent: (DictationRun) -> Boolean,
    private val windowVisible: () -> Boolean,
    private val emit: (SessionEvent) -> Unit,
    private val takeHistory: TakeHistory,
) {
    /** Output mode CLIPBOARD: enhance without touching the field; returns the text to copy. */
    suspend fun forClipboard(r: DictationRun, raw: String): String =
        when (val result = request(r, raw)) {
            is DictationEnhancer.Result.Enhanced -> result.text
            is DictationEnhancer.Result.Kept -> {
                if (result.isError) r.enhancementFailed = true
                result.notice?.let { emit(SessionEvent.EnhancementNotice(it, result.isError)) }
                raw
            }
            null -> raw
        }

    /** What goes into the field: the enhanced text, or [raw] when enhancement is off or failed. */
    class Outcome(val text: String, val enhanced: Boolean)

    /** Null when the run ended meanwhile. Failure keeps [raw] and says why. */
    suspend fun beforeInsert(r: DictationRun, raw: String): Outcome? {
        val result = request(r, raw)
        if (!isCurrent(r)) return null
        return when (result) {
            null -> Outcome(raw, enhanced = false)
            is DictationEnhancer.Result.Kept -> {
                if (result.isError) r.enhancementFailed = true
                result.notice?.let { emit(SessionEvent.EnhancementNotice(it, result.isError)) }
                Outcome(raw, enhanced = false)
            }
            is DictationEnhancer.Result.Enhanced -> if (result.text.isBlank()) {
                r.enhancementFailed = true
                emit(SessionEvent.EnhancementKeptRaw(result.label))
                Outcome(raw, enhanced = false)
            } else {
                Outcome(result.text, enhanced = true)
            }
        }
    }

    /** Whether this run will enhance, known up front so streaming partials can stay out of the field. */
    suspend fun willEnhance(r: DictationRun): Boolean {
        val enhancer = deps.enhancer ?: return false
        return enhancer.isEnabled(DictationEnhancer.Request(r.focus, PROBE_TEXT, null, r.options))
    }

    /** Enhancement context: text before the cursor plus the mode's extra sources. */
    fun context(r: DictationRun, options: DictationOptions): String? {
        val sameField = currentFocus() == r.focus
        val selected = if (options.contextSelectedText && sameField) deps.committer.selectedText() else null
        val clipboard = if (options.contextClipboard) deps.clipboardText()?.takeIf { it.isNotBlank() } else null
        val parts = listOfNotNull(
            r.contextPrompt,
            selected?.let { "Selected text:\n$it" },
            clipboard?.let { "Clipboard:\n$it" },
        )
        return if (parts.isEmpty()) null else parts.joinToString(separator = "\n\n")
    }

    /** Null when enhancement is off, absent, or the run ended meanwhile. */
    private suspend fun request(r: DictationRun, raw: String): DictationEnhancer.Result? {
        val enhancer = deps.enhancer ?: return null
        val request = DictationEnhancer.Request(r.focus, raw, r.enhancementContext, r.options)
        if (!enhancer.isEnabled(request)) return null
        if (!isCurrent(r)) return null
        state.value = SessionState.Enhancing(r.focus, raw)
        takeHistory.enhancing(r)
        val startedAt = deps.clock()
        val result = try {
            enhancer.enhanceStreaming(request) { text ->
                if (!isCurrent(r) || state.value !is SessionState.Enhancing || currentFocus() != r.focus ||
                    !windowVisible() || !deps.sendAllowed() || r.options.outputMode == OutputMode.CLIPBOARD ||
                    r.enhancementEditorChanged
                ) return@enhanceStreaming
                if (text.isEmpty() && !r.composing) return@enhanceStreaming
                if (!r.composing) r.replaceRange?.let(deps.committer::select)
                if (deps.committer.setComposing(r.focus, text, trackEdits = true)) {
                    r.composing = true
                    r.enhancementComposing = true
                } else if (r.composing) {
                    r.enhancementEditorChanged = true
                    r.composing = false
                    deps.committer.dropComposing()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DictationEnhancer.Result.Kept(e.message, isError = true)
        }
        if (!isCurrent(r)) return null
        if (result is DictationEnhancer.Result.Enhanced) {
            r.enhancementLatencyMs = (deps.clock() - startedAt).coerceAtLeast(0L)
            r.enhancementProviderId = result.providerId
            r.enhancementModelId = result.modelId
        }
        return result
    }

    private companion object {
        /** Any non-blank, non-punctuation text: only the enabled switch matters for [willEnhance]. */
        const val PROBE_TEXT = "a"
    }
}
