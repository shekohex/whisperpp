# STATE: Whisper++

## Project Reference

- **Core value:** Speak anywhere; get accurate, context-appropriate text inserted instantly.
- **Constraints:** Android IME (minSdk 24), BYO OpenAI/Gemini-compatible providers, privacy-first (no sensitive logging/leaks), editor correctness across third-party apps.

## Current Position

Phase: 1 of 7 (Privacy & Safety Controls)
Plan: 5 of 5 in current phase
Status: Phase complete
Last activity: 2026-02-26 - Completed 01-05-PLAN.md

Progress: `[██████████] 100%`

## Performance Metrics (targets)

- No dictation/command availability in secure fields.
- No auth headers or user payloads logged by default.

## Accumulated Context

### Decisions

- v1 is BYO API key only (no accounts/billing/backend).
- Realtime insertion is capability-gated; composing text for partials.
- Enhancement replaces in place with 1-tap undo; fallback to raw on failure.
- Command mode uses best-effort selection, with clipboard fallback.
- Networking errors shown to users are constrained to provider + HTTP code + endpoint host.
- Provider networking logs default to NONE with an opt-in HEADERS mode (no BODY).
- Cancellation is treated as benign and must cancel the underlying OkHttp Call.
- External-send entry points must use a single IME secure-field gate before any network-bound action.
- Secure-field blocked UX is tap-to-explain only (no proactive banner) with a global don’t-show-again preference.
- Settings destination requests from IME must route via MainActivity extras and safely fall back to settings main.
- Provider API keys are runtime-only on `ServiceProvider` and must never serialize into providers JSON/export payloads.
- Plaintext provider `apiKey` migration uses raw JSON tree parsing with sanitize-then-flag ordering guarded by `PROVIDER_API_KEY_MIGRATION_DONE`.
- IME dictation and Smart Fix must inject provider API keys from `SecretsStore` at call time via `provider.copy(apiKey=...)`.
- Per-app send policy persists as packageName→blocked JSON with default-allow semantics when no rule exists.
- Privacy & Safety must be addressable as a dedicated settings destination (`privacy_safety`) for IME deep-links.
- IME external-send blocking must enforce both secure-field and per-app rules, with app-rule-specific blocked explanation.
- Privacy disclosures must be generated from live provider config (base URL + endpoint path) via a shared formatter.
- First-use disclosure gating is mode-specific (`dictation`, `enhancement`, `command`) and resettable from Privacy & Safety.
- Verbose diagnostics logging is user-togglable but constrained to redacted HEADERS logging only.

### Blockers

- None recorded.

### Notes / To Watch

- IME editor surfaces are unreliable; drop late events on focus changes.

## Session Continuity

Last session: 2026-02-26 16:07 UTC
Stopped at: Completed 01-05-PLAN.md
Resume file: None
