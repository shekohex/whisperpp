# Phase 8: Phase 06 Verification Recovery - Research

**Researched:** 2026-03-12
**Domain:** Android phase-verification recovery for shipped settings/import/export work
**Confidence:** MEDIUM

<user_constraints>
## User Constraints

No `CONTEXT.md` exists for this phase.

### Locked Decisions
- Phase goal is verification recovery for shipped phase-06 settings/import-export behavior.
- Must address `UI-01`, `SET-01`, and `SET-02`.
- Scope is gap closure: remove audit orphaning caused by missing phase-06 verification coverage.
- Use existing phase-06 shipped work, summaries, and UAT artifacts as the evidence base.

### OpenCode's Discretion
- Exact split between new automated coverage, human-needed verification, and documentation recovery.
- Exact verification artifact updates needed to restore traceability.

### Deferred Ideas (OUT OF SCOPE)
- Phase 09 privacy/command disclosure recovery.
- Broad Nyquist backfill for unrelated phases.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| UI-01 | Settings UI uses Material 3 (Material You) components + theming | Verify grouped settings home, nested Backup & restore route, and Material 3 surfaces with focused Compose androidTest plus code-backed evidence in `06-VERIFICATION.md`. |
| SET-01 | All core behavior is configurable in settings (providers/models, prompts, per-app/per-language overrides, dictation/enhancement/command toggles) | Verify settings-home discoverability/status cards, nested destinations, help routing, and post-restore home-state wiring; cite shipped phase-06 UI artifacts plus targeted UI assertions. |
| SET-02 | User can export/import full settings and back up/restore them | Reuse existing passing backup unit tests for crypto/export/import/apply semantics, then add UI-surface proof for backup route, preview/report flow, and repair/status handoff. |
</phase_requirements>

## Summary

Phase 8 is not new feature delivery. It is traceability recovery for already-shipped phase-06 work. The audit is explicit: `UI-01`, `SET-01`, and `SET-02` are orphaned because phase 06 has summaries and UAT evidence but no `06-VERIFICATION.md`. The shipped implementation is real and substantial: grouped settings home, nested backup/restore route, encrypted backup envelope, restore analysis/apply pipeline, repair checklist flow, and phase-06 UAT all exist.

The evidence base is uneven. `SET-02` already has strong unit coverage: `SettingsBackupCryptoTest`, `SettingsBackupRepositoryExportTest`, `SettingsBackupImportAnalysisTest`, and `SettingsBackupApplyTest` all exist, and they passed on 2026-03-12 when run from the Android module. UI-facing verification is the weak seam: there is no settings/backup androidTest, and the settings package currently exposes test tags only for analytics, not for backup/restore or the phase-06 home surfaces.

Plan Phase 8 around one goal: produce an honest, audit-grade verification artifact for phase 06. That likely means: keep the existing backup unit suite as the SET-02 backbone, add one focused Compose androidTest for settings-home + backup/restore surfaces, add minimal stable test hooks where selectors are brittle, then write `06-VERIFICATION.md` in the same observable-truth format used by phases 05 and 07. Also close traceability artifacts that depend on verification existing.

**Primary recommendation:** Treat Phase 8 as evidence recovery: add the minimum missing UI automation for phase-06 settings surfaces, then author `06-VERIFICATION.md` and update requirement traceability/checklists so `UI-01`, `SET-01`, and `SET-02` are no longer orphaned.

## Standard Stack

### Core
| Library / Tool | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit4 | `4.13.2` | Unit verification for backup crypto/export/import/apply | Already configured in repo and already proves most `SET-02` domain logic. |
| Jetpack Compose UI Test (`ui-test-junit4`) | Compose BOM `2024.02.00` | Instrumented verification for settings home and backup/restore UI | Already used successfully by `AnalyticsDashboardUiTest`; best fit for phase-06 UI recovery. |
| AndroidJUnitRunner | `1.6.2` | Runs instrumented UI tests on emulator/device | Already configured in `android/app/build.gradle.kts`. |
| Compose Material 3 | BOM `2024.02.00` | Actual UI under verification for `UI-01` | Verifies shipped Material 3 settings surfaces without introducing new UI stack. |
| Phase verification report format | repo-local | Audit-traceable artifact (`06-VERIFICATION.md`) | Existing phases 05 and 07 establish the house format the audit already trusts. |

