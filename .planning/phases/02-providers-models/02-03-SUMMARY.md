---
phase: 02-providers-models
plan: 03
subsystem: ui
tags: [datastore, preferences, jetpack-compose, settings]

# Dependency graph
requires:
  - phase: 02-providers-models
    provides: provider + model schema v2 and edit UX rules
provides:
  - Active STT/text provider+model selection keys (plus optional command override)
  - Selector UI (two-step provider → model pickers) with model-kind compatibility filtering
  - Setup-needed banner + invalid-selection clearing + deletion blocking when selected
affects: [02-04-runtime-wiring, settings, privacy]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Selection keys persisted in Preferences DataStore by providerId+modelId"
    - "Selection validation helper returns keys-to-clear + effective selections with inheritance"

key-files:
  created:
    - android/app/src/test/java/com/github/shekohex/whisperpp/data/ProviderSelectionsMigrationTest.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt

key-decisions:
  - "Command mode inherits enhancement selection by keeping override keys unset (explicit Inherit option in UI)."
  - "Invalid selections are cleared to none and surfaced via setup-needed banner; no auto-fallback to another provider/model."

patterns-established:
  - "Settings-level validation clears bad IDs and drives setup-needed UI state"

requirements-completed: [PROV-03, PROV-04]

# Metrics
duration: 16 min
completed: 2026-03-03
---

# Phase 2 Plan 03: Active selections UX Summary

**Explicit STT + text provider/model selectors (two-step pickers) with optional command override inheritance and immediate setup-needed banner.**

## Performance

- **Duration:** 16 min
- **Started:** 2026-03-03T03:48:07Z
- **Completed:** 2026-03-03T04:04:26Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Added v2 selection keys for active STT/text provider+model and optional command override, with a one-time migration from legacy keys.
- Implemented a dedicated selections screen with two-step provider→model pickers and model-kind compatibility filtering (STT/TEXT + MULTIMODAL), including clear-to-none.
- Added selection validation (clear invalid IDs), setup-needed banner with direct CTA to selectors, updated Privacy & Safety disclosures to reflect effective selections, and blocked provider/model deletion when referenced.

## task Commits

Each task was committed atomically:

1. **task 1: Add DataStore keys + migration + tests** - `e619f4e` (feat)
2. **task 2: Build selectors UI (two-step pickers)** - `a647ae9` (feat)
3. **task 3: Setup-needed banner + validation + Privacy & Safety + deletion blocking** - `b84a1ec` (feat)

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` - New selection preference keys.
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` - One-time migration + selection validation helper.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - Selectors route/UI, setup-needed banner, and deletion blocking dialogs.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt` - Disclosures reflect effective selections (incl. command inheritance).
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/ProviderSelectionsMigrationTest.kt` - Migration + validation unit tests.

## Decisions Made
None - followed plan as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Gradle build failed under JDK 21 (`androidJdkImage` jlink transform). Verification was run with the bundled Temurin JDK 17.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Selections UX and persistence are in place; ready for Phase 2 Plan 04 runtime wiring to use these selections for STT/text requests.

## Self-Check: PASSED

- FOUND: `.planning/phases/02-providers-models/02-03-SUMMARY.md`
- FOUND commits: `e619f4e`, `a647ae9`, `b84a1ec`
