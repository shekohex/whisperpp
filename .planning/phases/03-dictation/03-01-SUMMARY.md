---
phase: 03-dictation
plan: 01
subsystem: ui
tags: [android, ime, compose, dictation, gestures]

# Dependency graph
requires:
  - phase: 02-providers-models
    provides: STT provider/model selection and runtime secrets injection
provides:
  - Dictation bar UI states for hold-to-talk, lock, pause, resume, send, cancel
affects: [03-dictation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - State-driven Compose UI for IME dictation surface
    - Gesture thresholds for lock/cancel with haptic feedback

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/keyboard/KeyboardState.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt

key-decisions:
  - "Release from hold pauses recording (no auto-transcribe)"

patterns-established:
  - "Paused surface uses explicit actions: trash/resume/send"

requirements-completed: [DICT-01]

# Metrics
duration: 5 min
completed: 2026-03-03
---

# Phase 3 Plan 01: Dictation Bar UI Flow Summary

**Compose dictation bar now matches WhatsApp/Telegram-style hold→pause, swipe-up lock, longer cancel swipe, and explicit paused controls (trash/resume/send).**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-03T18:51:27Z
- **Completed:** 2026-03-03T18:57:16Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Extended `KeyboardState` with helper flags to simplify UI rendering.
- Updated dictation bar gestures (hold, lock, discard) with a longer discard swipe threshold.
- Implemented paused/locked control surface: trash (confirm cancel), resume, send; and ensured release pauses without auto-transcribing.

## task Commits

Each task was committed atomically:

1. **task 1: Extend KeyboardState for paused+locked control surface** - `94fd938` (feat)
2. **task 2: Update KeyboardScreen gestures + paused/locked controls to match Phase 3 decisions** - `ec5b512` (feat)

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/keyboard/KeyboardState.kt` - Add helper flags for recording/paused/locked UI decisions.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt` - Pause/lock control surface + gesture threshold updates.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - Pause now remains paused (no auto-transcribe on release).

## Decisions Made
- Release from hold pauses (no auto-transcribe); send is explicit via the paused control surface.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Release from hold was auto-transcribing instead of pausing**
- **Found during:** task 2 (Update KeyboardScreen gestures + paused/locked controls)
- **Issue:** Releasing from hold triggered pause → auto-transcribe via `AUTO_TRANSCRIBE_ON_PAUSE`, conflicting with locked Phase 3 mic flow (pause on release, no auto-send).
- **Fix:** Removed auto-transcribe-on-pause path; pause now always stays paused until explicit Send.
- **Files modified:** `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- **Verification:** `./android/gradlew -p android :app:assembleDebug`
- **Committed in:** `ec5b512`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Required for correctness of the specified hold→pause flow. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- UI surface now exposes the intended dictation control affordances; ready for service-side dictation session wiring and composing text insertion.

## Self-Check: PASSED
