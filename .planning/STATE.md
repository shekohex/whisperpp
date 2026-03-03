# STATE: Whisper++

**Core value:** Speak anywhere; get accurate, context-appropriate text inserted instantly.

**Current Phase:** 03
**Current Phase Name:** Dictation
**Status:** Ready to execute
**Current Plan:** 2
**Total Plans in Phase:** 4

**Progress:** [████████░░] 80%

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 02-providers-models P01 | 20 min | 3 tasks | 8 files |
| Phase 02-providers-models P02 | 10 min | 2 tasks | 1 files |
| Phase 02-providers-models P03 | 16 min | 3 tasks | 5 files |
| Phase 02-providers-models P04 | 3 min | 3 tasks | 3 files |
| Phase 02-providers-models P05 | 6h 32m | 3 tasks | 4 files |
| Phase 03-dictation P01 | 5 min | 2 tasks | 3 files |

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
- Blocked explanation fallback uses a dedicated activity host when in-IME sheet rendering is unreliable.
- Privacy disclosures must be generated from live provider config (base URL + endpoint path) via a shared formatter.
- First-use disclosure gating is mode-specific (`dictation`, `enhancement`, `command`) and resettable from Privacy & Safety.
- Verbose diagnostics logging is user-togglable but constrained to redacted HEADERS logging only.
- [Phase 02-providers-models]: Kept JSON field name endpoint for compatibility; semantics are now base URL
- [Phase 02-providers-models]: Default auth mode is API_KEY except WHISPER_ASR which defaults to NO_AUTH
- [Phase 02]: Gemini Smart Fix uses x-goog-api-key header (no key= in URL)
- [Phase 02]: Provider diagnostics show raw response in UI with redaction; never log bodies
- [Phase 03-dictation]: Release from hold pauses recording (no auto-transcribe)

### Blockers

None

### Notes / To Watch

- IME editor surfaces are unreliable; drop late events on focus changes.

## Session Continuity

**Last session:** 2026-03-03T19:00:30.698Z
**Stopped At:** Completed 03-dictation-01-PLAN.md
**Resume file:** None