### Supporting
| Library / Tool | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `./android/gradlew -p android assembleDebug` | Gradle wrapper | Compile/build gate for verification changes | Run after UI-test hooks or verification-only code touches. |
| `06-UAT.md` | repo-local | Human-run behavior evidence for shipped phase-06 flow | Use as supporting evidence, not as the only closure artifact. |
| `SettingsBackup*Test.kt` suite | repo-local | Existing proof of encrypted backup and restore semantics | Reuse directly for `SET-02`; do not rewrite. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Focused Compose androidTest + verification doc | UAT-only recovery | Faster, but weak for audit-grade UI proof and likely forces large human-needed sections. |
| Focused composable/navigation checks | Full end-to-end SAF picker automation | More realistic, but much heavier and brittle for this recovery phase. |
| Minimal test tags/semantics hooks | Broad settings UI refactor for testability | Over-scope; verification recovery should not redesign shipped UI. |

**Installation:**
```bash
# No new dependency required for the baseline plan.
# Existing stack already includes JUnit4, Compose UI tests, and AndroidJUnitRunner.
```

## Architecture Patterns

### Recommended Project Structure
```text
.planning/phases/06-settings-ux-import-export/
├── 06-VERIFICATION.md                   # missing audit-blocking artifact
├── 06-UAT.md                            # existing human-run evidence
├── 06-01-SUMMARY.md ... 06-04-SUMMARY.md# shipped work summaries

android/app/src/test/java/.../data/
├── SettingsBackupCryptoTest.kt
├── SettingsBackupRepositoryExportTest.kt
├── SettingsBackupImportAnalysisTest.kt
└── SettingsBackupApplyTest.kt

android/app/src/androidTest/java/.../ui/settings/
└── Phase06VerificationUiTest.kt         # likely Wave 0 gap
```

### Pattern 1: Evidence-First Re-Verification
**What:** Build `06-VERIFICATION.md` from observable truths, required artifacts, key links, requirements coverage, and validation runs.
**When to use:** Always for this phase.
**Example:**
```markdown
## Goal Achievement

| # | Truth | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Backup export is password-encrypted only. | ✓ VERIFIED | `SettingsBackupCryptoTest.kt`, `BackupRestoreScreen.kt` |
| 2 | Restore stays inside settings and surfaces repair follow-up. | ✓ VERIFIED | `BackupRestoreScreen.kt`, `SettingsHomeScreen.kt` |
```

### Pattern 2: Split Proof by Seam
**What:** Use unit tests for backup domain logic and androidTest for settings/backup UI flows.
**When to use:** For `SET-02` data semantics vs `UI-01`/`SET-01` UI proof.
**Example:**
```kotlin
val analysis = repository.analyzeEncryptedBackup(envelope, PASSWORD, RestoreMode.MERGE)
val summary = repository.applyImportAnalysis(
    analysis = analysis,
    includedCategoryIds = setOf(SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS),
)
```
Source: `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupApplyTest.kt`

### Pattern 3: Minimal Stable Test Hooks
**What:** Add only the smallest set of `testTag`/stable semantics needed for reliable Compose assertions.
**When to use:** Where backup/restore UI text is too verbose or stateful for resilient selection.
**Example:**
```kotlin
val analyticsCard = composeRule.onNodeWithTag("analytics_home_card")
analyticsCard.assertIsDisplayed()
analyticsCard.performClick()
```
Source: `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/AnalyticsDashboardUiTest.kt`

### Pattern 4: Honest Human-Needed Boundaries
**What:** Keep system file picker and visual-polish checks in `human_verification` if they cannot be cleanly automated.
**When to use:** SAF integration or subjective spacing/copy quality.
**Example:** Verify file-pick/password/preview/apply semantics in code/UI tests, but leave real document-provider interaction and visual quality in explicit human-needed steps if necessary.

