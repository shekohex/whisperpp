package com.github.shekohex.whisperpp.domain.dictation

import com.github.shekohex.whisperpp.dictation.FocusKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/** Optional enhancement of a committed transcript; failure always keeps the raw text. */
internal class EnhancementStep(
    private val deps: DictationSession.Deps,
    private val state: MutableStateFlow<SessionState>,
    private val currentFocus: () -> FocusKey?,
    private val isCurrent: (DictationRun) -> Boolean,
    private val emit: (SessionEvent) -> Unit,
) {
    /** Output mode CLIPBOARD: enhance without touching the field; returns the text to copy. */
    suspend fun forClipboard(r: DictationRun, raw: String): String =
        when (val result = request(r, raw)) {
            is DictationEnhancer.Result.Enhanced -> result.text
            is DictationEnhancer.Result.Kept -> {
                result.notice?.let { emit(SessionEvent.EnhancementNotice(it, result.isError)) }
                raw
            }
            null -> raw
        }

    suspend fun enhance(r: DictationRun, raw: String, segment: TextCommitter.CapturedSegment): String {
        val result = request(r, raw) ?: return raw
        return when (result) {
            is DictationEnhancer.Result.Kept -> {
                result.notice?.let { emit(SessionEvent.EnhancementNotice(it, result.isError)) }
                raw
            }
            is DictationEnhancer.Result.Enhanced -> {
                if (deps.committer.replaceSegment(segment, result.text, currentFocus())) {
                    emit(SessionEvent.EnhancementApplied)
                    result.text
                } else {
                    emit(SessionEvent.EnhancementKeptRaw(result.label))
                    raw
                }
            }
        }
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
        val startedAt = deps.clock()
        val result = try {
            enhancer.enhance(request)
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
}
