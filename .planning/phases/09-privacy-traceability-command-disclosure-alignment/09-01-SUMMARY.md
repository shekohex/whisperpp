---
phase: 09-privacy-traceability-command-disclosure-alignment
plan: 01
subsystem: testing
tags: [privacy, command, junit4, compose-ui, datastore]
requires:
  - phase: 01-privacy-and-safety-controls
    provides: shared privacy primitives and disclosure surfaces
  - phase: 05-command-mode-presets
    provides: shipped command-mode pipeline requiring disclosure alignment
provides:
  - Wave-0 JVM regressions for secure-field detection and logging redaction
  - Wave-0 seams for send-policy persistence and command disclosure timing/content
  - Privacy & Safety androidTest harness with deterministic selectors for future runtime verification
affects: [09-02 runtime alignment, 09-03 traceability closure]
tech-stack:
  added: [org.json test dependency]
  patterns: [wave-0 seam tests before runtime fixes, stable Compose test tags for privacy surfaces]
key-files:
  created:
    - android/app/src/test/java/com/github/shekohex/whisperpp/privacy/SecureFieldDetectorTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/privacy/NetworkLoggingPrivacyTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/privacy/SendPolicyRepositoryTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/command/CommandDisclosureFlowTest.kt
    - android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreenUiTest.kt
  modified:
    - android/app/build.gradle.kts
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt
key-decisions:
  - "Use a task-local command disclosure seam that composes existing dictation/enhancement disclosures until wave-2 runtime alignment lands."
  - "Expose stable Privacy & Safety test tags so Compose verification can target disclosure and send-policy controls deterministically."
patterns-established:
  - "Wave-0 privacy recovery: add JVM seams first, then align runtime behavior in later plans."
  - "Privacy UI harnesses should prefer stable tags over copy-only selectors for repeatable androidTests."
requirements-completed: [PRIV-01, PRIV-03, PRIV-04, PRIV-05, CMD-03]
duration: 5 min
completed: 2026-03-13
---

# Phase 09 Plan 01: Privacy regression harness summary

**Wave-0 privacy coverage now pins secure-field detection, redacted logging, send-policy persistence, command disclosure seams, and Privacy & Safety UI verification hooks.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-13T15:06:16Z
- **Completed:** 2026-03-13T15:11:03Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- Added JVM regressions for secure-field detection and both network logging clients.
- Added repeatable seams for per-app send policy persistence and pre-recording command disclosure behavior.
- Added a Privacy & Safety androidTest harness plus stable test tags for disclosure reset and send-policy controls.

## Task Commits

Each task was committed atomically:

1. **task 1: add privacy boundary JVM tests for secure-field detection and logging redaction** - `d2c620b` (test)
2. **task 2: add JVM tests for per-app send-policy persistence and command disclosure flow seams** - `b2833e1` (test)
3. **task 3: create the Privacy & Safety instrumentation harness** - `5190299` (test)

## Files Created/Modified
- `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/SecureFieldDetectorTest.kt` - covers password, OTP, no-personalized-learning, and negative secure-field detection cases.
- `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/NetworkLoggingPrivacyTest.kt` - verifies transcriber and smart-fix logging stay at NONE/HEADERS with auth-header redaction.
- `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/SendPolicyRepositoryTest.kt` - verifies default-allow, normalization, persistence, and unblock removal semantics.
- `android/app/src/test/java/com/github/shekohex/whisperpp/command/CommandDisclosureFlowTest.kt` - defines timing/content seams for pre-recording disclosure and two-hop command disclosure content.
- `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreenUiTest.kt` - compiles deterministic Compose coverage for disclosure cards, reset action, and send-policy controls.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt` - adds stable tags used by the new instrumentation harness.
- `android/app/build.gradle.kts` - adds a unit-test JSON dependency so repository persistence tests run on the JVM.

## Decisions Made
- Used a command-disclosure seam inside the test harness instead of prematurely refactoring runtime code in this Wave-0 plan.
- Added stable tags on the shipped Privacy & Safety surface because copy-only selectors were too brittle for deterministic regression coverage.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added JVM JSON implementation for send-policy repository tests**
- **Found during:** task 2 (add JVM tests for per-app send-policy persistence and command disclosure flow seams)
- **Issue:** `SendPolicyRepository` uses `org.json.JSONObject`, which resolved to Android's not-mocked stub in `testDebugUnitTest`.
- **Fix:** Added `testImplementation("org.json:json:20240303")` so the repository persistence tests run in the JVM test environment.
- **Files modified:** `android/app/build.gradle.kts`
- **Verification:** `./android/gradlew -p android testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.SendPolicyRepositoryTest" --tests "com.github.shekohex.whisperpp.command.CommandDisclosureFlowTest"`
- **Committed in:** `b2833e1`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** The dependency was required to keep the planned repository regression coverage JVM-only. No scope creep.

## Issues Encountered
- Initial Gradle invocation used the wrong working directory and was rerun from repo root.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Wave 2 can now align runtime command disclosure timing/content against committed seams instead of inventing coverage ad hoc.
- Privacy & Safety has deterministic selectors ready for future disclosure-content assertions and reset/send-policy verification.

## Self-Check: PASSED

---
*Phase: 09-privacy-traceability-command-disclosure-alignment*
*Completed: 2026-03-13*
