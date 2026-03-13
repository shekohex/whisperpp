---
phase: 09
slug: privacy-traceability-command-disclosure-alignment
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-13
---

# Phase 09 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 4.13.2, Compose UI Test, AndroidJUnitRunner 1.6.2 |
| **Config file** | none — Gradle/Android defaults in `android/app/build.gradle.kts` |
| **Quick run command** | `./android/gradlew testDebugUnitTest` |
| **Full suite command** | `./android/gradlew testDebugUnitTest connectedDebugAndroidTest` |
| **Estimated runtime** | ~90 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./android/gradlew testDebugUnitTest`
- **After every plan wave:** Run `./android/gradlew testDebugUnitTest connectedDebugAndroidTest`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 90 seconds

---

## Per-task Verification Map

| task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | PRIV-01 | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.SecureFieldDetectorTest"` | ❌ W0 | ⬜ pending |
| 09-01-02 | 01 | 1 | PRIV-03 | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.NetworkLoggingPrivacyTest"` | ❌ W0 | ⬜ pending |
| 09-02-01 | 02 | 1 | PRIV-05, CMD-03 | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.command.CommandDisclosureFlowTest"` | ❌ W0 | ⬜ pending |
| 09-02-02 | 02 | 1 | PRIV-04, PRIV-05 | androidTest | `./android/gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.shekohex.whisperpp.ui.settings.PrivacySafetyScreenUiTest` | ❌ W0 | ⬜ pending |
| 09-03-01 | 03 | 2 | PRIV-02 | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsBackupRepositoryExportTest"` | ✅ | ⬜ pending |
| 09-03-02 | 03 | 2 | PRIV-01, PRIV-04, PRIV-05 | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.SendPolicyRepositoryTest" --tests "com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatterTest"` | ❌ W0 / ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/SecureFieldDetectorTest.kt` — stubs for PRIV-01
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/NetworkLoggingPrivacyTest.kt` — stubs for PRIV-03
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/SendPolicyRepositoryTest.kt` — stubs for PRIV-04
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/command/CommandDisclosureFlowTest.kt` — stubs for CMD-03 and PRIV-05
- [ ] `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreenUiTest.kt` — settings disclosure/reset/send-policy UI coverage for PRIV-04 and PRIV-05

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Command first-use disclosure appears before spoken-instruction capture in a real IME host | CMD-03, PRIV-05 | Requires interactive IME/audio confirmation in a target app | Enable Whisper++ IME, select text in a non-secure editor, tap Command, confirm selected text, verify disclosure appears before recording indicator or audio capture, then continue and confirm the command runs |
| Secure-field command/dictation affordances stay blocked with explanation on real password/OTP fields | PRIV-01 | Requires live secure-field surfaces across host apps | Open a password or OTP field, show the IME, confirm dictation and command are blocked, tap the explanation affordance, and verify secure-field-specific copy |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