### Anti-Patterns to Avoid
- **Docs-only recovery:** Writing `06-VERIFICATION.md` without any additional UI evidence will leave `UI-01`/`SET-01` thin.
- **Full fake E2E harness for SAF:** Android system pickers are not the right place to spend this phase budget.
- **Broad feature changes during verification:** This phase should verify shipped work, not re-implement Phase 6.
- **Root-dir Gradle invocation without module targeting:** running Gradle from repo root failed because the Gradle build lives under `android/`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Verification artifact format | ad-hoc checklist or narrative note | Existing phase verification report shape from phases 05/07 | Audit already consumes that structure. |
| UI proof | screenshot dump or manual notes only | Compose androidTest with minimal tags/semantics | Deterministic, repeatable, traceable. |
| Backup-domain proof | manual JSON inspection | Existing `SettingsBackup*Test.kt` suite | Already covers crypto, export completeness, import analysis, and apply semantics. |
| SAF verification | custom picker automation | Focused repository/UI verification + explicit human-needed picker check | Lower brittleness, same audit value. |

**Key insight:** The cheapest honest plan is not “write the missing markdown”; it is “reuse existing backup tests, add just enough UI automation to prove shipped settings surfaces, then write the missing markdown.”

## Common Pitfalls

### Pitfall 1: Treating Phase 8 as documentation-only
**What goes wrong:** `06-VERIFICATION.md` exists, but UI claims still rest mostly on summaries and UAT.
**Why it happens:** The audit blocker text names the missing verification file, so it is tempting to stop there.
**How to avoid:** Add at least one focused settings/backup androidTest before writing final verification.
**Warning signs:** No new automated proof for `SettingsHomeScreen` or `BackupRestoreScreen`.

### Pitfall 2: Assuming SET-02 is fully covered because unit tests exist
**What goes wrong:** Backup domain semantics are proven, but user-facing import/export flow remains weakly verified.
**Why it happens:** `SettingsBackup*Test.kt` is strong and easy to over-generalize.
**How to avoid:** Separate repository semantics from UI flow proof in the plan.
**Warning signs:** Verification cites only data-layer tests for password/export/import/repair UX behaviors.

### Pitfall 3: Missing saved-state home-status verification
**What goes wrong:** Restore works, but the settings home banner/status handoff is unproven.
**Why it happens:** The handoff is UI wiring, not repository logic.
**How to avoid:** Include assertions for `BACKUP_RESTORE_HOME_STATUS_KEY` / repair-count effects through navigation or screen state.
**Warning signs:** `SettingsHomeScreen` is cited, but no test asserts the post-restore status text or repair-needed banner.

### Pitfall 4: No stable selectors for phase-06 UI surfaces
**What goes wrong:** Compose tests become brittle or impossible.
**Why it happens:** Current settings package exposes test tags only for analytics.
**How to avoid:** Add minimal tags/semantics to the backup card, preview block, and completion summary only where needed.
**Warning signs:** Tests depend on very long prose strings or card ordering only.

### Pitfall 5: Over-automating system file-picker behavior
**What goes wrong:** Verification effort stalls on SAF/system-dialog orchestration.
**Why it happens:** Realistic restore/export flows cross app boundaries.
**How to avoid:** Prove repository behavior and in-app state transitions automatically; keep actual picker/provider interaction as explicit human-needed verification if required.
**Warning signs:** Test plan spends more effort on document-provider plumbing than on settings behavior.

### Pitfall 6: Using the wrong Gradle execution context
**What goes wrong:** Verification commands fail even though tests/build are fine.
**Why it happens:** Repo root is not the Gradle root.
**How to avoid:** Use `./android/gradlew -p android ...` from repo root or run `./gradlew ...` inside `android/`.
**Warning signs:** Error says the repo root does not contain a Gradle build.

### Pitfall 7: Forgetting the closure artifacts beyond `06-VERIFICATION.md`
**What goes wrong:** Verification exists, but requirement traceability still looks stale.
**Why it happens:** The phase goal is phrased as verification recovery, not checklist recovery.
**How to avoid:** Plan explicit traceability updates after verification lands.
**Warning signs:** `REQUIREMENTS.md` still leaves `UI-01`, `SET-01`, `SET-02` unchecked/pending after the phase completes.

## Code Examples

Verified patterns from repo sources:

