# Architecture Research

**Domain:** Android dictation keyboard (IME) with optional LLM rewrite/post-processing
**Researched:** 2026-02-26
**Confidence:** MEDIUM

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
|                           IME process (Whisper++)                        |
├──────────────────────────────────────────────────────────────────────────┤
| UI Layer (Compose)                                                       |
|  ┌───────────────┐                                                       |
|  │ Keyboard UI    │  mic/stop/undo/settings                              |
|  └───────┬───────┘                                                       |
|          │ intents/events                                                 |
├──────────┴───────────────────────────────────────────────────────────────┤
| Orchestration / Domain                                                    |
|  ┌───────────────────────┐      ┌───────────────────────┐               |
|  │ ImeSessionManager     │◄────►│ ProfileResolver        │               |
|  │ (per EditorInfo/IC)   │      │ (per-app/per-field)    │               |
|  └──────────┬────────────┘      └──────────┬────────────┘               |
|             │ sessionScope + state machine             │                 |
|  ┌──────────▼──────────┐     transcript events     ┌──▼───────────────┐ |
|  │ DictationController  │──────────────────────────►│ TextApplier       │ |
|  │ (record→stt→rewrite) │◄─────────undo─────────────│ (composing/commit)│ |
|  └───────┬───────┬─────┘                           └──┬───────────────┘ |
|          │       │ rewrite request                        │ InputConnection
|          │       └─────────────────────────────┐          │ IPC
|          │                                     ▼          ▼
|  ┌───────▼──────────┐                   ┌───────────────────────┐        |
|  │ AudioCapture       │ audio frames/WAV │ LLMClient (SmartFix)   │        |
|  │ (AudioRecord/VAD)  │────────────────►│ provider adapters       │        |
|  └───────┬───────────┘                   └───────────────────────┘        |
|          │ audio                                                        |
|          ▼                                                              |
|  ┌───────────────────────┐                                             |
|  │ STTClient              │ (file upload or streaming)                   |
|  │ provider adapters       │                                             |
|  └───────────────────────┘                                             |
├──────────────────────────────────────────────────────────────────────────┤
| Persistence / Config                                                     |
|  ┌───────────────────────┐   ┌───────────────────────┐                 |
|  │ DataStore (settings)   │   │ Provider registry      │                 |
|  │ + per-app profiles     │   │ + model catalog        │                 |
|  └───────────────────────┘   └───────────────────────┘                 |
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
|                            Host app process                               |
|  ┌───────────────────────┐   InputConnection ops   ┌───────────────────┐ |
|  │ Focused editor/view    │◄────────────────────────│ IME TextApplier    │ |
|  │ (EditText/custom)      │────────────────────────►│ (commit/composing)│ |
|  └───────────────────────┘   selection updates      └───────────────────┘ |
└──────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **IME Shell** | Owns Android lifecycle (onStartInput/onFinishInput, window shown/hidden), hosts Compose UI, exposes current `InputConnection` and `EditorInfo` | `InputMethodService` subclass + `ComposeView` |
| **ImeSessionManager** | Creates/cancels a *session* bound to a specific `EditorInfo` + `InputConnection`; ensures late STT/LLM results do not apply to the wrong target | Session id + coroutine `SupervisorJob` cancelled on `onFinishInput`/window hidden |
| **DictationController** | State machine: idle → recording → transcribing (partials/final) → (optional) rewriting → committed; handles cancellation, retries, UX states | Kotlin sealed-state + flow/events |
| **AudioCapture** | Captures mic audio (PCM frames for streaming, or file for upload); amplitude for UI; optional VAD endpointing | `AudioRecord` pull loop + ring buffer / WAV writer |
| **STTClient** | Provider abstraction for transcription: file upload or streaming; emits partial and final hypotheses + errors | OkHttp (REST) and/or WebSocket; adapter per provider |
| **LLMClient (rewrite)** | Provider abstraction for “smart fix”: prompt templating, model selection, temp/thinking options | OkHttp JSON API (OpenAI/Gemini/custom) |
| **TextApplier (EditorBridge)** | The *only* component allowed to mutate the editor. Applies partial transcript via composing region, commits final text, performs in-place replacement, and supports undo when safe | Wraps `InputConnection` calls like `setComposingText`, `commitText`, `beginBatchEdit/endBatchEdit` |
| **UndoManager** | Tracks last applied operation (insert/replace) and reverts it if the editor state still matches expected preconditions | “optimistic undo” based on cursor position + surrounding text check |
| **ProfileResolver** | Resolves settings per app/field: language, provider/model, prompt append, rewrite mode, command mode enablement | Key by `EditorInfo.packageName`/`inputType`/field hints; stored in DataStore |

