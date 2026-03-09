---
phase: 07-local-analytics-dashboard
plan: 02
subsystem: data
tags: [android, kotlin, ime, analytics, datastore, junit4]
requires:
  - phase: 07-local-analytics-dashboard
    provides: local analytics repository contracts and seven-day persistence from plan 01
  - phase: 03-dictation
    provides: dictation session ids, lifecycle callbacks, and raw/enhanced insertion semantics
provides:
  - exactly-once dictation analytics outcome helper keyed by IME dictation session id
  - live IME runtime writes for completed vs cancelled dictation sessions
  - fallback-safe analytics recording across enhancement skips, failures, and streaming cached sends
affects: [dictation-runtime, analytics-store, settings-analytics-ui]
tech-stack:
  added: []
  patterns:
    - session-scoped analytics helper finalized once per dictation lifecycle
    - runtime analytics writes delayed until final inserted text is known
key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/analytics/DictationAnalyticsSession.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/analytics/DictationAnalyticsSessionTest.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
key-decisions:
  - "WhisperInputService now keys analytics on DictationController sessionId so retries, duplicate callbacks, and streaming terminal paths collapse into one outcome write."
  - "Completed analytics are emitted only after the raw insert and enhancement outcome are known, with Smart Fix cancellation after raw insertion treated as a raw completion instead of a cancelled run."
patterns-established:
  - "IME analytics wiring: arm a per-run helper at recording start, then finalize through raw-only, enhanced, or cancelled terminal branches."
  - "Streaming cached-send path must finalize analytics from the stored realtime transcript even when no transcription callback fires."
requirements-completed: [STATS-01]
duration: 8 min
completed: 2026-03-09
---

# Phase 07 Plan 02: Runtime Dictation Analytics Summary

**Exactly-once IME analytics writes for completed and cancelled dictation runs, including enhancement fallback and streaming cached-send paths.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-09T22:35:09Z
- **Completed:** 2026-03-09T22:42:49Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Added a framework-free `DictationAnalyticsSession` helper that emits one terminal completed or cancelled payload per dictation run.
- Covered exactly-once completion, replacement-text finalization, and cancellation behavior with targeted JVM unit tests.
- Wired `WhisperInputService` into `AnalyticsRepository` so raw-only sends, enhancement success/failure, streaming cached sends, and confirmed cancellations update local analytics once.

## Task Commits

Each task was committed atomically:

1. **Task 1: add an exactly-once dictation analytics outcome helper** - `21041ac` (test), `cee651a` (feat)
2. **Task 2: wire WhisperInputService to record terminal dictation analytics** - `12e9a96` (feat)

**Plan metadata:** Pending docs/state commit.

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/analytics/DictationAnalyticsSession.kt` - session-scoped helper for exactly-once completed vs cancelled analytics outcomes.
- `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/DictationAnalyticsSessionTest.kt` - regression coverage for duplicate finalize/cancel behavior and enhanced final text handling.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - IME lifecycle wiring from dictation terminal branches into analytics repository writes.

## Decisions Made
- Reused `DictationController.SendToken.sessionId` as the analytics key so service callbacks can ignore duplicate or stale terminal events without extra global state.
- Recorded completed analytics only after the service knows the user-visible final inserted text, defaulting back to raw text for enhancement skips, failures, focus-safe fallback, and cached realtime sends.
- Treated Smart Fix cancellation after raw insertion as a completed raw dictation, avoiding false cancelled-session metrics once text is already in the editor.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Initial RED test run used the repo root instead of `android/` for Gradle; reran from the wrapper directory and continued normally.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Settings analytics UI can now consume live completed/cancelled dictation metrics from real IME traffic instead of repository-only test data.
- Phase 07 plan 03 can build dashboard surfaces on top of runtime-populated analytics without adding more dictation hooks.

---
*Phase: 07-local-analytics-dashboard*
*Completed: 2026-03-09*

## Self-Check: PASSED