### Compose settings-route harness
```kotlin
composeRule.setContent {
    MaterialTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = SettingsScreen.Main.route) {
            composable(SettingsScreen.Main.route) {
                SettingsHomeScreen(
                    dataStore = context.dataStore,
                    navController = navController,
                )
            }
            composable(SettingsScreen.Analytics.route) {
                Text("Analytics dashboard")
            }
        }
    }
}
```
Source: `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/AnalyticsDashboardUiTest.kt`

### Existing backup apply verification seam
```kotlin
val analysis = repository.analyzeEncryptedBackup(
    envelopeForPayload(payload),
    PASSWORD,
    RestoreMode.OVERWRITE,
)

val summary = repository.applyImportAnalysis(analysis)

assertTrue(summary.clearedSelections.any { it.selectionType == RestoreSelectionType.ACTIVE_STT })
assertTrue(summary.repairChecklist.any { it.area == RestoreRepairArea.ACTIVE_STT })
```
Source: `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupApplyTest.kt`

### Stable selector pattern already used in settings UI tests
```kotlin
val analyticsCard = composeRule.onNodeWithTag("analytics_home_card")
analyticsCard.assertIsDisplayed()
analyticsCard.performClick()
```
Source: `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/AnalyticsDashboardUiTest.kt`

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Phase summaries + UAT only | Phase-scoped `*-VERIFICATION.md` with observable truths and evidence tables | Existing verifier workflow; phases 05 and 07 show the pattern | Audit can trace requirements to explicit verification evidence. |
| Implicit proof from shipped code | Mixed proof: unit tests, UI tests, human-needed sections, and build output | Current repo practice | Lets verification stay honest instead of overstating automation. |
| Broad text-only Compose assertions | Minimal targeted `testTag` hooks for stable assertions | Phase 07 analytics UI | Lower flakiness for Compose verification. |
| Stale assumption that phase-06 tests were blocked | Current reality: backup unit suite and `assembleDebug` pass with correct Android-module command | Verified 2026-03-12 | Previous deferred blocker note is outdated for planning. |

**Deprecated/outdated:**
- Relying on `06-UAT.md` alone to close `UI-01`, `SET-01`, and `SET-02`.
- Treating `.planning/phases/06-settings-ux-import-export/deferred-items.md` as current truth for backup test execution; the targeted backup suite now passes.

## Open Questions

1. **How much new UI automation is the minimum honest closure?**
   - What we know: no settings/backup androidTest exists today; analytics demonstrates the pattern; backup unit coverage already exists.
   - What's unclear: whether one focused `Phase06VerificationUiTest` class is enough, or whether multiple classes are needed.
   - Recommendation: Start with one focused androidTest covering settings home entry, backup route, restore flow copy/states, and post-restore summary hooks; expand only if gaps remain.

2. **Should Phase 8 also backfill `06-VALIDATION.md`?**
   - What we know: milestone audit marks validation missing for phase 06, but the explicit phase-08 gap closure is orphaned verification coverage.
   - What's unclear: whether the planner should keep Phase 8 tightly scoped to audit closure or opportunistically close Nyquist debt too.
   - Recommendation: Make `06-VERIFICATION.md` and requirement traceability the must-have. Treat `06-VALIDATION.md` as opportunistic only if it does not dilute closure work.

3. **What remains human-needed after automation?**
   - What we know: SAF/system file pickers and subjective visual quality are awkward to prove fully in deterministic tests.
   - What's unclear: whether the repo/emulator setup available during execution will support enough picker coverage to avoid `human_needed` status.
   - Recommendation: Plan explicit human-needed steps for real picker interaction and visual hierarchy if automation cannot honestly cover them.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 `4.13.2` + AndroidX Compose UI test + AndroidJUnitRunner `1.6.2` |
| Config file | none — Gradle defaults in `android/app/build.gradle.kts` |
| Quick run command | `./android/gradlew -p android testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsBackupCryptoTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupRepositoryExportTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupImportAnalysisTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupApplyTest" -x lint` |
| Full suite command | `./android/gradlew -p android testDebugUnitTest connectedDebugAndroidTest assembleDebug` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UI-01 | Settings home and Backup & restore route render as shipped Material 3 grouped surfaces | androidTest | `./android/gradlew -p android connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.shekohex.whisperpp.ui.settings.Phase06VerificationUiTest` | ❌ Wave 0 |
| SET-01 | Settings home exposes core settings areas, status lines, and repair/status integration for shipped configuration surfaces | androidTest | `./android/gradlew -p android connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.shekohex.whisperpp.ui.settings.Phase06VerificationUiTest` | ❌ Wave 0 |
| SET-02 | Encrypted backup/export/import analysis/apply semantics hold, and restore UI surfaces preview/report state honestly | unit + androidTest | `./android/gradlew -p android testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsBackupCryptoTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupRepositoryExportTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupImportAnalysisTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupApplyTest" -x lint` | ✅ unit / ❌ UI Wave 0 |

