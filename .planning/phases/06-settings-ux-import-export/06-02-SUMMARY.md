---
phase: 06-settings-ux-import-export
plan: 02
subsystem: data
tags: [android, kotlin, datastore, backup, restore, merge, secrets, gson]
requires:
  - phase: 06-01
    provides: encrypted backup envelope, stable category manifest, export snapshot payloads
  - phase: 02-providers-models
    provides: provider/model persistence and selection validation helpers
provides:
  - encrypted backup import analysis previews for overwrite and merge modes
  - category-scoped restore apply pipeline with partial restore reporting
  - post-restore selection cleanup and repair checklist generation for missing credentials
affects: [06-04-backup-restore-ui, settings-import-export, restore-preview]
tech-stack:
  added: []
  patterns: [analyze-before-apply restore flow, category-scoped restore semantics, credential source/sink separation]
key-files:
  created:
    - android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupImportAnalysisTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupApplyTest.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupModels.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupRepository.kt
key-decisions:
  - "Import always produces a typed analysis object first so UI can preview overwrite or merge without mutating DataStore."
  - "Merge semantics are explicit per category, with imported values winning conflicts and category inclusion controlled at apply time."
  - "Provider credentials restore through a dedicated sink and post-restore validation clears broken selections while emitting repair checklist entries."
patterns-established:
  - "Restore Analysis: decrypt -> parse categories independently -> warn/skip invalid pieces -> build preview payload."
  - "Restore Apply: apply selected categories -> validate selections -> clear unusable state -> return typed repair summary."
requirements-completed: [SET-02]
duration: 24 min
completed: 2026-03-09
---

# Phase 06 Plan 02: Import Restore Semantics Summary

**Encrypted backup restore previews with merge/overwrite category semantics, partial invalid-item recovery, and post-restore repair guidance for broken selections and credentials.**

## Performance

- **Duration:** 24 min
- **Started:** 2026-03-09T18:54:05Z
- **Completed:** 2026-03-09T19:18:45Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Added a non-destructive import analysis path that decrypts backups, parses categories independently, and previews overwrite vs merge outcomes.
- Implemented category-scoped restore apply behavior with imported-values-win merge rules and merge-time category selection support.
- Added post-restore validation that clears invalid selections, restores credentials through a dedicated sink, and returns repair checklist entries for unusable providers.

## Task Commits

Each task was committed atomically:

1. **Task 1: Build import analysis for overwrite/merge, category selection, cross-version warnings, and skipped items** - `a93b6ac` (feat)
2. **Task 2: Implement category-scoped apply functions, selection cleanup, and repair checklist generation** - `6e55b9b` (feat)

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupModels.kt` - Restore analysis/apply summary models and repair reporting types.
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupRepository.kt` - Backup decrypt/analyze/apply pipeline with partial parsing, merge semantics, and post-restore validation.
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupImportAnalysisTest.kt` - Coverage for merge vs overwrite previews, warnings, and skipped-item handling.
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupApplyTest.kt` - Coverage for apply semantics, credential restoration, selection cleanup, and repair checklist output.

## Decisions Made
- Analysis and apply were split into separate repository APIs so the UI can always preview restore effects before any destructive write.
- Merge rules are encoded per category instead of raw preference-key merging, keeping imported providers, mappings, profiles, and policies deterministic.
- Credential writes happen only through a dedicated sink while selection validation and missing-key detection emit explicit repair guidance instead of leaving silent broken state.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Ready for 06-04 UI wiring against `ImportAnalysis` previews and `RestoreSummary` completion data.
- Backup restore flow now exposes the category, warning, skipped-item, and repair data needed for the bottom-sheet preview/confirm UX.

## Self-Check: PASSED
