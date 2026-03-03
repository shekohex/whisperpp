---
phase: 02-providers-models
plan: 04
subsystem: api
tags: [okhttp, datastore, providers, openai, gemini, ime]

# Dependency graph
requires:
  - phase: 02-providers-models
    provides: active STT/text selection keys + selector UI
provides:
  - Dictation uses active STT provider+model at runtime (no implicit first model)
  - Smart Fix uses active text provider+model with safe fallback when unset
  - Base-URL-derived routing for OpenAI-compatible STT/text and Gemini-compatible text
affects: [02-05-provider-tests, phase-03-dictation, phase-04-enhancement]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Derive operation endpoints from provider base URL using OkHttp HttpUrl builders"
    - "Respect provider authMode (API_KEY vs NO_AUTH) when building requests"

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt

key-decisions: []

patterns-established:
  - "Runtime request routing must use ACTIVE_* selection keys and base-URL-derived endpoints"

requirements-completed: [PROV-03, PROV-04, PROV-05, PROV-06]

# Metrics
duration: 3 min
completed: 2026-03-03
---

# Phase 2 Plan 04: Runtime wiring for selections + base-URL routing Summary

**Dictation and Smart Fix now use active provider/model selections, with OpenAI/Gemini endpoints derived from provider base URLs.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-03T04:16:15Z
- **Completed:** 2026-03-03T04:19:19Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments

- Wired IME runtime behavior to Phase 2 selection keys for dictation (STT) and enhancement (text).
- Derived OpenAI-compatible STT endpoint from base URL using `/audio/transcriptions` and authMode.
- Derived OpenAI-compatible text endpoint (`/chat/completions`) and Gemini-compatible text endpoint (`/models/{model}:generateContent`) from base URL with no-auth support.

## task Commits

Each task was committed atomically:

1. **task 1: Resolve selected STT/text providers+models in WhisperInputService** - `63f1256` (feat)
2. **task 2: Derive OpenAI-compatible STT endpoint from base URL in WhisperTranscriber** - `bb1803d` (feat)
3. **task 3: Derive OpenAI/Gemini text endpoints from base URL in SmartFixer (no-auth supported)** - `30ade41` (feat)

## Files Created/Modified

- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - Resolve active STT/text provider+model selections for dictation and Smart Fix.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` - Build STT transcription URL from provider base URL and respect authMode.
- `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt` - Build OpenAI/Gemini text transform URLs from base URL with authMode support and sanitized logging.

## Decisions Made

None - followed plan as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Gradle assembleDebug fails under JDK 21 due to `androidJdkImage` jlink transform; verification was run with the bundled Temurin JDK 17.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Runtime wiring is complete; ready for Phase 2 Plan 05 provider test actions and model import.

## Self-Check: PASSED

- FOUND: `.planning/phases/02-providers-models/02-04-SUMMARY.md`
- FOUND commits: `63f1256`, `bb1803d`, `30ade41`
