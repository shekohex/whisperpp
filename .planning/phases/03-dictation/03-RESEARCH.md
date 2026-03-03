# Phase 3: Dictation - Research

**Researched:** 2026-03-03
**Domain:** Android IME dictation (recording, streaming partials, composing text insertion, focus safety, undo)
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Mic interaction flow
- Interaction model should feel like WhatsApp/Telegram voice-note UX.
- Primary input is hold-to-talk; releasing finger pauses (does not send).
- Swipe up while holding enters locked hands-free mode.
- In paused/locked controls: trash on left, resume center, send on right.
- Swipe-to-cancel remains available in both hold and locked modes; require a longer swipe to reduce accidental cancel.
- Trash cancel requires confirmation; once confirmed, remove visible partial text immediately.
- Recording state uses VAD-driven wave effect while active; paused state shows paused icon + timer.
- Start/lock/cancel transitions should have haptic feedback.
- If keyboard hides or user switches keyboard/app, auto-pause and show toast (no auto-cancel/finalize).
- No hard dictation duration limit.
- Paused sessions do not expire; user must explicitly resume/send/cancel.

### Partial text behavior
- If streaming is supported, show partials inline as composing text.
- Partial updates should be throttled/smooth rather than every tiny chunk.
- If streaming is unsupported/disabled, insert no partials; insert only final text on send/stop.
- Finalization should atomically replace composing content, and always run replace path even when final equals latest partial.
- Live partials should use minimal punctuation/capitalization; spoken formatting commands (e.g., "new line", "comma") are disabled.
- Streaming may rewrite earlier words without a strict backtracking window.
- Disable manual text edits while recording is active.
- Keep visible partial text when paused.
- Use standard platform composing style; do not add confidence styling.
- If stream drops mid-session, keep partial text and finalize best effort.
- Keep partial behavior consistent across languages.
- Do not show a special "changed a lot" notice when final differs from partial.

### Cancel and undo rules
- Cancel discards the current dictation session content.
- Undo supports a multi-step dictation stack, stepped by repeated undo taps.
- One send operation equals one undo step, even with pause/resume inside the session.
- Undo appears as an inline quick action and stays until the next dictation action.
- Undo should still target dictated segment after manual typing when safe; if not safely applicable, fail with clear notice.
- Undo is scoped to the current field/context only (not cross-app/global).
- After undo, restore cursor to original insertion position.

### Focus safety handling
- Auto-pause on focus change, app backgrounding, and focus change while finger is holding mic.
- After auto-pause, require explicit resume (no auto-resume).
- Sending is allowed to current focused editable field.
- Track app + field identity for session context/safety decisions.
- If no editable field is focused on send: copy transcript to clipboard, show toast, keep session paused.
- If focused field is secure on send: block send, keep session paused, wait for non-secure field.
- If focus enters secure field while paused: keep paused until non-secure field is focused.
- Multiple focus changes keep a single consistent paused state.
- Safety messages should be short, toast-style reason strings.

### OpenCode's Discretion
- Exact gesture thresholds (lock swipe-up distance, cancel swipe distance/tolerance).
- Exact VAD wave rendering approach (native capability vs custom vs external package), while preserving chosen UX behavior.

### Deferred Ideas (OUT OF SCOPE)

None - discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DICT-01 | User can start/stop dictation via a mic key with clear recording state | Map mic gestures to a dictation state machine and drive UI from `KeyboardState`/new dictation state. |
| DICT-02 | If selected STT model supports streaming, partial transcription updates insert as composing text while speaking | Use `InputConnection.setComposingText` updates fed by a streaming STT client (WebSocket/SSE), gated by model capability. |
| DICT-03 | If streaming unsupported/disabled, dictation inserts only final transcript on stop (no partial insertion) | Capability gate in controller: no `setComposingText` calls; only final replace/commit at send/stop. |
| DICT-04 | User can cancel dictation and no additional text is inserted after cancel | Session token + cancellation plumbing (`Job.cancel`, `Call.cancel`, close WebSocket/SSE); immediately clear composing region on confirm. |
| DICT-05 | User can select dictation language and it is applied to STT requests | Thread `LANGUAGE_CODE` into STT requests; omit/auto when "auto". |
| DICT-06 | User can undo the last dictation insertion | Maintain per-field undo stack with safe-apply checks before deleting previously inserted segment. |
| DICT-07 | If editor focus changes during dictation, Whisper++ does not insert late results into a different field/app | Capture a `FocusKey` at session start; on focus changes auto-pause and drop late events unless still same focus/session. |
</phase_requirements>

