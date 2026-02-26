# STATE: Whisper++

## Project Reference

- **Core value:** Speak anywhere; get accurate, context-appropriate text inserted instantly.
- **Constraints:** Android IME (minSdk 24), BYO OpenAI/Gemini-compatible providers, privacy-first (no sensitive logging/leaks), editor correctness across third-party apps.

## Current Position

Phase: 1 of 7 (Privacy & Safety Controls)
Plan: 1 of 5 in current phase
Status: In progress
Last activity: 2026-02-26 - Completed 01-01-PLAN.md

Progress: `[██░░░░░░░░] 20%`

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

### Blockers

- None recorded.

### Notes / To Watch

- IME editor surfaces are unreliable; drop late events on focus changes.

## Session Continuity

Last session: 2026-02-26 14:57 UTC
Stopped at: Completed 01-01-PLAN.md
Resume file: None
