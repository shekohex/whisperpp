---
phase: 01-privacy-and-safety-controls
plan: 04
subsystem: privacy-ui
tags: [android, ime, compose, datastore, privacy]

# Dependency graph
requires:
  - phase: 01-03
    provides: Keystore-backed provider secrets and settings destination extras
provides:
  - Per-app external-send policy persistence keyed by packageName
  - Privacy & Safety settings route with searchable app list and toggles
  - IME send gate enforcement for per-app blocks with blocked-sheet explanation
affects: [01-05, phase-2, dictation, command-mode]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Centralized IME send gating combining secure-field and per-app policy checks
    - Settings deep-link destination routing via SettingsScreen routes

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SendPolicyRepository.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt
  modified:
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt

key-decisions:
  - "Persist per-app policy as packageName->blocked JSON and remove false rules to keep default-allow semantics explicit."
  - "Use privacy_safety as a first-class settings route for both settings entry and IME deep-link navigation."
  - "Per-app blocking reuses blocked-sheet UX with app-rule-specific copy while keeping secure-field don't-show behavior limited to secure-field blocks."

patterns-established:
  - "IME send checks must run through shouldBlockExternalSend() before recorder/transcriber/smart-fix actions."
  - "Privacy controls in settings are exposed as dedicated routes consumable by MainActivity destination extras."

# Metrics
duration: 6m
completed: 2026-02-26
---

# Phase 1 Plan 4: Per-app send policy UI + enforcement Summary

**Per-app package-level external-send blocking shipped with searchable Privacy & Safety controls and IME enforcement that stops active sending and explains app-rule blocks.**

## Performance

- **Duration:** 6m
- **Started:** 2026-02-26T15:50:34Z
- **Completed:** 2026-02-26T15:56:46Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- Added DataStore-backed per-app send policy repository keyed by `EditorInfo.packageName`.
- Added Privacy & Safety settings screen with launcher-app enumeration, search, allow/block toggles, and manual package blocking fallback.
- Enforced per-app blocking in IME send gate (dictation/transcription/smart-fix) with immediate stop and app-rule blocked explanation UX.

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement per-app send policy persistence keyed by package name** - `d8a91ee` (feat)
2. **Task 2: Add Privacy & Safety settings screen with installed-apps list + search + block toggles** - `91011b1` (feat)
3. **Task 3: Enforce per-app policy in IME send gate + blocked explanation UX** - `97c1745` (feat)

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SendPolicyRepository.kt` - Persists and exposes per-app blocked rules via Flow APIs.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt` - Searchable per-app policy management UI with manual package-rule fallback.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - Added Privacy & Safety route and entry point from main settings.
- `android/app/src/main/AndroidManifest.xml` - Added launcher intent package-visibility queries.
- `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` - Added destination routing for `privacy_safety`.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - Added per-app policy checks and stop-on-block behavior.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt` - Added app-rule blocked-sheet messaging and behavior.

## Decisions Made
- Stored only blocked=true entries for per-app policy; unblocked returns to implicit default-allow.
- Exposed Privacy & Safety as a dedicated nav route so IME deep-link lands directly on policy controls.
- Kept secure-field "don't show again" scope limited to secure-field explanations; app-rule blocks always explain on tap.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Plan verification command needed execution from `android/` module context (`./gradlew`) instead of repo root wrapper invocation.
- Initial `PrivacySafetyScreen` compile failed on missing `statusBars` import; resolved by adding the Compose layout import.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- PRIV-04 is satisfied with packageName-keyed blocking UI and IME enforcement.
- Ready to execute 01-05 privacy disclosure and verbose redacted logging work.

---
*Phase: 01-privacy-and-safety-controls*
*Completed: 2026-02-26*