## Summary

Phase 3 is mostly IME correctness work: wire the existing gesture-rich UI (`KeyboardScreen.kt`) to a real dictation session controller that (1) records audio, (2) optionally streams partial transcripts, (3) inserts partials as composing text, and (4) finalizes atomically on send/stop while staying safe across focus/app changes.

The repo already has the building blocks for non-streaming dictation (`RecorderManager.kt` + `WhisperTranscriber.kt`) and a keyboard UI state enum (`KeyboardState`). What's missing for this phase are: composing-text insertion (no `InputConnection.setComposingText` today), a streaming STT path gated by `ModelConfig.streamingPartialsSupported`, robust cancellation (no late inserts), a focus identity model to auto-pause and prevent cross-field inserts, dictation language actually applied to STT requests, and an undo stack scoped to the current editor context.

**Primary recommendation:** implement a `DictationController` in `WhisperInputService.kt` with a session token + `FocusKey`, and treat insertion as "composing lifecycle" (update composing on partials; replace+finish composing on final; clear composing on cancel).

## Standard Stack

### Core (repo-standard)

| Library/SDK | Version | Purpose | Why Standard |
|------------|---------|---------|-------------|
| Android SDK IME APIs (`InputMethodService`, `InputConnection`, `EditorInfo`) | compileSdk 34 / minSdk 24 | IME lifecycle + text insertion | Dictation correctness depends on platform primitives. |
| Kotlin + Coroutines | Kotlin plugin / stdlib | Concurrency for recording + networking + UI | Already used throughout service/network code. |
| Jetpack Compose + Material3 | Compose BOM `2024.02.00` | Keyboard UI states/gestures | Already the keyboard surface. |
| OkHttp | via `logging-interceptor:4.12.0` | STT/LLM HTTP + WebSocket | Already used for STT; supports cancelation + WebSocket. |
| DataStore Preferences | `1.0.0` | Language + toggles + policy | Existing settings store. |

### Supporting (add only if needed)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.squareup.okhttp3:okhttp-sse` | align with OkHttp (`4.12.0`) | SSE/EventSource client | If streaming STT endpoint uses SSE (`text/event-stream`). |
| Android `ClipboardManager` | platform | Clipboard fallback on send w/o focused editor | Required by locked focus-safety decision. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|----------|-----------|----------|
| SSE via `okhttp-sse` | Manual line parsing of `ResponseBody.source()` | Hand-rolled SSE parsers are easy to get subtly wrong (reconnects, multi-line `data:`). |

## Architecture Patterns

### Existing Code Reality (what you must plan around)

- `WhisperInputService.kt` currently drives `KeyboardState` and uses `RecorderManager` + `WhisperTranscriber.startAsync(...)` (file-based, non-streaming) then `currentInputConnection?.commitText(processedText, 1)`.
- No composing text usage today (only `commitText`), so partials + atomic replacement require new insertion primitives.
- `KeyboardScreen.kt` already implements hold, swipe-to-cancel, swipe-up-to-lock gestures; but the service-side behavior still follows a toggle model (Ready->Recording->Paused->Transcribing).
- `ModelConfig.streamingPartialsSupported` exists but is unused.

### Recommended Controller Split

Keep the IME service thin; centralize dictation behavior behind a controller with a strict session token.

Suggested objects (keep in `android/app/src/main/java/com/github/shekohex/whisperpp/`):

- `DictationController` (owned by `WhisperInputService`)
  - owns current `DictationSession?`
  - exposes UI-facing state (recording/paused/locked/transcribing + "undo available")
  - is the only place allowed to call `InputConnection` insertion APIs
- `DictationSession`
  - `sessionId: Long` (monotonic)
  - `focusKeyAtStart: FocusKey`
  - audio recording handles + (optional) streaming connection handles
  - composing insertion bookkeeping (whether composing is active; last partial)
  - undo stack mutations only on successful send/finalize
- `FocusKey`
  - minimal stable identity: `packageName`, plus editor signal(s) you can read consistently.
  - for planning: include `EditorInfo.packageName` and `EditorInfo.inputType`; optionally `fieldId` if meaningful in practice.

### State Machine (don't let callbacks mutate UI directly)

Use a single reducer-like path that reacts to events:

