---
phase: 01-privacy-and-safety-controls
plan: 02
subsystem: ui
tags: [android, ime, privacy, secure-field, compose, datastore]

# Dependency graph
requires:
  - phase: 01-01
    provides: Cancelable and safe networking primitives for transcription and Smart Fix
provides:
  - Strict secure-field detection utility with structured reasons
  - Central external-send gate in IME for dictation and Smart Fix paths
  - Disabled locked action UI with secure-field explanation sheet and do-not-show-again preference
affects: [dictation, smart-fix, command-mode, privacy-controls, settings]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Single IME gate function that blocks external sending by current EditorInfo
    - Focus-change secure stop path that cancels recorder, transcription, and Smart Fix
    - On-demand blocked-action explanation sheet with persistent dismissal preference

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecureFieldDetector.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
    - android/app/src/main/res/values/strings.xml

key-decisions:
  - "Gate all external-send entry points through one secure-field decision function in WhisperInputService"
  - "Show secure-field explanation only on tap of disabled main action (no proactive banner)"
  - "Route settings deep-link requests via MainActivity intent extra with fallback to Settings main"

patterns-established:
  - "SecureFieldDetector.detect(EditorInfo?) returns isSecure + reason and is reused by IME gate and UI"
  - "Blocked main action remains visible with lock icon while interaction is constrained to explanation path"

requirements-completed:
  - PRIV-01

# Metrics
duration: 10 min
completed: 2026-02-26
---

# Phase 01 Plan 02: Secure-field detection + IME send gate + disabled UI with explanation Summary

**Secure-field-aware IME now blocks dictation/Smart Fix sends, immediately stops active external work on secure focus, and explains blocked actions through a lock-state bottom sheet.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-02-26T15:14:40Z
- **Completed:** 2026-02-26T15:25:02Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Added strict secure-field detection for password-like fields, OTP-like hints, and no-personalized-learning privacy flags.
- Enforced a single external-send gate in `WhisperInputService` across mic start, transcription start, and Smart Fix send paths.
- Implemented blocked keyboard UX: lock-state main action, explanation sheet with reason, settings link request, and global don’t-show-again preference.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add strict secure-field detection helper** - `18f8507` (feat)
2. **Task 2: Enforce secure-field gate in IME + disabled UI with details sheet** - `2276f0e` (feat)

**Plan metadata:** to be captured in docs commit for this plan.

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecureFieldDetector.kt` - strict secure-field detector with structured reason output.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - external-send gate, secure-focus immediate stop, blocked action wiring, and don’t-show-again persistence.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt` - blocked lock-state main action and secure-field explanation sheet flow.
- `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` - new secure explanation preference key and settings destination extra routing.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - start route handling with fallback to main settings.
- `android/app/src/main/res/values/strings.xml` - secure-field toast and explanation sheet copy.

## Decisions Made
- Secure-field blocking is determined centrally in IME and reflected into UI state rather than duplicating detection logic in Compose UI.
- Entering a secure field during active external work immediately resets keyboard state to Ready and cancels in-flight work.
- Settings destination requests are string-based intent extras; unrecognized destinations safely fall back to Settings main.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Ensured Smart Fix cancellation is included in shared cancel path**
- **Found during:** Task 2 (IME external-send gate enforcement)
- **Issue:** `cancelTranscription()` only stopped transcription call; Smart Fix cancellation was not guaranteed in that shared path.
- **Fix:** Added `smartFixer.cancel()` in cancel path and service teardown paths.
- **Files modified:** android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
- **Verification:** `JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./gradlew :app:assembleDebug`
- **Committed in:** 2276f0e (part of Task 2 commit)

**2. [Rule 3 - Blocking] Adjusted verification command to actual Gradle project root**
- **Found during:** Task 1 verification
- **Issue:** Plan verification command from repository root failed because Gradle root is `android/`.
- **Fix:** Ran equivalent verification from `android/` using `./gradlew :app:assembleDebug`.
- **Files modified:** None
- **Verification:** Build succeeded from correct Gradle root.
- **Committed in:** N/A (execution-only adjustment)

**3. [Rule 3 - Blocking] Added Material3 experimental opt-in for ModalBottomSheet**
- **Found during:** Task 2 verification
- **Issue:** Build failed due usage of experimental Material3 sheet API without opt-in.
- **Fix:** Added `@OptIn(ExperimentalMaterial3Api::class)` to `KeyboardScreen`.
- **Files modified:** android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt
- **Verification:** `JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./gradlew :app:assembleDebug`
- **Committed in:** 2276f0e (part of Task 2 commit)

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** All fixes were required to keep secure-send behavior correct and keep verification green.

## Authentication Gates

None.

## Issues Encountered
- None beyond auto-fixed deviations.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Ready for `01-03-PLAN.md`.
- No blockers carried forward.

---
*Phase: 01-privacy-and-safety-controls*
*Completed: 2026-02-26*
