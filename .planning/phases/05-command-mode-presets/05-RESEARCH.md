# Phase 5: Command Mode & Presets - Research

**Researched:** 2026-03-05
**Domain:** Android IME command-mode workflow (selection/clipboard), voice→text instruction capture, LLM text transforms, preset library + undo UX
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### Clipboard fallback flow
- If no selection exists, show a guided fallback sheet with short, actionable steps.
- Require explicit clipboard preview confirmation (short snippet + character count) before using clipboard text.
- Resume only after user taps Continue; capture spoken instruction after text is confirmed.
- For very large clipboard text, show a warning but allow intentional continue.
- Allow two attempts total (initial + one retry); then abort safely with clear guidance.
- On cancel or fallback failure, exit command mode with no text changes.
- Do not reuse previously confirmed clipboard text; each command run requires fresh confirmation.
- If clipboard access is unavailable, show the reason and a manual select/copy recovery path.

### Preset library behavior
- Ship starter presets: cleanup, shorten, and tone rewrite.
- Provide an in-flow quick picker sheet during command mode.
- Use one shared preset library across dictation enhancement and selected-text transforms, with separate defaults per mode.
- Combine preset + spoken instruction; spoken instruction overrides preset where they conflict.

### Apply + undo behavior
- Apply transform results by immediate replacement of the selected text.
- Provide one-tap undo, available until the next command run or 30 seconds (whichever comes first).
- On transform failure/timeout, keep original text unchanged and show an error with Retry CTA.
- After apply or undo, keep resulting/restored text selected for fast re-run.

### Command-mode interaction
- Dedicated Command key uses single-tap to enter command mode.
- Show a compact status overlay with stage feedback (waiting, listening, processing, done/error).
- Support cancel via explicit Cancel control and Command-key toggle.
- On listen silence, reprompt once; if still silent, exit cleanly.

## Specific Ideas

- Preset and spoken instruction should apply together, not be mutually exclusive.
- Conflict rule is explicit: spoken instruction wins where it conflicts with preset intent.

### OpenCode's Discretion
- Exact visual styling (layout, spacing, iconography, motion) for fallback sheet, status overlay, and preset picker.
- Exact copy polish for prompts/errors while preserving the selected behavior.

### Deferred Ideas (OUT OF SCOPE)
## Deferred Ideas

None - discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CMD-01 | User can enter command mode via a dedicated Command key | IME UI is Compose (`KeyboardScreen`); add a Command key + command-mode state machine in `WhisperInputService` and UI overlay patterns consistent with existing dictation/enhancement. |
| CMD-02 | Command mode uses selected text from editor when available; otherwise clipboard fallback workflow | Use `InputConnection.getExtractedText()` to detect non-empty selection + best-effort `getSelectedText()`; implement guided clipboard confirmation sheet using `ClipboardManager` with Android 10+ access constraints (IME/focus). |
| CMD-03 | Speak instruction; transcribe; send (instruction + selected text) to selected text model/provider | Reuse `RecorderManager` + `WhisperTranscriber.startAsync()` for instruction STT; reuse `RuntimeSelectionResolver` effective `commandText` provider/model; reuse `SmartFixer` network calls with a command-mode prompt template incorporating preset + instruction. |
| CMD-04 | Replace selection with result and provide 1-tap undo | Use `InputConnection.setSelection(start,end)` + `commitText()` pattern; implement command undo entry with 30s expiry or cleared on next command run; keep text selected after apply/undo. |
| ENH-05 | Transform preset library (>= 3) usable for dictation or selected-text transforms | Add shared preset model + storage keys; incorporate preset intent into enhancement prompt and command prompt; defaults per mode + quick picker in command flow. |
</phase_requirements>

## Summary

Phase 5 is an **IME-first workflow**: command mode must run inside `WhisperInputService` (Compose keyboard UI) and must treat editor integration as **best-effort**. Selection comes from `InputConnection` when available; otherwise, the product explicitly falls back to a guided clipboard flow with per-run confirmation.

The safest implementation matches existing patterns already in the repo for dictation/enhancement: capture a **FocusKey + selection snapshot** at the start of a command run, run network work behind the existing **secure-field + per-app send policy gate**, and apply results using the same `InputConnection` editing primitives used by enhancement replacement—plus a dedicated **command undo** with a strict lifetime (30s / next-run).

Presets should be **prompt-shaping primitives** (cleanup/shorten/tone rewrite) stored in a shared library, referenced by ID, with separate default IDs for enhancement vs command mode. Command mode combines preset intent + spoken instruction in a single transform prompt; the prompt must explicitly state that **spoken instruction overrides** the preset when conflicting.

**Primary recommendation:** Implement a `CommandController`-style state machine (mirroring `DictationController`) that resolves text source (selection/clipboard), records instruction, calls STT + text transform, then applies replacement + timeboxed undo.

## Standard Stack