- User events: `MicDown`, `MicUp`, `Lock`, `Unlock`, `Resume`, `Send`, `CancelConfirmed`, `Undo`
- System events: `FocusChanged(newFocusKey)`, `ImeHidden`, `ImeShown`
- STT events: `Partial(text)`, `Final(text)`, `Error(reason)`, `StreamDropped`

Each transition must decide:

- what to do with audio capture (start/pause/resume/stop)
- what to do with partial insertion (update/keep/clear)
- what to do with network operations (start/stop/ignore late)

### Focus Safety Pattern (DICT-07)

- Capture `FocusKey` at dictation start.
- On focus change / `onWindowHidden` / app background: auto-pause session (stop sending audio; keep partial text visible; do not send/finalize).
- Any callback (partial/final) must check `sessionId` and current focus state before inserting; if mismatch, drop the event.
- On send: re-check focus and secure-field gate; only insert to current focused editable field (or clipboard fallback).

## Streaming Partials (DICT-02)

### What "streaming partials" must mean in this repo

- Behavior gate is `ModelConfig.streamingPartialsSupported` (already stored in provider config).
- Streaming path must emit partial transcripts continuously while recording, and allow rewrites (replace composing text wholesale each update).
- If streaming is unsupported OR a streaming connection fails: behave like DICT-03 (no new partial insertions; keep whatever is already visible; finalize best-effort on send).

### Practical Implementation Options

1) **OpenAI Realtime transcription (WebSocket)** (MEDIUM confidence; requires OpenAI-compatible endpoint that actually implements Realtime)
   - OpenAI docs show realtime transcription sessions with `conversation.item.input_audio_transcription.delta` and `.completed` events, and audio appended via `input_audio_buffer.append`.
   - This fits the "partials while speaking" requirement directly, and supports server VAD.
   - Planning impact: need PCM audio streaming (likely 24kHz mono PCM per docs) rather than only a WAV file; `RecorderManager` will need a chunk callback/flow.

2) **SSE/EventSource partials** (LOW-MEDIUM confidence; depends on custom providers)
   - Many "OpenAI-compatible" servers stream text via `text/event-stream`, but STT streaming is not a universal convention.
   - If you standardize on SSE for custom STT servers, use `okhttp-sse` and parse `data:` events into partial updates.

**Recommendation for planning:** implement streaming as a pluggable `StreamingSttClient` with an OpenAI-Realtime implementation first (since it has a documented event model), and treat everything else as non-streaming unless you can verify its protocol.

## Composing Text Insertion (partials + atomic finalization)

### The invariant

- **Only one composing region** per dictation session.
- Partials update that region via `setComposingText(...)` (throttled).
- Finalization always executes the replace path: `setComposingText(final)` then `finishComposingText()` inside a batch edit.
- Cancel clears the composing region immediately (after confirmation) and prevents late inserts via session token checks.

### Throttling/smoothing partials

- Apply a minimum interval (e.g., 100-250ms) OR coalesce by only emitting the most recent partial on a ticker.
- Optionally suppress micro-updates: don't update composing if new partial is the same as last partial.

### Minimal Kotlin pattern (IME-safe)

```kotlin
val ic = currentInputConnection ?: return

ic.beginBatchEdit()
try {
    ic.setComposingText(partialText, 1)
} finally {
    ic.endBatchEdit()
}

// Finalization
ic.beginBatchEdit()
try {
    ic.setComposingText(finalText, 1) // always run replace path
    ic.finishComposingText()
} finally {
    ic.endBatchEdit()
}
```

## Undo Stack (DICT-06)

### What to store per undo entry

- `focusKey`: to enforce per-field scope.
- `insertedText`: exact final text inserted.
- `selectionBeforeInsert`: cursor/selection start+end before insertion.
- `selectionAfterInsert`: cursor/selection after insertion (for restoration on undo).

### Safe-apply strategy (IME constraints)

- On undo request:
  - verify current `FocusKey` matches entry.
  - attempt to select the region where insertion happened (start..start+len) and read `getSelectedText(0)`; if it matches `insertedText`, delete it (`commitText("", 1)`), then restore cursor.
  - if mismatch or selection fails: do nothing; show toast ("Can't undo here").

Planning note: IME APIs don't reliably support global search; keep undo conservative and correct.

## Focus/App Change Handling (DICT-07)

### Where to hook

- `onStartInput` / `onStartInputView`: detect new `EditorInfo` and trigger `FocusChanged` event.
- `onWindowHidden`: **must not cancel** dictation per locked decision; instead pause session + toast.
- Optional: `onUpdateSelection` (if added) to detect user cursor movement and auto-pause.

