---
phase: 07-local-analytics-dashboard
plan: 01
subsystem: data
tags: [android, kotlin, datastore, analytics, privacy, junit4]
requires:
  - phase: 03-dictation
    provides: dictation session duration and terminal outcome semantics for future analytics writes
  - phase: 06-settings-ux-import-export
    provides: backup and export boundaries that analytics must stay outside of
provides:
  - dedicated local analytics datastore and repository with reset support
  - immutable analytics snapshot contracts and centralized time-saved formatter math
  - backup and device-transfer exclusions that keep analytics local-only
affects: [dictation-runtime, settings-analytics-ui, backup-privacy-boundary]
tech-stack:
  added: []
  patterns:
    - dedicated preferences datastore per privacy-sensitive subsystem
    - derived analytics formatting from raw persisted counts only
    - fixed rolling seven-day analytics bucket snapshot
key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsModels.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsFormatter.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsStore.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsRepository.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsFormatterTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsRepositoryTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsPrivacyTest.kt
  modified:
    - android/app/src/main/res/xml/backup_rules.xml
    - android/app/src/main/res/xml/data_extraction_rules.xml
key-decisions:
  - "Analytics persistence uses a dedicated datastore file so backup exclusion can operate at file granularity."
  - "Dashboard strings remain derived from raw counts at read time; formatted copy is never persisted."
  - "Seven-day history is stored as a fixed LocalDate-keyed bucket window with dates serialized as strings for JVM-safe tests."
patterns-established:
  - "Analytics repository: aggregate completed and cancelled sessions into one snapshot Flow plus rolling day buckets."
  - "Privacy boundary: exclude analytics datastore from both full-backup and Android 12+ data extraction rules."
requirements-completed: [STATS-01, PRIV-06]
duration: 16 min
completed: 2026-03-09
---

# Phase 07 Plan 01: Local Analytics Foundation Summary

**Dedicated local analytics DataStore with seven-day snapshot aggregation, typing-based time-saved formatting, and explicit backup exclusion rules.**

## Performance

- **Duration:** 16 min
- **Started:** 2026-03-09T22:12:05Z
- **Completed:** 2026-03-09T22:28:43Z
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments
- Added immutable analytics snapshot and daily bucket contracts plus centralized formatter helpers for time saved, WPM, hero copy, and keystroke estimates.
- Built a dedicated analytics repository over a separate preferences DataStore file that aggregates completed and cancelled sessions and resets to a zeroed seven-day snapshot.
- Locked analytics behind backup and device-transfer exclusions and added regression tests to keep analytics out of backup/export surfaces.

## Task Commits

Each task was committed atomically:

1. **Task 1: define analytics snapshot contracts and formatter math** - `f9699bf` (test), `3a38559` (feat)
2. **Task 2: implement the dedicated analytics repository and local aggregation rules** - `e20b697` (test), `9815071` (feat)
3. **Task 3: lock analytics behind a local-only backup boundary** - `fd2ca26` (test), `5b1894d` (feat)

**Plan metadata:** Pending docs/state commit.

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsModels.kt` - immutable snapshot and seven-day bucket contracts for analytics consumers.
- `android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsFormatter.kt` - centralized typing baseline, time-saved formatting, hero summary, WPM, and keystroke estimate helpers.
- `android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsStore.kt` - dedicated analytics DataStore keys and file path constants.
- `android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsRepository.kt` - local-only aggregation, reset behavior, word counting, and snapshot Flow.
- `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsFormatterTest.kt` - formatter regression coverage.
- `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsRepositoryTest.kt` - repository aggregation and reset coverage.
- `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsPrivacyTest.kt` - backup boundary and export-surface coverage.
- `android/app/src/main/res/xml/backup_rules.xml` - full-backup exclusion for analytics storage.
- `android/app/src/main/res/xml/data_extraction_rules.xml` - cloud-backup and device-transfer exclusion for analytics storage.

## Decisions Made
- Used a separate analytics preferences file instead of the shared settings store so Android backup exclusions can enforce the local-only requirement.
- Calculated dashboard-friendly values such as time saved, WPM, and hero messaging from raw persisted totals instead of storing presentation strings.
- Serialized day-bucket dates as strings inside the repository storage layer to avoid JVM reflection issues with `LocalDate` in unit tests while keeping immutable `LocalDate` DTOs at the API boundary.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Replaced direct `LocalDate` Gson persistence with string-backed storage buckets**
- **Found during:** Task 2 (implement the dedicated analytics repository and local aggregation rules)
- **Issue:** Gson-backed repository tests failed on JVM with `InaccessibleObjectException` when serializing/deserializing `LocalDate` directly.
- **Fix:** Added an internal string-backed persistence model and mapped it to immutable `LocalDate` DTOs inside `AnalyticsRepository`.
- **Files modified:** `android/app/src/main/java/com/github/shekohex/whisperpp/analytics/AnalyticsRepository.kt`
- **Verification:** `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.AnalyticsRepositoryTest" -x lint`
- **Committed in:** `9815071` (part of task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** No scope change. The fix kept the repository testable on the existing JVM toolchain while preserving the planned API.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 7 runtime wiring can now record completed and cancelled dictation outcomes into a stable analytics repository without touching settings export/import state.
- Settings UI work can consume `AnalyticsRepository.snapshot` and backup/privacy boundaries are already enforced for the analytics store.

---
*Phase: 07-local-analytics-dashboard*
*Completed: 2026-03-09*

## Self-Check: PASSED