### Core
| Library / API | Version (in repo) | Purpose | Why Standard |
|---|---:|---|---|
| Android IME (`InputMethodService`) | — | Keyboard surface + editor integration | Required for “speak anywhere; insert/replace text” workflows. |
| `InputConnection` | — | Read selection, set selection, commit replacement | Standard IME <-> editor bridge. Existing code already relies on `getExtractedText()` + `setSelection()` + `commitText()`. |
| Jetpack Compose + Material 3 | Compose BOM `2024.02.00` | Keyboard UI, sheets, overlays | Repo uses Compose for the entire keyboard surface and existing sheets/overlays. |
| Kotlin Coroutines | — | Orchestrate recording/STT/LLM calls + timers | Existing patterns use `CoroutineScope(Dispatchers.Main/IO)` and `withTimeout`. |

### Supporting
| Library / API | Version (in repo) | Purpose | When to Use |
|---|---:|---|---|
| `ClipboardManager` | — | Clipboard fallback source | When editor selection is empty/unavailable; requires explicit confirmation per run. |
| DataStore Preferences | `androidx.datastore:datastore-preferences:1.0.0` | Persist defaults (per-mode preset selection) | Store selected preset IDs + “last shown” gates (already used for disclosures). |
| `WhisperTranscriber` (OkHttp) | — | STT transcription | Reuse for command instruction audio→text. |
| `SmartFixer` (OkHttp) | — | Text transform call to OpenAI/Gemini-compatible endpoints | Reuse for command transform; treat as enhancement-like text call with different prompt template. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|---|---|---|
| IME selection + clipboard fallback | Accessibility service selection | Explicitly out of scope for v1 (see REQUIREMENTS “Out of Scope”). Clipboard fallback is the intended compromise. |

## Architecture Patterns

### Recommended Project Structure (phase-local)
Follow existing separation: controller logic in `dictation/`-style packages, UI state in `ui/keyboard`, persistence keys in `MainActivity.kt` + repository helpers.

Suggested additions:
```
android/app/src/main/java/com/github/shekohex/whisperpp/
├── command/
│   ├── CommandController.kt
│   ├── CommandUndoEntry.kt
│   ├── SelectionResolver.kt
│   └── TransformPromptBuilder.kt
├── data/
│   ├── TransformPresets.kt
│   └── (settings helpers for default preset IDs)
└── ui/keyboard/
    ├── CommandModeUiState.kt
    ├── CommandModeOverlay.kt
    └── CommandPresetPickerSheet.kt
```

### Pattern 1: Controller-driven state machine (mirror `DictationController`)
**What:** A controller owns the “command run” lifecycle: resolve text → listen → process → apply → undo availability.

**When to use:** Always for command mode; don’t scatter lifecycle logic across composables.

**Example (existing pattern for safe replace via selection+commit):**
```kotlin
// Source: android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt
ic.beginBatchEdit()
val replaced = try {
    if (!ic.setSelection(start, end)) false
    else if (!ic.commitText(replacement, 1)) false
    else {
        ic.setSelection(start, start + replacement.length)
        true
    }
} finally {
    ic.endBatchEdit()
}
```

### Pattern 2: Focus safety gate (mirror DICT-07 behavior)
**What:** Capture `FocusKey` at the start of command mode and check that focus hasn’t changed before applying results.

**When to use:** Before applying transform results and before applying undo.

**How:** Use the existing `FocusKey.from(EditorInfo, focusInstanceId)` approach already used for dictation.

### Pattern 3: Reuse existing privacy gates + first-use disclosure
**What:** Command mode is an “external send” entry point; enforce the same **secure-field gate** + **per-app send policy** gate already implemented in `WhisperInputService.shouldBlockExternalSend()`.

**When to use:** Before starting recording, before STT, and before LLM call.

**Also:** There’s already a first-use disclosure mode placeholder for command text (`DISCLOSURE_SHOWN_COMMAND_TEXT`, `FirstUseDisclosureMode.COMMAND_TEXT`). Command mode should route through the same `awaitFirstUseDisclosure()` flow used by enhancement.

### Anti-Patterns to Avoid
- **Adding an AccessibilityService for selection:** explicitly out of scope; selection must be best-effort and otherwise clipboard fallback.
- **Applying replacements without FocusKey validation:** risks replacing text in the wrong field/app (existing DICT-07 decision warns IME surfaces are unreliable).
- **Persisting clipboard-confirmed text across runs:** forbidden by locked decision; each run must re-confirm.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Cross-app selection reading | Accessibility service selection scraper | `InputConnection` best-effort + clipboard fallback | Complexity + explicitly out of scope in v1 requirements. |
| Command parsing DSL | Custom rule engine for “cleanup/shorten/tone” | Preset prompt intents + LLM instruction | LLM already in stack; avoid brittle parsing that won’t generalize. |
| Full diff/merge UI | Custom diff renderer | Simple replace + undo (timeboxed) | v1 scope; ENH-20 diff preview is explicitly v2. |