### "No editable field focused"

- Detect via `currentInputConnection == null` OR `EditorInfo.inputType == 0` (TYPE_NULL) or other editor flags.
- On send in that case: run transcription, copy to clipboard, toast, keep session paused.

## Cancellation Guarantees (DICT-04)

- Treat cancel as a terminal event for the current `sessionId`.
- Cancel must:
  - stop recording
  - cancel any in-flight HTTP call (`Call.cancel()`) and coroutines (`Job.cancel()`)
  - close streaming socket/event source
  - clear composing region immediately (after confirmation)
- Any late callback must check the session token and return without inserting.

## Language Selection (DICT-05)

- `LANGUAGE_CODE` already exists (menu + DataStore key).
- `WhisperTranscriber.buildWhisperRequest(...)` does not currently apply language; planning must thread language to request:
  - For OpenAI/CUSTOM multipart: add `language` form field when language != "auto".
  - For Whisper ASR query param: replace hard-coded `language=auto` with selected language.
- Keep behavior consistent across streaming and non-streaming clients.

## Common Pitfalls

### Late inserts after cancel/focus change
**What goes wrong:** partial/final callbacks commit into the new focused app/field.
**How to avoid:** session token + `FocusKey` checks in *every* insertion path.

### Composing region corruption
**What goes wrong:** mixing `commitText` and composing APIs leads to duplicate text or cursor jumps.
**How to avoid:** only use composing APIs during dictation; finalize with set+finish composing inside a batch edit.

### IME lifecycle resets session unexpectedly
**What goes wrong:** `onWindowHidden` currently stops recorder/transcriber and resets to Ready.
**How to avoid:** change hidden/background behavior to "pause but keep session" (audio stops, partial stays visible, no finalize).

### Streaming audio format mismatch
**What goes wrong:** streaming STT expects PCM at a specific sample rate; sending wrong format produces garbage partials.
**How to avoid:** convert/record at the required rate for the streaming client (OpenAI realtime docs specify `audio/pcm` 24kHz mono).

## Code Examples

### Token-gated callback guard

```kotlin
val mySessionId = session.sessionId

fun onPartial(text: String) {
    if (controller.currentSessionId != mySessionId) return
    if (!controller.isSafeToInsertNow()) return
    controller.updateComposing(text)
}
```

### SSE client via `okhttp-sse` (if used)

```kotlin
val request = Request.Builder().url(url).build()
val factory = EventSources.createFactory(okHttpClient)
val eventSource = factory.newEventSource(request, object : EventSourceListener() {
    override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
        // parse data -> partial
    }
    override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
        // mark stream dropped
    }
})
```

### OpenAI Realtime transcription event model (what to handle)

- `conversation.item.input_audio_transcription.delta` -> composing update
- `conversation.item.input_audio_transcription.completed` -> final transcript for a turn (your controller may still wait for user "send")

## Open Questions

1. **What streaming protocol should `ModelConfig.streamingPartialsSupported=true` imply for non-OpenAI providers?**
   - What we know: only a boolean exists today; no endpoint path/versioning/config for streaming.
   - What's unclear: whether "custom/openai-compatible" STT servers used by Whisper++ will support WebSocket realtime or SSE streaming.
   - Recommendation: ship streaming via OpenAI Realtime first (documented), treat others as non-streaming until protocol is specified.

2. **Should streaming partials be "per-turn" (server VAD) or "continuous until send"?**
   - What we know: locked decisions allow rewrites and don't require strict backtracking window.
   - Recommendation: model partials as "current composing text for the whole session" and allow rewrites on each new delta.

## Sources

### Primary (HIGH confidence)
- OpenAI Realtime API overview: https://platform.openai.com/docs/guides/realtime
- OpenAI Realtime transcription (events + audio format): https://platform.openai.com/docs/guides/realtime-transcription

### Secondary (MEDIUM confidence)
- OkHttp SSE Javadoc (EventSource/EventSourceListener): https://javadoc.io/doc/com.squareup.okhttp3/okhttp-sse/4.12.0/

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH (repo + Gradle pinned versions)
- Architecture: MEDIUM (Android IME constraints are known; exact focus identity reliability varies by app)
- Streaming: MEDIUM-LOW (OpenAI Realtime is documented; mapping to arbitrary "OpenAI-compatible" endpoints is uncertain)

**Research date:** 2026-03-03
**Valid until:** 2026-04-02