### Sampling Rate
- **Per task commit:** run the targeted backup unit suite or the focused `Phase06VerificationUiTest` command for the surface touched.
- **Per wave merge:** `./android/gradlew -p android testDebugUnitTest assembleDebug`; include `connectedDebugAndroidTest` once the new settings UI test exists.
- **Phase gate:** full suite green, `06-VERIFICATION.md` written, and traceability artifacts updated before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/Phase06VerificationUiTest.kt` — covers `UI-01`, `SET-01`, and UI-facing `SET-02`
- [ ] Stable test selectors in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt` and `BackupRestoreScreen.kt` for backup card, preview section, and restore summary section
- [ ] `.planning/phases/06-settings-ux-import-export/06-VERIFICATION.md` — audit-blocking artifact itself
- [ ] Optional: `.planning/phases/06-settings-ux-import-export/06-VALIDATION.md` if planner decides to close Nyquist debt while here

## Sources

### Primary (HIGH confidence)
- `.planning/ROADMAP.md` - phase 08 goal, dependency, requirement scope
- `.planning/REQUIREMENTS.md` - requirement text and current traceability state
- `.planning/STATE.md` - shipped phase-06 decisions retained in project history
- `.planning/v1.0-MILESTONE-AUDIT.md` - exact orphaned-gap cause and expected closure
- `.planning/phases/06-settings-ux-import-export/06-01-SUMMARY.md` - encrypted backup export foundation delivered
- `.planning/phases/06-settings-ux-import-export/06-02-SUMMARY.md` - restore analysis/apply semantics delivered
- `.planning/phases/06-settings-ux-import-export/06-03-SUMMARY.md` - grouped settings home/help routing delivered
- `.planning/phases/06-settings-ux-import-export/06-04-SUMMARY.md` - backup/restore UI flow delivered
- `.planning/phases/06-settings-ux-import-export/06-UAT.md` - shipped human-run phase-06 checks
- `.planning/phases/05-command-mode-presets/05-VERIFICATION.md` - passed verification report format pattern
- `.planning/phases/07-local-analytics-dashboard/07-VERIFICATION.md` - human-needed verification pattern and evidence style
- `.planning/phases/07-local-analytics-dashboard/07-VALIDATION.md` - current Nyquist validation format pattern
- `android/app/build.gradle.kts` - actual test stack, runner, Compose BOM, and module layout
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt` - grouped home, repair banner, backup route status
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/BackupRestoreScreen.kt` - export/import/preview/summary flow
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - nested settings route wiring
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupCryptoTest.kt` - backup crypto verification
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupRepositoryExportTest.kt` - export coverage verification
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupImportAnalysisTest.kt` - import analysis verification
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupApplyTest.kt` - apply/repair verification
- `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/AnalyticsDashboardUiTest.kt` - current settings UI-test pattern
- `./gradlew testDebugUnitTest --tests ...SettingsBackup... -x lint` run in `android/` on 2026-03-12 - passed
- `./gradlew assembleDebug` run in `android/` on 2026-03-12 - passed

### Secondary (MEDIUM confidence)
- Android Developers official docs surfaced via grounded search: “Testing in Jetpack Compose” - Compose UI testing guidance
- Android Developers official docs surfaced via grounded search: “AndroidJUnitRunner” / “Set up project for AndroidX Test” - instrumented test runner guidance

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - explicit in `build.gradle.kts`, existing tests, and successful local commands
- Architecture: MEDIUM - evidence split is clear, but exact minimum UI automation vs human-needed boundary is still a planning choice
- Pitfalls: HIGH - directly supported by audit findings, grep results, and command failures/successes

**Research date:** 2026-03-12
**Valid until:** 2026-04-11
