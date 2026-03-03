---
phase: 03-dictation
plan: 02
subsystem: dictation
tags: [ime, dictation, focus, language]

requires:
  - phase: 03-dictation
    provides: Dictation bar UI flow + mic hold/lock/pause states
provides:
  - FocusKey-based focus instance identity and session-tokened dictation controller
  - IME dictation flow: pause on release, explicit send, cancel blocks late inserts, auto-pause on focus/lifecycle
  - Language-aware STT request builder with unit tests
affects: [dictation, streaming, undo]

tech-stack:
  added: []
  patterns:
    - Session token + focus key guard for any late insertion callbacks
    - Controller-driven IME dictation state transitions

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/dictation/FocusKey.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/WhisperTranscriberLanguageTest.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt

key-decisions:
  - "Focus safety uses focusInstanceId incremented on onStartInput and onStartInputView"
  - "Non-streaming mode inserts only on explicit send (no composing/partials)"

patterns-established:
  - "Controller owns sessionId and validates focus before any insertion"

requirements-completed: [DICT-01, DICT-03, DICT-04, DICT-05, DICT-07]

duration: 22 min
completed: 2026-03-03
---

# Phase 03 Plan 02: Dictation correctness controller + focus safety + language

**Session-tokened dictation orchestration with focus-instance guards, explicit send-only finalization, and language-aware STT requests.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-03-03T19:04:27Z
- **Completed:** 2026-03-03T19:26:53Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Added `FocusKey` + `DictationController` with session token and focus-safety gates (DICT-07).
- Rewired IME dictation flow to pause on mic release, explicit send to finalize, cancel blocks late inserts, and lifecycle/focus auto-pause (DICT-03/04/07).
- Applied selected dictation language to STT requests with unit tests (DICT-05).

## task Commits

Each task was committed atomically:

1. **task 1: Add FocusKey + DictationController with session token + focus-safety gates** - `bf21846` (feat)
2. **task 2: Rewire WhisperInputService dictation flow to pause on release, explicit send, and auto-pause on lifecycle/focus changes** - `4a7203b` (feat)
3. **task 3: Apply dictation language selection to STT requests + add unit tests** - `1cd12eb` (feat)

**Plan metadata:** _pending_

## Self-Check: PASSED

## Files Created/Modified

- `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/FocusKey.kt` - Focus instance identity (packageName/inputType/fieldId + focusInstanceId).
- `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt` - Session-tokened dictation orchestration + insertion guards.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - Controller-driven dictation wiring + focusInstanceId hooks.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` - Language-aware request builder.
- `android/app/src/test/java/com/github/shekohex/whisperpp/WhisperTranscriberLanguageTest.kt` - Tests for language propagation.

## Decisions Made

- None - followed plan as specified.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Stop microphone capture while paused**
- **Found during:** task 2 (pause-on-release wiring)
- **Issue:** `RecorderManager.pause()` only slept write loop; `AudioRecord` stayed capturing.
- **Fix:** Stop/start `AudioRecord` on pause/resume.
- **Files modified:** android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt
- **Verification:** `./android/gradlew -p android :app:assembleDebug` and `./android/gradlew -p android testDebugUnitTest`
- **Committed in:** `b466c0e`

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Required for privacy/battery correctness; no scope creep.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Dictation sessions are now focus-safe and send-finalized for non-streaming.
- Ready for streaming partials and undo stack work in subsequent plans.

---
*Phase: 03-dictation*
*Completed: 2026-03-03*
