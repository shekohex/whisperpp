# Project Research Summary

**Project:** Whisper++
**Domain:** Android IME (keyboard) for dictation (STT) + optional LLM rewrite/command mode (BYO providers)
**Researched:** 2026-02-26
**Confidence:** MEDIUM

## Executive Summary

Whisper++ is an Android keyboard (IME) that must reliably insert dictated text (and optional LLM rewrites) into arbitrary third-party editors via `InputConnection`. Experts build this by treating the editor as an unreliable IPC surface: centralize all mutations behind a single “text applier” boundary, and run dictation/rewrite work in a session-scoped state machine that is cancelled on focus changes so late STT/LLM events can’t write into the wrong app/field.

Recommended approach: ship a robust “commit-only” dictation pipeline first (push-to-talk → record → STT final → commit), then add IME-level undo and safe in-place replace primitives before introducing LLM rewrite and/or streaming partials. When adding partials, use composing spans (`setComposingText`) + throttling + anchor validation; when adding rewrite/command mode, use verify-before-replace and fall back to suggestion/apply flows when the editor has drifted.

Key risks are correctness (InputConnection quirks, composing/selection drift, race conditions), privacy/security (sensitive fields, context over-collection, BYO API keys), and performance (IME thread jank/ANR, audio route/device variability). Mitigate via session IDs + cancellation contracts, strict composing/commit rules, atomic edit transactions with snapshots for undo, context budgets + per-app denylist/sensitive-field blocking, keystore-backed secret storage with aggressive redaction, and time-budgeted editor reads.

## Key Findings

### Recommended Stack

The stack should stay Kotlin-first, coroutine-driven, and streaming-capable for provider integrations. Prefer OkHttp for provider HTTP/streaming to keep cancellation/retry/interceptor behavior explicit, with Kotlinx Serialization for strict typed payloads. Use DataStore for settings and per-app profiles; add Room only if history/prompt libraries need querying.

**Core technologies:**
- Kotlin 2.2.21: language/tooling baseline — aligns with modern AndroidX + KSP.
- Jetpack Compose (BOM 2025.12.01): settings UI + keyboard chrome — standard modern UI.
- Coroutines 1.10.0: session-scoped concurrency — cancellation is non-negotiable in IMEs.
- OkHttp 5.3.2: provider HTTP + streaming — best fit for SSE/chunked parsing + retries.
- Kotlinx Serialization 1.9.0: JSON models — typed, Kotlin-native multi-provider schemas.
- AndroidX DataStore 1.2.0: settings/profiles persistence — coroutine-first.

**Critical version notes:** match Kotlin↔KSP versions; use Compose Compiler Gradle plugin (Kotlin 2.0+) to avoid Compose↔Kotlin mismatch churn.

### Expected Features

The MVP should focus on push-to-talk dictation that works “everywhere”, with predictable insertion + undo, plus a minimal selected-text command mode to validate the “LLM keyboard” angle. Streaming partials and richer command grammars are valuable but should follow once insertion correctness is proven across a compatibility matrix.

**Must have (table stakes):**
- Push-to-talk dictation + clear recording state.
- Insert into arbitrary editors (Compose/Views/WebView) with robust error handling.
- Basic punctuation phrases + casing.
- Undo last insertion/replacement (IME-level).
- Respect secure/sensitive fields; clear privacy controls.

**Should have (competitive):**
- BYO STT + LLM providers (OpenAI-compatible/Gemini-compatible) with per-provider keys.
- Selected-text command mode with clipboard fallback.
- Small transform library (fix grammar, concise rewrite, formal/casual, translate).
- Basic per-app prompt profiles (default + per-app override) with visibility.

**Defer (v2+):**
- On-device/local-first STT or rewrite.
- Rich structured voice command grammar (navigation/editing).
- Enterprise provisioning/export/import.

### Architecture Approach

