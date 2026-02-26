# STATE: Whisper++

## Project Reference

- **Core value:** Speak anywhere; get accurate, context-appropriate text inserted instantly.
- **Constraints:** Android IME (minSdk 24), BYO OpenAI/Gemini-compatible providers, privacy-first (no sensitive logging/leaks), editor correctness across third-party apps.

## Current Position

- **Phase:** 1 — Privacy & Safety Controls
- **Status:** Not started
- **Next:** /gsd-plan-phase 1

Progress: `[░░░░░░░░░░] 0%`

## Performance Metrics (targets)

- No dictation/command availability in secure fields.
- No auth headers or user payloads logged by default.

## Accumulated Context

### Decisions

- v1 is BYO API key only (no accounts/billing/backend).
- Realtime insertion is capability-gated; composing text for partials.
- Enhancement replaces in place with 1-tap undo; fallback to raw on failure.
- Command mode uses best-effort selection, with clipboard fallback.

### Blockers

- None recorded.

### Notes / To Watch

- IME editor surfaces are unreliable; drop late events on focus changes.

## Session Continuity

- Keep planning artifacts in `.planning/` aligned with v1 requirements and phase mapping.
