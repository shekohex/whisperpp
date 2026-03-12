---
phase: 08
slug: phase-06-verification-recovery
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-12
---

# Phase 08 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 4.13.2 + AndroidX Compose UI test + AndroidJUnitRunner 1.6.2 |
| **Config file** | none — Gradle defaults in `android/app/build.gradle.kts` |
| **Quick run command** | `./android/gradlew -p android testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsBackupCryptoTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupRepositoryExportTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupImportAnalysisTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupApplyTest" -x lint` |
| **Full suite command** | `./android/gradlew -p android testDebugUnitTest connectedDebugAndroidTest assembleDebug` |
| **Estimated runtime** | ~20-40 seconds targeted, ~90 seconds full suite |

---

## Sampling Rate

- **After every task commit:** Run the targeted backup unit suite or `Phase06VerificationUiTest`, depending on the seam touched.
- **After every plan wave:** Run `./android/gradlew -p android testDebugUnitTest assembleDebug`; include `connectedDebugAndroidTest` once the phase-06 UI verification test exists.
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 40 seconds

---

## Per-task Verification Map

| task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 08-W0-01 | W0 | 0 | UI-01 | androidTest | `./android/gradlew -p android connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.shekohex.whisperpp.ui.settings.Phase06VerificationUiTest` | ❌ W0 | ⬜ pending |
| 08-W0-02 | W0 | 0 | SET-01 | androidTest | `./android/gradlew -p android connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.shekohex.whisperpp.ui.settings.Phase06VerificationUiTest` | ❌ W0 | ⬜ pending |
| 08-W0-03 | W0 | 0 | SET-02 | unit + androidTest | `./android/gradlew -p android testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsBackupCryptoTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupRepositoryExportTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupImportAnalysisTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupApplyTest" -x lint` | ✅ unit / ❌ UI W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/Phase06VerificationUiTest.kt` — focused settings-home and backup/restore verification for UI-01, SET-01, and UI-facing SET-02
- [ ] Stable test selectors in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt`
- [ ] Stable test selectors in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/BackupRestoreScreen.kt`
- [ ] `.planning/phases/06-settings-ux-import-export/06-VERIFICATION.md` — observable-truth verification artifact

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real system file-picker interaction for export/import document selection | SET-02 | SAF/document-provider dialogs cross app boundaries and are brittle to automate honestly in this recovery phase | Run exported app on emulator/device, open Settings → Backup & restore, trigger export and import with a real document provider, confirm the app returns to settings and shows preview/summary states |
| Final visual hierarchy/material polish check for grouped settings home and nested backup screen | UI-01 | Material 3 usage can be automation-assisted but visual polish is still subjective | Inspect Settings home and Backup & restore on device, confirm grouped cards/top app bars/help affordances match shipped Material 3 styling |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [x] Feedback latency < 40s for targeted checks
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
