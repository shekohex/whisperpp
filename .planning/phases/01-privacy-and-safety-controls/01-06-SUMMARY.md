---
phase: 01-privacy-and-safety-controls
plan: 06
subsystem: ui
tags: [privacy, safety, ime, compose, bottom-sheet]

# Dependency graph
requires:
  - phase: 01-05
    provides: blocked explanation UX path in IME + Privacy & Safety destination wiring
provides:
  - IME-safe blocked explanation UI (expanded sheet + dedicated-activity fallback)
  - Deterministic blocked-explanation copy spec (secure-field vs app-policy)
  - Regression tests for copy selection and constrained-host visible content
affects: [02-providers-and-models, 03-dictation, 04-prompts-profiles-enhancement, 05-command-mode-and-presets]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Explicit ModalBottomSheet state forcing expanded + zero insets in IME host
    - Shared blocked-explanation content composable reused across presentation containers

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationCopySpec.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationActivity.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt
    - android/app/src/main/res/values/strings.xml
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/test/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationCopySpecTest.kt
    - android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationContentUiTest.kt

key-decisions:
  - "Use a dedicated activity fallback for blocked explanation when IME host sheet rendering is unreliable"
  - "Centralize blocked explanation copy selection in a single copy spec used by both secure-field and app-policy flows"

patterns-established:
  - "Blocked explanation UI text is selected via resource-id copy spec instead of hardcoded strings"
  - "IME UI fallbacks should reuse the exact same content composable to prevent drift"

requirements-completed:
  - PRIV-01
  - PRIV-04

# Metrics
duration: 89h 19m
completed: 2026-03-02
---

# Phase 01 Plan 06: IME-safe blocked explanation (secure-field + app-policy) Summary

**Tapping a locked main action now reliably shows a non-empty blocked explanation for both secure-field and app-policy blocks, with an IME-safe fallback when sheets can’t render correctly.**

## Performance

- **Duration:** 89h 19m
- **Started:** 2026-02-26T21:37:43Z
- **Completed:** 2026-03-02T14:57:06Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- Forced blocked explanation presentation to expanded state and removed IME insets so content doesn’t appear empty.
- Added deterministic copy selection for secure-field vs app-policy with per-reason mapping to string resources.
- Added unit + instrumentation regression coverage for copy selection and constrained-host visibility.

## Task Commits

Each task was committed atomically:

1. **Task 1: Make blocked explanation sheet IME-safe and always expanded** - `8b65653` (feat)
2. **Task 2: Add regression tests (copy selection + constrained-host visible content)** - `3f3b333` (test)
3. **Task 3: checkpoint:human-verify** - approved (no code commit)

Additional fix during checkpoint cycle:

- **UAT fix: detach blocked explanation into full-height sheet** - `3ffb039` (fix)

**Plan metadata:** pending

## Files Created/Modified

- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationCopySpec.kt` - Resource-id based copy spec for blocked explanation content selection.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt` - Shared blocked explanation content + IME-safe presentation logic.
- `android/app/src/main/res/values/strings.xml` - App-policy blocked explanation strings.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationActivity.kt` - Dedicated activity host for blocked explanation fallback.
- `android/app/src/main/AndroidManifest.xml` - Registers blocked explanation activity.
- `android/app/src/test/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationCopySpecTest.kt` - Unit coverage for copy selection mapping.
- `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationContentUiTest.kt` - Constrained-host UI visibility assertions.

## Decisions Made

- Used a dedicated activity fallback rather than relying solely on in-IME `ModalBottomSheet`/`Popup` presentation.
- Kept “Don’t show again” limited to secure-field flow only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced in-IME popup fallback with dedicated-activity fallback**
- **Found during:** Task 3 (checkpoint:human-verify)
- **Issue:** IME-hosted bottom sheet/popup presentation could still render as effectively empty/clipped in real-world IME constraints.
- **Fix:** Added a dedicated activity host to display the exact same blocked explanation content as a full-height sheet.
- **Files modified:** android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt, android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/BlockedExplanationActivity.kt, android/app/src/main/AndroidManifest.xml
- **Verification:** `./gradlew assembleDebug testDebugUnitTest :app:assembleDebugAndroidTest` (android/ workdir)
- **Committed in:** 3ffb039

**2. [Rule 3 - Blocking] Verification command required Android project working directory**
- **Found during:** Final verification
- **Issue:** Running Gradle verification from repo root failed because Gradle root is `android/`.
- **Fix:** Executed verification in `android/` workdir using `./gradlew ...`.
- **Files modified:** None (execution-only adjustment)
- **Verification:** `./gradlew assembleDebug testDebugUnitTest :app:assembleDebugAndroidTest`
- **Committed in:** N/A

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** All fixes were directly required to close the reported “empty blocked sheet” UAT gaps; no scope expansion beyond the blocked explanation UX.

## Authentication Gates

None.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 01 UAT gaps for blocked explanation visibility are addressed; Phase 02 can proceed.
- Follow-up consideration (user note): evaluate whether an in-IME alert dialog style presentation could replace the dedicated activity while remaining reliable in constrained IME hosts.

---
*Phase: 01-privacy-and-safety-controls*
*Completed: 2026-03-02*

## Self-Check: PASSED

- FOUND: .planning/phases/01-privacy-and-safety-controls/01-06-SUMMARY.md
- FOUND commits: 8b65653, 3f3b333, 3ffb039