## Common Pitfalls

### Pitfall 1: Selection retrieval is unreliable across editors
**What goes wrong:** `getSelectedText()` returns null, or selection indices are unavailable.

**Why it happens:** Editors can omit selected text for performance/privacy; IME APIs are best-effort.

**How to avoid:** Detect selection via `getExtractedText()` selectionStart/End first; use `getSelectedText()` as a best-effort value; if no selection, always drive clipboard fallback.

**Warning signs:** SelectionStart/End are -1, or start==end while user expects selection.

### Pitfall 2: Clipboard access appears “empty” on modern Android
**What goes wrong:** Clipboard returns null/empty unexpectedly.

**Why it happens:** Android restricts clipboard reading to the foreground focused app or the default IME.

**How to avoid:** Command mode reads clipboard only while the IME is active; still handle null and show “clipboard unavailable” recovery copy (manual select/copy).

**Verification note:** AOSP `ClipboardManager#getPrimaryClip()` Javadoc explicitly documents returning null when the caller is not the default IME or lacks input focus.

### Pitfall 3: Replacing text but losing selection (breaking “fast re-run”)
**What goes wrong:** After commit, cursor ends up at end with no selection.

**Why it happens:** Many code paths default to collapsing selection to cursor.

**How to avoid:** After applying transform (and after undo), explicitly `setSelection(start, start + newText.length)` best-effort.

### Pitfall 4: Focus changes mid-command (wrong-field edits)
**What goes wrong:** User switches app/field while processing; command result replaces a different field.

**Why it happens:** IME callbacks are asynchronous; currentInputConnection can change.

**How to avoid:** Capture `FocusKey` and validate before applying; if mismatch, abort apply and consider copying result to clipboard + toast (safe fallback).

### Pitfall 5: “Silence” detection coupled to STT latency
**What goes wrong:** Users wait too long with no feedback; STT returns blank; flow feels broken.

**Why it happens:** No VAD; blank transcript can mean either silence or STT failure.

**How to avoid:** Treat blank transcript as “silence” for UX purposes (reprompt once), but show a distinct error if STT returned an explicit failure message.

## Code Examples

Verified patterns from this codebase (reuse exactly):

### Best-effort selection snapshot + safe replace
```kotlin
// Source: android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt
private fun InputConnection.selectionSnapshot(): SelectionSnapshot? {
    val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return null
    val start = extracted.selectionStart
    val end = extracted.selectionEnd
    if (start < 0 || end < 0) return null
    return SelectionSnapshot(start = start, end = end)
}
```

### External-send gate to reuse for command mode
```kotlin
// Source: android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
private fun shouldBlockExternalSend(): Boolean {
    refreshExternalSendBlock(currentInputEditorInfo)
    return isExternalSendBlocked()
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| Apps reading clipboard freely in background | Clipboard read limited to focused app or default IME | Android 10+ | Clipboard fallback must tolerate null access and be explicitly user-driven. |

## Open Questions

1. **Command-mode recording UX: auto-stop vs explicit stop**
   - What we know: must be single-tap entry, cancellable via Command key, reprompt once on silence.
   - What's unclear: whether user gets a “Stop” action.
   - Recommendation: auto-stop on simple energy/silence heuristic + max duration; keep explicit Cancel always available.

2. **Large text threshold**
   - What we know: warn on “very large clipboard text” but allow continue.
   - What's unclear: what “very large” should be.
   - Recommendation: pick a conservative threshold (e.g., 10k–20k chars) and display actual char count; do not hard-reject.

3. **Failure fallback when apply fails**
   - What we know: on transform failure/timeout keep original unchanged.
   - What's unclear: if replacement fails due to editor limitations.
   - Recommendation: treat “apply failed” like a failure: keep original, show error, and offer “Copy result” CTA (optional) to avoid losing work.

## Sources

### Primary (HIGH confidence)
- Repo implementation pattern for replace+undo: `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt`
- External-send gate + disclosure plumbing: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Clipboard access constraint documented in AOSP `ClipboardManager` Javadoc (`getPrimaryClip()` returns null if not default IME or no focus): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/content/ClipboardManager.java
- Sensitive clipboard hint key (`ClipDescription.EXTRA_IS_SENSITIVE`): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/content/ClipDescription.java
- IME editor bridge API definitions: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/view/inputmethod/InputConnection.java

### Secondary (MEDIUM confidence)
- Android 10 clipboard privacy behavior change overview (official): https://developer.android.com/about/versions/10/privacy/changes#clipboard-data (not fetchable in this environment; validate during implementation)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH (repo-defined + Android platform APIs)
- Architecture: HIGH (mirrors existing dictation/enhancement patterns already implemented)
- Pitfalls: MEDIUM (platform behaviors vary by editor/OEM; some official docs not fetchable here)

**Research date:** 2026-03-05
**Valid until:** 2026-04-04
