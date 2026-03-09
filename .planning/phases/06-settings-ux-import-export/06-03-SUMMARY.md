---
phase: 06-settings-ux-import-export
plan: 03
subsystem: ui
tags: [android, compose, material3, settings, navigation]
requires:
  - phase: 05-command-mode-presets
    provides: existing provider, prompt, preset, keyboard, and privacy settings areas reorganized here
provides:
  - grouped settings home with setup-critical banner and concise status cards
  - route-aware contextual help sheets across main and nested settings screens
  - centralized nested settings start-route resolution for settings deep-links
affects: [settings-home, settings-navigation, deep-links, import-export-ui]
tech-stack:
  added: []
  patterns:
    - grouped settings overview cards
    - route-aware help bottom sheets
key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHelp.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt
    - android/app/src/main/res/values/strings.xml
key-decisions:
  - "Settings home is a grouped overview surface with setup-critical items first and maintenance/update controls visually secondary."
  - "Contextual help is served from a shared route-aware bottom sheet reused across home and nested settings screens."
  - "Settings deep-links resolve through a centralized route whitelist so nested destinations work and unknown routes fall back safely."
patterns-established:
  - "Grouped settings cards: each top-level card exposes one concise status line before drilling into detail screens."
  - "Settings help action: top app bars reuse a shared route-to-tips mapping instead of screen-local TODO affordances."
requirements-completed: [UI-01, SET-01]
duration: 6 min
completed: 2026-03-09
---

# Phase 6 Plan 3: Settings Home IA & Help Summary

**Grouped Material 3 settings home with a persistent setup banner, concise status cards, and route-aware help sheets across settings areas.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-09T17:44:18Z
- **Completed:** 2026-03-09T17:49:41Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Extracted the settings home into a dedicated grouped overview screen with setup-critical issues surfaced first.
- Added concise status summaries for core settings areas and kept backup/update affordances visually secondary.
- Replaced no-op help affordances with shared contextual help sheets and expanded start-route handling for nested settings destinations.

## task Commits

Each task was committed atomically:

1. **task 1: Extract a grouped Material 3 settings home with setup-critical banner and concise status cards** - `97f6958` (feat)
2. **task 2: Add route-aware help affordances and clean nested start-route handling** - `a667cf8` (feat)

**Plan metadata:** Pending docs/state commit.

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt` - grouped settings home, setup banner, overview cards, and backup/update placement
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHelp.kt` - shared route-aware help content and bottom-sheet action
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - home-screen extraction, shared help wiring, inline guidance text, and centralized route validation
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt` - privacy help action and clearer reset guidance
- `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` - nested settings start-route resolution via shared route validation
- `android/app/src/main/res/values/strings.xml` - help action accessibility string

## Decisions Made
- Kept settings home as a focused overview surface instead of exposing every editor directly at the top level.
- Used a single route-aware help mapping so contextual tips stay consistent across home and nested screens.
- Treated nested settings destinations as valid app entry routes while preserving safe fallback to the main settings screen.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Ready for 06-04 backup/restore UI flow to plug into the grouped home and nested settings navigation.
- Shared help plumbing and nested route handling are in place for new import/export detail screens.

---
*Phase: 06-settings-ux-import-export*
*Completed: 2026-03-09*

## Self-Check: PASSED
