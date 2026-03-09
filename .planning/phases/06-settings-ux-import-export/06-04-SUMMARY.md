---
phase: 06-settings-ux-import-export
plan: 04
subsystem: ui
tags: [android, compose, material3, backup, restore, saf, import-export]
requires:
  - phase: 06-02
    provides: import analysis, merge and overwrite apply semantics, restore summaries
  - phase: 06-03
    provides: grouped settings home, nested settings routing, contextual help sheets
provides:
  - dedicated backup and restore settings destination with password-gated encrypted export
  - restore flow with file pick, overwrite or merge choice, password gate, preview, and explicit apply
  - post-restore summary, skipped-item reporting, repair CTAs, and settings-home status integration
affects: [settings-home, backup-restore-ui, restore-preview, repair-guidance]
tech-stack:
  added: []
  patterns:
    - nested backup restore settings route
    - saved-state home status handoff after export and restore
    - category-level restore preview with merge inclusion chips
key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/BackupRestoreScreen.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHelp.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
key-decisions:
  - "Backup and restore is a dedicated nested settings screen, not two loose actions on the home screen."
  - "Restore status and repair counts flow back to settings home through savedStateHandle so users stay inside settings after export or restore."
  - "Repair checklist CTAs route credential fixes to Providers and selection fixes to Provider selections."
patterns-established:
  - "Backup route: export and import share one Material 3 screen with inline notices, dialogs, and restore summaries."
  - "Restore confirmation: file pick -> mode sheet -> password gate -> preview -> confirm apply."
requirements-completed: [UI-01, SET-01, SET-02]
duration: 21 min
completed: 2026-03-09
---

# Phase 06 Plan 04: Backup & Restore UI Summary

**Password-encrypted backup export plus in-settings restore preview, merge or overwrite choice, skipped-item reporting, and repair guidance.**

## Performance

- **Duration:** 21 min
- **Started:** 2026-03-09T19:24:01Z
- **Completed:** 2026-03-09T19:45:31Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Added a dedicated Backup & restore destination with contextual help, explicit category disclosure, sensitive-content warning, and password-confirmed encrypted export.
- Replaced loose home-row import/export actions with a nested route and a concise home status line that reflects recent export or restore state.
- Implemented restore flow UI for file pick, overwrite or merge choice, password-gated preview, explicit confirmation, completion summary, skipped-item reporting, and repair CTAs.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add the dedicated Backup & restore screen and password-protected export flow** - `f993a34` (feat)
2. **Task 2: Wire the full import flow with mode sheet, password gate, preview, category toggles, and completion report** - `0c458aa` (feat)

**Plan metadata:** Pending docs/state commit.

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/BackupRestoreScreen.kt` - backup screen with encrypted export, restore mode sheet, password gate, preview, completion summary, and repair actions.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt` - nested backup entry card plus post-restore status and repair attention banner wiring.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHelp.kt` - backup-specific contextual help guidance.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - nested backup route registration and start-route whitelist update.

## Decisions Made
- Kept backup and restore in one focused settings destination so export, preview, and completion reporting stay together.
- Reflected restore completion back on the grouped home screen through saved-state status instead of bouncing users to another screen.
- Mapped repair actions to existing settings areas instead of creating new repair-only surfaces.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Restored missing `Uri` import after home-screen refactor**
- **Found during:** Task 1 (dedicated backup screen wiring)
- **Issue:** `SettingsHomeScreen` still used `Uri.fromParts(...)` for app settings navigation after legacy import/export code removal, but the import had been removed, breaking compilation.
- **Fix:** Re-added the required `android.net.Uri` import before rerunning the build.
- **Files modified:** `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt`
- **Verification:** `./android/gradlew -p android :app:assembleDebug`
- **Committed in:** `f993a34` (part of task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** No scope change. Fix was required to keep the new settings wiring compiling.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 6 is complete: settings home, encrypted export, restore preview, and repair guidance are all wired into the shipped settings flow.
- Phase 7 can build on the grouped settings home without needing more import/export infrastructure.

## Self-Check: PASSED