## Recommended Project Structure

Current code is centered around `WhisperInputService` + helper classes. For reliability and future features (streaming partials, in-place rewrite + undo, per-app profiles), typical IME projects isolate “editor mutations” and “session lifecycle” from UI and networking:

```
android/app/src/main/java/com/github/shekohex/whisperpp/
├── ime/
│   ├── WhisperInputService.kt            # Android entrypoint + UI wiring
│   ├── ImeSessionManager.kt              # session lifecycle + cancellation
│   └── EditorContext.kt                  # EditorInfo-derived context (packageName, inputType...)
├── dictation/
│   ├── DictationController.kt            # state machine + orchestration
│   ├── DictationEvents.kt                # partial/final/error events
│   └── UndoManager.kt                    # undo model + checks
├── editor/
│   ├── TextApplier.kt                    # composing/commit/replace helpers
│   └── InputConnectionFacade.kt          # thread/validity guard, batch edits
├── audio/
│   ├── AudioCapture.kt                   # PCM frames + VAD + amplitude
│   └── WavSink.kt                        # optional file sink for upload-only providers
├── providers/
│   ├── stt/
│   │   ├── SttClient.kt                  # interface
│   │   └── adapters/...                  # OpenAI/WhisperASR/custom
│   └── llm/
│       ├── LlmClient.kt                  # interface
│       └── adapters/...                  # OpenAI/Gemini/custom
├── profiles/
│   ├── ProfileResolver.kt                # per-app/per-field resolution
│   └── ProfileStore.kt                   # persistence (DataStore)
└── data/
    ├── SettingsRepository.kt
    └── ProviderModels.kt
```

### Structure Rationale

