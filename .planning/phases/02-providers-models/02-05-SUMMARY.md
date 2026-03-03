---
phase: 02-providers-models
plan: 05
subsystem: ui
tags: [providers, models, okhttp, datastore, gemini, openai]

# Dependency graph
requires:
  - phase: 02-providers-models
    provides: provider selections + base-URL routing (OpenAI STT/text, Gemini text)
provides:
  - Provider edit: manual Text/STT test actions with raw response dialog (redacted)
  - Provider edit: optional model list fetch/import (deduped)
  - Secret-safe verbose logging for Gemini (header auth) + query-param redaction
affects: [settings, providers, diagnostics, privacy]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - OkHttp HttpUrl-based endpoint derivation for provider diagnostics
    - UI-only raw response display with string redaction (no logs)

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt

key-decisions:
  - "Gemini auth for SmartFixer uses x-goog-api-key header (avoid key= query params)"
  - "Provider diagnostics display raw response in UI with redaction; never log bodies"

patterns-established:
  - "Provider diagnostics should derive endpoints from stored base URL (not stored full paths)"
  - "Any UI surface that shows raw HTTP data must redact potential secrets"

requirements-completed: [PROV-01, PROV-05, PROV-06]

# Metrics
duration: 6h 32m
completed: 2026-03-03
---

# Phase 02 Plan 05: Provider Test + Model Import Summary

**Provider diagnostics in Settings: manual Text/STT tests with redacted raw responses, plus optional model list import and secret-safe Gemini logging.**

## Performance

- **Duration:** 6h 32m
- **Started:** 2026-03-03T04:41:27Z
- **Completed:** 2026-03-03T11:13:21Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments
- Added provider-level manual Text/STT test actions (capability-aware, with force-model fallback) and raw response dialog.
- Added optional per-provider model fetch/import with persistence and deduping.
- Ensured verbose logging remains secret-safe for Gemini (prefer header auth; redact key= if present).

## task Commits

Each task was committed atomically:

1. **task 1: Add provider Test actions (Text + STT) with raw response UI** - `c7cb371` (feat)
2. **task 2: Add optional model fetch/import per provider (non-blocking)** - `1d1f402` (feat)
3. **task 3: Ensure verbose logging remains secret-safe when Gemini auth uses query params** - `0f10490` (fix)

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - Provider test UI, raw response dialog (redacted), model import action.
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` - Provider upsert helper for reuse by import/save.
- `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt` - Gemini requests use x-goog-api-key header (no key= in URL).
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` - Verbose log redaction for key= query params; header redaction list updated.

## Decisions Made
- Gemini Smart Fix auth now uses `x-goog-api-key` header to avoid leaking `key=...` in request line.
- Diagnostics raw response is UI-only and redacted; no response bodies are logged.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Build verification required JDK 17 runtime**
- **Found during:** task 1 (assembleDebug verification)
- **Issue:** JDK 21 caused Android JdkImageTransform/jlink failure; required JDK 17 to build.
- **Fix:** Used local Temurin JDK 17 for Gradle verification runs.
- **Files modified:** None
- **Verification:** `./android/gradlew -p android :app:assembleDebug` succeeds with JDK 17
- **Committed in:** N/A

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Verification environment fix only; no scope creep.

## Issues Encountered
- None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 2 complete: provider setup and diagnostics now support end-to-end OpenAI/Gemini troubleshooting from settings.

## Self-Check: PASSED