Adopt a session-scoped, state-machine controller with a hard editor-mutation boundary: UI emits intents; controller orchestrates audio/STT/LLM; a single `TextApplier` performs composing/commit/replace/undo against the current `InputConnection`, dropping late events when the session key no longer matches.

**Major components:**
1. IME shell (`InputMethodService`): lifecycle + UI wiring; exposes `EditorInfo`/`InputConnection`.
2. `ImeSessionManager`: creates/cancels per-editor sessions; owns coroutine scope.
3. `DictationController`: dictation/rewrite state machine; drives UI + text apply commands.
4. `AudioCapture`: `AudioRecord` lifecycle, frames/WAV, (optional) VAD.
5. `STTClient` + adapters: upload/streaming transcription emitting partial/final events.
6. `LLMClient` + adapters: rewrite/command transforms; best-effort and cancellable.
7. `TextApplier` (EditorBridge): only component that mutates editor (composing/commit/replace).
8. `UndoManager`: snapshot + optimistic validation; restores original safely.
9. `ProfileResolver` + DataStore: per-app/per-field settings (language, provider/model, prompts).

### Critical Pitfalls

1. **Late events writing into the wrong editor (no session scoping)** — tag every request with a session key; cancel scopes on `onFinishInput`/window hidden; drop mismatched events.
2. **Composing vs commit misuse** — strict rule: partials only via composing; finish composition and commit once; wrap edits in batch edits where available.
3. **Streaming races when cursor/selection changes** — maintain an anchor/fingerprint; pause/abort streaming updates on mismatch; offer “commit-only” fallback.
4. **Non-atomic rewrite/replace + unreliable undo** — apply rewrite as one transaction with snapshots; provide IME-level undo (don’t rely on app undo).
5. **Privacy/security footguns (context + secrets)** — strict context budgets; selected-text-only by default; sensitive-field blocking + per-app denylist; keystore-backed key storage + redaction.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 0: Privacy + secrets boundaries
**Rationale:** Trust failures are existential for keyboards; keys/context handling must be correct before expanding integrations.
**Delivers:** Sensitive-field gating, per-app deny/allow policy hooks, context budgets, secure key storage/redaction, “panic wipe”.
**Addresses:** Privacy basics; respects secure fields; BYO provider keys.
**Avoids:** Context over-collection; key leakage; accidental capture in password/OTP flows.

### Phase 1: IME session + editor mutation foundation (commit-only)
**Rationale:** Everything depends on correctness across arbitrary editors; build the `TextApplier` boundary + session cancellation first.
**Delivers:** Session-scoped controller, commit-only dictation pipeline (record → STT final → commit), basic punctuation/casing, IME-level undo for insertion.
**Addresses:** Push-to-talk dictation; reliable insertion; undo.
**Avoids:** InputConnection invalid/stale crashes; composing/commit drift; non-deterministic undo behavior.

### Phase 2: Provider/model abstraction hardening
**Rationale:** BYO providers require stable interfaces and a cancellation/retry contract before adding streaming/LLM complexity.
**Delivers:** Normalized `STTClient`/`LLMClient` interfaces + adapters, model catalog + capability flags, request IDs, retry/backoff rules that cannot duplicate committed text.
**Uses:** OkHttp streaming/REST + coroutines + serialization; DataStore for provider config.
**Avoids:** Stop-doesn’t-stop; duplicated insertions on retries; provider-specific code leaking everywhere.

### Phase 3: Streaming partials + composing UX (capability-gated)
**Rationale:** Partial results are high-UX but high-risk; only add once editor foundation + cancellation are proven.
**Delivers:** Composing-first partial updates with throttling, anchor validation, per-app capability gating, “commit-only” fallback.
**Addresses:** Partial/streaming results; faster perceived latency.
**Avoids:** Streaming race corruption; stuck composition; IME thread jank from too-frequent IPC updates.

