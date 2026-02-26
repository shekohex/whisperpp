---
phase: 01-privacy-and-safety-controls
plan: 05
subsystem: ui
tags: [privacy, disclosures, ime, compose, okhttp, datastore]

# Dependency graph
requires:
  - phase: 01-04
    provides: per-app external-send policy enforcement and privacy_safety deep-link handling
provides:
  - Mode-specific privacy disclosures derived from active provider endpoints
  - Privacy & Safety diagnostics controls for verbose redacted network headers logging
  - First-use disclosure gating before dictation and Smart Fix sends
affects: [02-providers-and-models, 03-dictation, 04-prompts-profiles-enhancement, 05-command-mode-and-presets]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Mode-specific disclosure formatting from live provider config
    - IME first-use send gate with explicit Continue/Cancel/Open Settings user decision
    - Runtime DataStore-driven network logging mode wiring for OkHttp clients

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt

key-decisions:
  - "Disclosure strings are generated from parsed provider endpoints (base URL + path) via HttpUrl"
  - "Gemini text-mode disclosures normalize to /models/{model}:generateContent path"
  - "First-use gating is mode-specific and blocks sends until explicit Continue"

patterns-established:
  - "Privacy disclosures and first-use modal share the same formatter source"
  - "Diagnostics toggles remain privacy-safe by constraining logs to redacted headers only"

# Metrics
duration: 8m
completed: 2026-02-26
---

# Phase 01 Plan 05: Privacy disclosures + first-use gating + verbose logs toggle Summary

**Mode-aware privacy disclosures now show real send targets, and first-use gates block dictation/Smart Fix sends until explicit consent while verbose logs stay header-only and redacted.**

## Performance

- **Duration:** 8m
- **Started:** 2026-02-26T15:59:15Z
- **Completed:** 2026-02-26T16:07:12Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Added `PrivacyDisclosureFormatter` to produce disclosure content for dictation, Smart Fix, and command placeholders from actual config.
- Extended Privacy & Safety settings with disclosure cards, verbose network logs toggle, and first-use disclosure reset action.
- Added first-use disclosure bottom-sheet gating in IME flow for dictation and Smart Fix, and wired verbose log preference to transcriber/fixer clients.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add disclosure formatter derived from real endpoints + context usage** - `0aa6f5e` (feat)
2. **Task 2: Extend Privacy & Safety screen with disclosures + verbose logs toggle (still redacted)** - `806f5e6` (feat)
3. **Task 3: Add first-use disclosure gating before sending + wire verbose log toggle to network clients** - `4fbf313` (feat)

**Plan metadata:** pending

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt` - Pure disclosure formatter for mode-specific endpoint/data-send descriptions.
- `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` - Added DataStore keys for verbose logging and disclosure-shown flags.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt` - Added Disclosure and Diagnostics sections with reset action.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - Added first-use disclosure decision flow and runtime logging preference wiring.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt` - Added first-use disclosure modal UI with Continue/Cancel/Open Privacy & Safety actions.

## Decisions Made
- Used a single formatter for both settings disclosure and first-use modal content to avoid drift.
- Used mode-specific DataStore flags (`dictation`, `enhancement`, `command`) with reset support in Privacy & Safety.
- Kept verbose logs constrained to HEADERS mode only; no payload logging path introduced.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Verification command required Android project working directory**
- **Found during:** Task 1 verification
- **Issue:** Running `./android/gradlew :app:assembleDebug` from repo root failed because Gradle root is `android/`.
- **Fix:** Executed verification in `android/` workdir using `./gradlew :app:assembleDebug`.
- **Files modified:** None (execution-only adjustment)
- **Verification:** `assembleDebug` succeeded for each task and final verification.
- **Committed in:** N/A

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** No scope change; deviation only corrected execution environment for required verification.

## Authentication Gates

None.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 01 is complete: disclosures, diagnostics boundary, secure send gating, and per-app policy controls are in place.
- No blockers recorded for Phase 02.

---
*Phase: 01-privacy-and-safety-controls*
*Completed: 2026-02-26*
