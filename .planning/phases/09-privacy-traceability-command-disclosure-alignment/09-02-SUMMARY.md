---
phase: 09-privacy-traceability-command-disclosure-alignment
plan: 02
subsystem: privacy
tags: [android, privacy, command-mode, disclosures, compose-ui, testing]
requires:
  - phase: 01-privacy-and-safety-controls
    provides: shared privacy disclosure patterns and send-policy controls
  - phase: 05-command-mode-presets
    provides: command-mode runtime and transform flow
provides:
  - truthful two-hop command disclosures from live STT and text-provider config
  - pre-recording command consent gate with shared external-send blocking preserved
  - Privacy & Safety UI coverage for command disclosure, reset, and send-policy controls
affects: [09-03 traceability, privacy audit coverage, command-mode consent]
tech-stack:
  added: []
  patterns: [shared disclosure formatter rows, pre-capture command disclosure gating, deterministic Compose privacy fixtures]
key-files:
  created: []
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatterTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/command/CommandDisclosureFlowTest.kt
    - android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreenUiTest.kt
key-decisions:
  - "Command disclosure text stays shared by exposing formatter-rendered endpoint rows to both IME first-use UI and Privacy & Safety settings."
  - "Command-mode consent remains mode-specific and resolves before recorder start while shared external-send gates still wrap record, STT, and transform steps."
patterns-established:
  - "Privacy disclosures describe real runtime hops from effective provider selections rather than placeholder copy."
  - "Privacy Compose tests use stable same-package tags and seeded datastore fixtures for deterministic coverage."
requirements-completed: [PRIV-01, PRIV-04, PRIV-05, CMD-03]
duration: 53 min
completed: 2026-03-25
---

# Phase 09 Plan 02: Command disclosure alignment Summary

**Shared two-hop command disclosures with pre-recording consent gating and Privacy & Safety UI coverage.**

## Performance

- **Duration:** 53 min
- **Started:** 2026-03-25T19:17:43Z
- **Completed:** 2026-03-25T20:10:15Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- Replaced stale command-mode placeholder disclosure copy with truthful two-hop STT plus text-transform messaging sourced from live provider config.
- Moved command first-use consent ahead of spoken-instruction recording while keeping shared secure-field and per-app blocking checks around every external-send step.
- Expanded Privacy & Safety instrumentation coverage for labeled command disclosure hops, reset flow, and manual per-app send-policy controls.

## task Commits

Each task was committed atomically:

1. **task 1: make command disclosures truthful and shared across IME + settings** - `bb5ba07` (test), `feea21b` (feat)
2. **task 2: move command disclosure gating to pre-recording without bypassing shared blocks** - `b955f5d` (test), `842100f` (fix)
3. **task 3: prove Privacy & Safety disclosure/reset/send-policy UI with instrumentation** - `4fc6ecf` (test)

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt` - models command mode as explicit STT and text-transform hops from live runtime config.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - resolves command disclosures before recording and renders formatter output directly in first-use UI.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt` - renders shared labeled disclosure rows in Privacy & Safety.
- `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatterTest.kt` - proves truthful hop labeling and safe fallback rows.
- `android/app/src/test/java/com/github/shekohex/whisperpp/command/CommandDisclosureFlowTest.kt` - proves disclosure timing stays ahead of recorder start and shared gates remain intact.
- `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreenUiTest.kt` - covers command disclosure card content, disclosure reset, and manual send-policy controls.

## Decisions Made
- Command disclosure rows are rendered from shared formatter output in both settings and IME surfaces to prevent copy drift.
- Command-mode disclosure remains a mode-specific first-use flag; consent is checked before recording instead of after transcription.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Patched local Android SDK layout so Gradle verification could run on this host**
- **Found during:** task 3 verification
- **Issue:** The executor host lacked usable Android 34 platform/build-tools metadata, which blocked Gradle unit/build verification before project code could be exercised.
- **Fix:** Repaired local SDK paths/metadata under `/usr/local/share/android-sdk` so Gradle could compile and run JVM verification tasks.
- **Files modified:** `/usr/local/share/android-sdk/platforms/android-34/*`, `/usr/local/share/android-sdk/build-tools/34.0.0/*` (environment only)
- **Verification:** `testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` completed successfully afterward.
- **Committed in:** N/A (environment-only fix)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Required only to unblock local verification; repo scope stayed focused on command disclosure alignment.

## Issues Encountered
- `connectedDebugAndroidTest` still cannot execute in this environment because no device/emulator is attached: `DeviceException: No connected devices!` The test APK compiled successfully via `:app:assembleDebugAndroidTest`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 09 plan 03 can restore requirement traceability using the aligned disclosure/runtime artifacts and updated UI coverage.
- A connected Android device or emulator is still required to run `PrivacySafetyScreenUiTest` end-to-end outside compile-only verification.

## Self-Check: PASSED

- Summary file exists.
- task commits `bb5ba07`, `feea21b`, `b955f5d`, `842100f`, and `4fc6ecf` exist in git history.