### Phase 4: LLM rewrite + selected-text command mode (verify-before-replace)
**Rationale:** Rewrite/command mode is the differentiator, but must be reversible and safe under editor drift.
**Delivers:** Selected-text retrieval best-effort + clipboard fallback, transform presets, verify-before-replace with suggestion fallback, atomic replace + IME undo, basic per-app prompt profiles with visibility.
**Addresses:** Command mode; transform library; per-app profiles.
**Avoids:** Non-atomic rewrite trust death; prompt injection confusion; hidden per-app prompt footguns.

### Phase 5: Hardening + compatibility matrix
**Rationale:** IMEs fail in the “long tail” of editors/devices; validate before scaling features.
**Delivers:** Regression suite for Unicode/offsets, editor compatibility matrix runs (Gmail/Docs/WebView/Compose), audio route/device interruption testing, perf budgets (no idle typing jank).
**Avoids:** Emoji/locale corruption; device-specific audio failures; ANRs; “works in demo app only”.

### Phase Ordering Rationale

- Editor correctness + session scoping is the primary dependency for all higher-level features.
- Provider abstraction must define cancellation/retry semantics before streaming/LLM rewrite, otherwise late events corrupt text.
- Streaming and rewrite are separated: streaming stresses composing/anchors; rewrite stresses atomic replace + undo + privacy/prompt discipline.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 0:** Best-practice secure storage for BYO keys (platform keystore patterns vs deprecated Security Crypto) + backup exclusions.
- **Phase 3:** Provider-specific streaming protocols (SSE vs WS vs chunked JSONL), backpressure patterns, and per-editor composing quirks.
- **Phase 5:** Device audio routing matrix (BT SCO, route changes, interruptions) and diagnostics strategy.

Phases with standard patterns (skip research-phase):
- **Phase 1:** Session-scoped structured concurrency + centralized editor mutation boundary (well-established IME reliability pattern).
- **Phase 2 (core):** Adapter interfaces + normalized events + request IDs (standard integration architecture).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM | Mostly official release notes; final versions must align with current Gradle/AGP in-repo. |
| Features | MEDIUM | Table stakes well-supported; differentiators are product-informed and need user validation. |
| Architecture | MEDIUM | Grounded in AOSP IME/InputConnection model; needs fit-check against current code structure. |
| Pitfalls | MEDIUM | Strong practical alignment; some items are experience-based vs externally verified. |

**Overall confidence:** MEDIUM

### Gaps to Address

- **Current code alignment:** confirm how existing `WhisperInputService`/transcriber/smart-fix map onto the recommended `TextApplier` + session manager boundaries.
- **Provider capability discovery:** define a minimal capability schema (streaming, max context, languages) that works for OpenAI-compatible + Gemini-compatible endpoints.
- **Selection retrieval limits:** validate selected-text access patterns across target apps; define when to require clipboard fallback.

## Sources

### Primary (HIGH confidence)
- AndroidX Compose BOM mapping — https://developer.android.com/jetpack/compose/bom/bom-mapping
- AOSP `InputMethodService` / `InputConnection` contracts — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/inputmethodservice/InputMethodService.java ; https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/view/inputmethod/InputConnection.java
- AndroidX release notes: DataStore, Room, Lifecycle, Security — https://developer.android.com/jetpack/androidx/releases/

### Secondary (MEDIUM confidence)
- Kotlin releases — https://kotlinlang.org/docs/releases.html
- OkHttp tags / releases — https://github.com/square/okhttp/tags
- kotlinx.coroutines / kotlinx.serialization releases — https://github.com/Kotlin/kotlinx.coroutines/releases ; https://github.com/Kotlin/kotlinx.serialization/releases
- Gboard voice typing help (punctuation phrases, voice typing UX) — https://support.google.com/gboard/answer/2781851?hl=en

### Tertiary (LOW confidence)
- Media reports on Gboard “proofread” rollout (verify if used for competitive benchmarking) — see FEATURES.md sources.

---
*Research completed: 2026-02-26*
*Ready for roadmap: yes*
