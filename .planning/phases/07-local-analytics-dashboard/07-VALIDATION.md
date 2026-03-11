---
phase: 07
slug: local-analytics-dashboard
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-09
---

# Phase 07 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 4.13.2 + AndroidX Compose UI test + AndroidJUnitRunner |
| **Config file** | none — Gradle defaults in `android/app/build.gradle.kts` |
| **Quick run command** | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.AnalyticsFormatterTest" -x lint` |
| **Full suite command** | `./android/gradlew testDebugUnitTest connectedDebugAndroidTest` |
| **Estimated runtime** | ~10-25 seconds targeted, ~60 seconds full suite |

---

## Sampling Rate

- **After every task commit:** Run that task's targeted `<automated>` command from the PLAN file (single test class or `assembleDebug` only).
- **After every plan wave:** Run the affected targeted phase checks for that wave; reserve `connectedDebugAndroidTest` for the analytics UI plan or pre-verify gate.
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 25 seconds

---

## Per-task Verification Map

| task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 07-W0-01 | W0 | 0 | STATS-01 | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.AnalyticsRepositoryTest" -x lint` | ❌ W0 | ⬜ pending |
| 07-W0-02 | W0 | 0 | STATS-01 | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.AnalyticsFormatterTest" -x lint` | ❌ W0 | ⬜ pending |
| 07-W0-03 | W0 | 0 | PRIV-06 | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.AnalyticsPrivacyTest" -x lint` | ❌ W0 | ⬜ pending |
| 07-W0-04 | W0 | 0 | STATS-02 | androidTest | `./android/gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.shekohex.whisperpp.ui.settings.AnalyticsDashboardUiTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsRepositoryTest.kt` — stubs for STATS-01
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsFormatterTest.kt` — formatter and time-saved math coverage
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsPrivacyTest.kt` — reset and backup-boundary coverage for PRIV-06
- [ ] `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/AnalyticsDashboardUiTest.kt` — dashboard UI/reset flow coverage for STATS-02
- [ ] `androidx.navigation:navigation-testing:2.7.7` — if route-level navigation tests are added

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| None | n/a | All targeted phase behaviors should receive automated coverage after Wave 0 scaffolding | n/a |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [x] Feedback latency < 30s via task-targeted verifies
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