- **editor/** is a hard boundary: only this layer touches `InputConnection`. This prevents “late result writes” and makes undo/replace rules testable.
- **ime/** owns lifecycle; **dictation/** owns the dictation state machine; network/audio are pure services called by the controller.

## Architectural Patterns

### Pattern 1: Session-scoped structured concurrency (cancel on focus change)

**What:** Every dictation/rewrite runs inside a coroutine scope owned by the current editor session. When `onFinishInput()` or window hidden occurs, cancel the scope.

**When to use:** Always—IME targets change frequently; without session scoping you will apply results to the wrong app/field.

**Trade-offs:** Slight plumbing overhead (session ids, scope wiring) but huge reliability win.

**Example (conceptual):**
```kotlin
data class SessionKey(val packageName: String?, val fieldId: Int, val startedAtMs: Long)

class ImeSession(val key: SessionKey, val scope: CoroutineScope)
```

### Pattern 2: “Composing-first” text updates for partials, commit once

**What:** Streaming/partial hypotheses update the editor via `setComposingText(...)` (or composing region updates). On finalization, call `finishComposingText()` then `commitText(...)` (or `commitText(...)` directly).

**When to use:** Any partial/streaming transcription. Prevents spamming commits and enables “replace final with rewrite” safely.

**Trade-offs:** Some editors behave differently with long composing spans; mitigate with timeouts (commit if rewrite takes too long).

### Pattern 3: Two-phase apply for LLM rewrite (verify-before-replace)

**What:** Apply raw transcript quickly for UX. When rewrite arrives, replace *only if* the editor still appears to contain the previously inserted text at the expected location; otherwise surface as a suggestion the user can tap to apply.

**When to use:** Optional LLM post-processing; network latency and user typing make “blind replace” risky.

**Trade-offs:** More logic, but avoids corrupting user text.

### Pattern 4: Provider adapters behind stable domain interfaces

**What:** `STTClient` and `LLMClient` are interfaces emitting domain events (`Partial`, `Final`, `Error`). Implement adapters for OpenAI/WhisperASR/custom/Gemini.

**When to use:** BYO providers/models.

**Trade-offs:** Up-front abstraction work; pays off when adding streaming vs upload-only providers.

## Data Flow

### Request Flow (dictation with partials + optional rewrite)

```
[User taps mic]
    ↓
Keyboard UI
    ↓ event
DictationController (sessionScope)
    ↓ start
AudioCapture ──(PCM frames / WAV)──► STTClient
    ▲                                  │
    │ amplitude                         │ partial/final events
    │                                  ▼
    └────────────── UI state ◄── TextApplier ──► InputConnection ──► Host editor
                                      │
                                      └─(final transcript)─► LLMClient (optional)
                                                           │
                                                           ▼
                                                     rewritten text
                                                           │ verify+replace
                                                           ▼
                                                     TextApplier (replace)
                                                           │
                                                           ▼
                                                     UndoManager snapshot
```

### Key Data Flows

1. **Profile resolution:** `EditorInfo.packageName`/`inputType` → ProfileResolver → dictation settings (language, prompt append, rewrite mode).
2. **Context capture (prompting):** `InputConnection.getTextBeforeCursor(...)` / `getSelectedText(...)` (bounded) → prompt builder (base + per-app append).
3. **Partial transcription:** STT partial → `TextApplier.setComposingText(partial)` (throttled) to avoid flicker.
4. **Final transcription commit:** STT final → finish composing + commit; store “apply operation” for undo.
5. **Rewrite:** send (transcript + context + config) → LLM → replacement guarded by “editor still matches expected” check.

### State Management

```
ImeSession (key + scope)
   ↓ owns
DictationController State
   ↓ drives
UI state + TextApplier operations
```

## Build Order Implications (roadmap dependencies)

1. **Editor/TextApplier boundary + session lifecycle** (foundation)
   - Define “session key” and cancel rules.
   - Centralize *all* `InputConnection` mutations.
2. **Basic dictation pipeline (record → final transcript → commit)**
   - File-based audio + upload STT is simplest.
3. **Undo + safe replace primitives**
   - Needed before LLM rewrite to avoid destructive edits.
4. **Optional LLM rewrite as phase-2 apply**
   - Verify-before-replace; fallback to suggestion.
5. **Streaming/partials + composing UX polish**
   - Throttling, VAD endpointing, “commit on timeout”.
6. **Per-app/per-field profiles**
   - Depends on a stable session/editor context model.
7. **Command mode (selection-based)**
   - Builds on selection capture + safe replace.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Single user/device, upload-only STT | Monolithic process is fine, but keep `TextApplier` boundary to prevent corruption. |
| Multiple providers + streaming partials | Strong adapter interfaces, backpressure/ring buffer in AudioCapture, throttled composing updates. |
| Aggressive LLM features (commands, multi-step rewrites) | Treat LLM as “suggestion engine”; require explicit apply when editor drift is detected. |

### Scaling Priorities

1. **First bottleneck:** correctness under target switches → session scoping + cancellation.
2. **Second bottleneck:** editor flicker / latency → composing updates + batch edits + throttling.

## Anti-Patterns

### Anti-Pattern 1: Writing to `InputConnection` from network callbacks without session checks

**What people do:** fire STT/LLM requests and call `commitText()` when the response returns.

**Why it's wrong:** IME focus can change while the request is in-flight; you’ll write into the wrong app/field.

**Do this instead:** tag every request with a session key; drop results if the session no longer matches.

### Anti-Pattern 2: Using `commitText()` for partial results

**What people do:** commit each partial hypothesis.

**Why it's wrong:** creates undo/selection chaos and visible flicker; makes later replacement hard.

**Do this instead:** update a composing span (`setComposingText`) and commit once when final.

### Anti-Pattern 3: Blind in-place replacement after rewrite

**What people do:** delete N chars and insert rewritten text regardless of current cursor/editor state.

**Why it's wrong:** corrupts user edits if they typed/moved cursor during rewrite.

**Do this instead:** verify surrounding text/cursor position still matches expectations; otherwise present the rewrite as a suggestion.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| STT (Whisper/OpenAI-compatible) | HTTP multipart upload or streaming | Upload-only is simpler; streaming enables partials but needs backpressure + session checks. |
| LLM rewrite (OpenAI/Gemini/custom) | JSON request/response | Treat as optional, best-effort; never block commit of raw transcript. |
| App updater | Background check + in-app prompt | Must not disrupt IME session; avoid heavy work on IME main thread. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| UI ↔ DictationController | events + state flow | UI should not call providers directly. |
| DictationController ↔ TextApplier | commands (apply partial/final/replace/undo) | Single choke point for editor mutations. |
| DictationController ↔ Audio/STT/LLM | service interfaces | Makes provider swapping and testing feasible. |

## Sources

- AOSP `InputMethodService` (lifecycle, editor binding model): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/inputmethodservice/InputMethodService.java
- AOSP `InputConnection` (composing, commit, batch edits, selection/around-cursor text): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/view/inputmethod/InputConnection.java
- AOSP `AudioRecord` (pull-based recording, buffer sizing, permission requirements): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/media/java/android/media/AudioRecord.java
- AOSP LatinIME reference implementation (IME shell vs logic separation): https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/master/java/src/com/android/inputmethod/latin/LatinIME.java

---
*Architecture research for: Android dictation IME + optional LLM rewrite*
*Researched: 2026-02-26*
