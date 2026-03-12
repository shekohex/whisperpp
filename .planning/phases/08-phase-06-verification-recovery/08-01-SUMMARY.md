---
phase: 08-phase-06-verification-recovery
plan: 01
subsystem: ui
tags: [android, kotlin, compose, androidtest, settings, backup-restore]
requires:
  - phase: 06-settings-ux-import-export
    provides: shipped settings home plus backup/restore preview and completion flows
provides:
  - stable test tags for phase-06 settings home and backup/restore surfaces
  - focused Compose androidTest coverage for settings-home backup state, import preview, and restore summary
affects: [08-02 verification artifact, requirement traceability, phase-06 audit coverage]
tech-stack:
  added: []
  patterns: [stable Compose testTag seams, fixture-driven same-package androidTests, savedStateHandle-backed nav state seeding]
key-files:
  created:
    - android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/Phase06VerificationUiTest.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/BackupRestoreScreen.kt
key-decisions:
  - "Use explicit root test tags for shipped settings/import-export surfaces instead of brittle copy-only selectors."
  - "Expose ImportPreviewCard and RestoreSummaryCard as internal composables so androidTests can render deterministic fixtures without touching production state wiring."
patterns-established:
  - "Settings verification pattern: seed NavHost savedStateHandle keys to exercise home-card status handoff deterministically."
  - "Backup verification pattern: render preview and summary cards directly with typed ImportAnalysis and RestoreSummary fixtures."
requirements-completed: [UI-01, SET-01, SET-02]
duration: 14 min
completed: 2026-03-12
---

# Phase 08 Plan 01: Stable phase-06 settings/import-export UI proof Summary

**Deterministic Compose selectors and focused instrumentation fixtures for settings-home backup state, import preview, and restore completion UI.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-03-12T21:30:47Z
- **Completed:** 2026-03-12T21:44:28Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Added stable test tags for the settings-home repair banner, backup overview card, import preview root, and restore summary root.
- Opened backup preview and restore summary composables just enough for same-package androidTests while keeping `BackupRestoreScreen` as the production wrapper.
- Added one focused `Phase06VerificationUiTest` class covering settings-home backup status + repair state, merge preview warnings/skips/categories, and restore summary repair actions.

## task Commits

Each task was committed atomically:

1. **task 1: expose stable phase-06 verification seams in settings UI** - `49476a9` (feat)
2. **task 2: add a focused compose androidTest for phase-06 settings verification** - `f193451` (test)

**Plan metadata:** pending

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt` - tagged the setup banner root and backup/restore overview card root.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/BackupRestoreScreen.kt` - tagged preview/summary roots and exposed preview/summary composables to same-package tests.
- `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/Phase06VerificationUiTest.kt` - verifies settings-home saved-state handoff plus preview/summary fixture rendering.

## Decisions Made
- Used explicit root tags as the stable verification contract for shipped settings/import-export surfaces.
- Kept preview/summary verification fixture-based instead of attempting SAF/file-picker automation in instrumentation.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- `:app:assembleDebug` passed.
- `:app:compileDebugAndroidTestKotlin` passed, confirming the new test class compiles.
- `connectedDebugAndroidTest` could not run in this environment: first no connected devices, then `phase7-api34-default` failed with a broken AVD system-image path, and `phase7-api36` failed because `/dev/kvm` is unavailable so x86_64 emulation cannot start.

## Deferred Issues
- Full execution of `./android/gradlew -p android connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.shekohex.whisperpp.ui.settings.Phase06VerificationUiTest` still needs a real device or KVM-capable emulator host.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Stable UI seams and focused test coverage are in place for phase-06 recovery documentation.
- Phase 08 plan 02 can reference these selectors and tests, but final instrumentation proof still needs a device-enabled environment.

## Self-Check: PASSED
