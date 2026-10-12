package com.github.shekohex.whisperpp.ime

import com.github.shekohex.whisperpp.dictation.FocusKey
import com.github.shekohex.whisperpp.domain.autolearn.AutoLearnRevision

/**
 * Pure Auto-Learn capture state (§4.11 step 1): a baseline taken right after a dictation landed in a
 * field, a cheap dirty flag while the user edits, and the comparison that turns an edited committed
 * region into an [AutoLearnRevision]. Holds at most one capture; never logs field text.
 */
class AutoLearnCaptureStateMachine(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Conditions checked when a capture would start. */
    data class Gates(
        val enabled: Boolean,
        val secureField: Boolean,
        val blockedApp: Boolean,
        val autoSend: Boolean,
    )

    enum class SkipReason {
        DISABLED,
        AUTO_SEND,
        SECURE_FIELD,
        BLOCKED_APP,
        EMPTY,
        TOO_LONG,
        NO_SNAPSHOT,
        NOT_FOUND,
    }

    sealed interface BeginResult {
        data object Started : BeginResult
        data class Skipped(val reason: SkipReason) : BeginResult
    }

    sealed interface Evaluation {
        data class Changed(val revision: AutoLearnRevision) : Evaluation

        /** The committed region is intact (or only text outside it changed). */
        data object Unchanged : Evaluation

        /** The snapshot no longer lines up with the baseline (other text edited, field replaced). */
        data object Unrelated : Evaluation
    }

    private data class Baseline(
        val focus: FocusKey,
        val text: String,
        val start: Int,
        val end: Int,
        val committedText: String,
    )

    private var baseline: Baseline? = null
    private var latest: FieldTextReader.Snapshot? = null

    /** True once the field reported an edit after the baseline. */
    var dirty: Boolean = false
        private set

    /** True after the field was left unchanged; the user may come back to fix it before the deadline. */
    var parked: Boolean = false
        private set

    val focus: FocusKey?
        get() = baseline?.focus

    val isActive: Boolean
        get() = baseline != null

    fun isTracking(focus: FocusKey?): Boolean = focus != null && baseline?.focus == focus

    /** Same editor (package, input type, field id) regardless of the focus instance, e.g. after a keyboard switch. */
    fun isSameField(focus: FocusKey?): Boolean {
        val current = baseline?.focus ?: return false
        if (focus == null) return false
        return current.copy(focusInstanceId = 0L) == focus.copy(focusInstanceId = 0L)
    }

    /** Records the baseline for [committedText] just inserted into [focus]; replaces any running capture. */
    fun begin(
        focus: FocusKey,
        committedText: String,
        snapshot: FieldTextReader.Snapshot?,
        gates: Gates,
    ): BeginResult {
        cancel()
        val reason = skipReason(committedText, snapshot, gates)
        if (reason != null) return BeginResult.Skipped(reason)
        val field = snapshot ?: return BeginResult.Skipped(SkipReason.NO_SNAPSHOT)
        val start = locate(field, committedText) ?: return BeginResult.Skipped(SkipReason.NOT_FOUND)
        baseline = Baseline(focus, field.text, start, start + committedText.length, committedText)
        return BeginResult.Started
    }

    fun markDirty(focus: FocusKey?) {
        if (isTracking(focus)) dirty = true
    }

    /** Keeps the newest readable snapshot for triggers that can no longer read the field. */
    fun cache(focus: FocusKey?, snapshot: FieldTextReader.Snapshot?) {
        if (snapshot != null && isTracking(focus)) latest = snapshot
    }

    /** Follows the field into a new focus instance (the user came back to it) and expects edits. */
    fun resume(focus: FocusKey) {
        val current = baseline ?: return
        if (!isSameField(focus)) return
        baseline = current.copy(focus = focus)
        parked = false
        dirty = true
    }

    fun park() {
        if (baseline == null) return
        parked = true
        dirty = false
    }

    fun cancel() {
        baseline = null
        latest = null
        dirty = false
        parked = false
    }

    /** Compares [snapshot] (or the cached one) with the baseline; never ends the capture. */
    fun evaluate(snapshot: FieldTextReader.Snapshot?, packageName: String?): Evaluation {
        val base = baseline ?: return Evaluation.Unrelated
        val field = snapshot ?: latest ?: return Evaluation.Unrelated
        val corrected = correctedRegion(base, field.text) ?: return Evaluation.Unrelated
        if (corrected == base.committedText) return Evaluation.Unchanged
        if (corrected.isBlank() || corrected.length > MAX_TEXT_CHARS) return Evaluation.Unrelated
        return Evaluation.Changed(AutoLearnRevision(base.committedText, corrected, clock(), packageName))
    }

    /** Evaluates and ends the capture; returns the revision to submit, if any. */
    fun finish(snapshot: FieldTextReader.Snapshot?, packageName: String?): AutoLearnRevision? {
        if (baseline == null) return null
        val evaluation = if (dirty || snapshot != null) evaluate(snapshot, packageName) else Evaluation.Unchanged
        cancel()
        return (evaluation as? Evaluation.Changed)?.revision
    }

    private fun skipReason(committedText: String, snapshot: FieldTextReader.Snapshot?, gates: Gates): SkipReason? = when {
        !gates.enabled -> SkipReason.DISABLED
        gates.autoSend -> SkipReason.AUTO_SEND
        gates.secureField -> SkipReason.SECURE_FIELD
        gates.blockedApp -> SkipReason.BLOCKED_APP
        committedText.isBlank() -> SkipReason.EMPTY
        committedText.length > MAX_TEXT_CHARS -> SkipReason.TOO_LONG
        snapshot == null -> SkipReason.NO_SNAPSHOT
        else -> null
    }

    /** Start of [committed] in [field]: right before the cursor when possible, else the last match before it. */
    private fun locate(field: FieldTextReader.Snapshot, committed: String): Int? {
        val text = field.text
        val beforeCursor = field.cursor - committed.length
        if (beforeCursor >= 0 && text.regionMatches(beforeCursor, committed, 0, committed.length)) return beforeCursor
        val index = text.lastIndexOf(committed, startIndex = field.cursor.coerceIn(0, text.length))
        return index.takeIf { it >= 0 }
    }

    /**
     * The committed region as it reads in [finalText], or null when edits reach outside it. Text
     * appended or prepended at the region's edges is not part of the correction.
     */
    private fun correctedRegion(base: Baseline, finalText: String): String? {
        val before = base.text
        val limit = minOf(before.length, finalText.length)
        var prefix = 0
        while (prefix < limit && before[prefix] == finalText[prefix]) prefix++
        var suffix = 0
        while (suffix < limit - prefix &&
            before[before.length - 1 - suffix] == finalText[finalText.length - 1 - suffix]
        ) {
            suffix++
        }
        val changedStart = prefix
        val changedEnd = before.length - suffix
        if (changedStart == changedEnd && before.length == finalText.length) return base.committedText
        val inside = if (changedStart == changedEnd) {
            changedStart > base.start && changedStart < base.end
        } else {
            changedStart >= base.start && changedEnd <= base.end
        }
        if (!inside) {
            val touchesRegion = changedStart < base.end && changedEnd > base.start
            return if (touchesRegion) null else base.committedText
        }
        val regionEnd = finalText.length - (before.length - base.end)
        if (regionEnd < base.start) return null
        return finalText.substring(base.start, regionEnd)
    }

    companion object {
        /** Longer dictations or edits are not reviewed. */
        const val MAX_TEXT_CHARS = 4_000
    }
}
