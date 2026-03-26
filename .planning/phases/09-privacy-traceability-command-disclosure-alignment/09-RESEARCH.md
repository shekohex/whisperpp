# Phase 9: Privacy Traceability & Command Disclosure Alignment - Research

**Researched:** 2026-03-13
**Domain:** Android IME privacy controls, command-mode disclosure timing, and requirements traceability recovery
**Confidence:** MEDIUM

<user_constraints>
## User Constraints

No `CONTEXT.md` exists for this phase.

### Locked Decisions
- Phase goal is gap closure, not a new privacy feature set.
- Must address `PRIV-01`, `PRIV-02`, `PRIV-03`, `PRIV-04`, `PRIV-05`, and `CMD-03`.
- Fix the phase-01 to phase-05 integration drift so command-mode privacy disclosure appears before spoken-instruction audio capture.
- Restore traceability from the privacy requirements back to verified artifacts.
- Reuse the shipped Phase 01 and Phase 05 primitives instead of replacing them.

### OpenCode's Discretion
- Exact split between runtime fixes, regression tests, and traceability/documentation updates.
- Exact disclosure model shape needed to represent command mode accurately.
- Exact evidence updates needed so audit artifacts and requirement status align again.

### Deferred Ideas (OUT OF SCOPE)
- New privacy features beyond the existing Phase 01 scope.
- Reworking unrelated settings, analytics, or provider flows.
- Broad Nyquist backfill for unrelated phases.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PRIV-01 | Dictation and command mode are disabled in secure fields (password/OTP/etc) with a clear explanation | Reuse `SecureFieldDetector`, `shouldBlockExternalSend()`, `blockedExplanationCopySpec()`, and the IME-safe fallback activity; add regression tests around secure-field detection and command-mode entry. |
| PRIV-02 | Provider API keys are stored securely (Keystore-backed) and excluded from logs | Keep `SecretsStore` as the only at-rest secret store, preserve `@Transient` runtime-only `ServiceProvider.apiKey`, and extend traceability to existing migration/export tests rather than inventing new storage. |
| PRIV-03 | Network logging redacts auth headers and does not log user audio/text payloads by default | Keep `HttpLoggingInterceptor` constrained to `NONE`/`HEADERS` plus `redactHeader("Authorization")` and `redactHeader("x-goog-api-key")`; add regression tests/documented evidence for the logging boundary. |
| PRIV-04 | User can set per-app send policy to block sending audio/text to external providers and Whisper++ enforces it | Reuse `SendPolicyRepository`, `PrivacySafetyScreen`, and the shared IME gate so command-mode recording, STT, and transform calls all stay blocked for denied apps. |
| PRIV-05 | UI clearly discloses what data is sent (audio/text) and to which provider endpoint(s) | Extend `PrivacyDisclosureFormatter` so command mode discloses both the instruction-audio STT hop and the text-transform hop; keep `PrivacySafetyScreen` and IME first-use UI fed by the same formatter. |
| CMD-03 | User can speak an instruction; Whisper++ transcribes it and sends (instruction + selected text) to the selected text model/provider | Preserve the existing command pipeline, but move command disclosure gating to after text confirmation and before `recorderManager.start()` so consent happens before audio capture. |
</phase_requirements>

## Summary

Phase 9 is mostly a recovery-and-alignment phase. The repo already contains the Phase 01 privacy primitives (`SecureFieldDetector`, `SendPolicyRepository`, `SecretsStore`, `PrivacyDisclosureFormatter`) and the Phase 05 command pipeline. The audit gap is that traceability drifted and one real integration bug shipped: command mode records instruction audio before the user sees the command disclosure, while the disclosure copy still says command mode is only planned.

The important implementation fact is that command mode is a two-hop external-send flow, not a single text-only flow. It first uploads spoken-instruction audio to the active STT provider via `WhisperTranscriber`, then sends selected text plus the transcribed instruction to the active text provider via `SmartFixer`. Current `PrivacyDisclosureFormatter.disclosureForCommand()` exposes only one endpoint and uses placeholder copy, so both the IME first-use sheet and Privacy & Safety settings are inaccurate for command mode today.

Plan this phase around three seams: align runtime disclosure timing, align disclosure content, and align traceability artifacts. Runtime-wise, the disclosure must move to the point where command text is already confirmed but recording has not started yet. Content-wise, command disclosure must describe both audio and text sends from live provider configuration. Traceability-wise, Phase 01 summaries, requirement checklist/status, and any new Phase 09 verification evidence must stop contradicting each other.

**Primary recommendation:** Treat Phase 9 as one shared-source-of-truth fix: extend `PrivacyDisclosureFormatter` to model the real command pipeline, invoke the command disclosure before `startCommandListening()` begins recording, then close the audit by updating verification/summary/checklist artifacts to match shipped privacy behavior.

## Standard Stack

### Core
| Library / API | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Android IME (`InputMethodService`, `EditorInfo`, `InputConnection`) | platform | Owns secure-field gating, command entry, and editor replacement | Existing runtime surface; all privacy decisions already depend on IME context. |
| Jetpack Compose + Material 3 | Compose BOM `2024.02.00` | Privacy & Safety screen and IME disclosure/blocking UI | Existing UI stack for both settings and keyboard surfaces. |
| AndroidX DataStore Preferences | `1.0.0` | Persists send policy, disclosure-shown flags, diagnostics toggle | Existing preference store; already backs privacy settings. |
| OkHttp logging-interceptor | `4.12.0` | Redacted request logging boundary for STT and transform clients | Existing networking/logging layer used by `WhisperTranscriber` and `SmartFixer`. |
| Android Keystore (`AndroidKeyStore` + AES/GCM) | platform | Provider API-key protection | Already implemented in `SecretsStore`; official platform primitive. |

### Supporting
| Library / API | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `PrivacyDisclosureFormatter` | repo-local | Shared disclosure generation from live provider config | Always for Privacy & Safety cards and IME first-use sheets. |
| `SendPolicyRepository` | repo-local | Package-name → blocked policy persistence | Always for per-app external-send enforcement. |
| `SecureFieldDetector` | repo-local | Password/OTP/no-personalized-learning detection | Always before any command/dictation recording or send starts. |
| JUnit4 | `4.13.2` | Fast regression tests for formatter/gates/repositories | Use for Wave 0 privacy regression coverage. |
| Compose UI Test + AndroidJUnitRunner | Compose BOM `2024.02.00`, runner `1.6.2` | Settings/IME UI verification | Use for Privacy & Safety disclosure and blocked-explanation UI assertions. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Extending the current privacy formatter | Separate command-only disclosure copy in IME and settings | Causes drift again; the audit issue exists because shared truth was incomplete. |
| `SecretsStore` + runtime `provider.copy(apiKey=...)` | New crypto/storage library | Unnecessary churn; current Keystore-backed split already matches `PRIV-02`. |
| Existing `shouldBlockExternalSend()` gate | Command-mode-specific block logic | Duplicates policy/security rules and risks divergence. |
| Summary frontmatter + verification + requirements checklist updates | Ad-hoc audit note only | Does not restore machine-readable traceability. |

**Installation:**
```bash
# No new dependency is required for the recommended plan.
# Use the existing Android/Compose/DataStore/OkHttp stack already in the repo.
```

## Architecture Patterns

### Recommended Project Structure
```text
.planning/phases/01-privacy-and-safety-controls/
├── 01-01-SUMMARY.md ... 01-06-SUMMARY.md   # historical summary frontmatter to realign
├── 01-VERIFICATION.md                      # existing source-of-truth evidence

.planning/phases/09-privacy-traceability-command-disclosure-alignment/
├── 09-RESEARCH.md
├── 09-PLAN.md / 09-0X-PLAN.md
└── 09-VERIFICATION.md                      # likely needed for audit closure

android/app/src/main/java/com/github/shekohex/whisperpp/
├── privacy/
│   ├── PrivacyDisclosureFormatter.kt
│   ├── SecureFieldDetector.kt
│   ├── SendPolicyRepository.kt
│   └── SecretsStore.kt
├── WhisperInputService.kt
└── ui/
    ├── keyboard/KeyboardScreen.kt
    └── settings/PrivacySafetyScreen.kt
```

### Pattern 1: Single privacy source for both settings and IME
**What:** `PrivacyDisclosureFormatter` remains the only place that knows what leaves the device and where it goes.

**When to use:** Always. Especially for command mode, where both settings and first-use UI must change together.

**Example:**
```kotlin
// Source: android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt
val commandDisclosure = remember(commandProvider, validation.effective.commandText.modelId, useContext) {
    PrivacyDisclosureFormatter.disclosureForCommand(
        provider = commandProvider,
        selectedModelId = validation.effective.commandText.modelId,
        useContext = useContext,
    )
}
```

### Pattern 2: Command disclosure gate after text confirmation, before recording
**What:** Command mode should confirm its target text first (selection or clipboard), then show the command disclosure, then start recording spoken instruction.

**When to use:** For every command run before `recorderManager.start(recordedAudioFilename)`.

**Why:** The disclosure needs accurate command context, but consent must still happen before audio capture.

### Pattern 3: Shared external-send gate, not per-feature checks
**What:** Keep `shouldBlockExternalSend()` as the single gate for secure fields and per-app policy.

**When to use:** Before command entry, before instruction recording, before STT, and before LLM transform.

**Example:**
```kotlin
// Source: android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
private fun shouldBlockExternalSend(): Boolean {
    refreshExternalSendBlock(currentInputEditorInfo)
    return isExternalSendBlocked()
}
```

### Pattern 4: Traceability recovery through machine-readable artifacts
**What:** Close the audit gap by updating the artifacts the tooling already reads: `requirements-completed` frontmatter, `REQUIREMENTS.md`, and phase verification docs.

**When to use:** After runtime/tests land; do not leave closure in prose-only notes.

### Anti-Patterns to Avoid
- **Showing command disclosure after `onCommandStopListening()`:** too late; audio was already captured/uploaded.
- **Leaving command disclosure text-only:** command mode uses both STT and text-transform endpoints.
- **Duplicating disclosure copy in IME and settings:** guarantees future drift.
- **Closing traceability only in `REQUIREMENTS.md`:** audit drift also lives in summary frontmatter and phase verification linkage.
- **Replacing the shared secure/app-policy gate with command-specific checks:** breaks the locked “single IME gate” decision in `STATE.md`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Command disclosure endpoint parsing | Manual URL string slicing | OkHttp `HttpUrl` via `PrivacyDisclosureFormatter` | Existing formatter already derives base URL/path safely. |
| Secret storage | New encryption wrapper or plaintext prefs | `SecretsStore` + runtime `provider.copy(apiKey=...)` | Already Keystore-backed and wired through repo/runtime. |
| Per-app privacy rules | New map/store in command code | `SendPolicyRepository` | Existing default-allow semantics and settings UI already depend on it. |
| Secure-field explanation UI | New command-only modal | Existing blocked explanation sheet + fallback activity | Reuses tested IME-safe behavior. |
| Traceability closure | Custom markdown note | Existing summary frontmatter + `*-VERIFICATION.md` + `REQUIREMENTS.md` | These are the artifacts the audit already reads. |

**Key insight:** Phase 9 should add almost no new privacy infrastructure; it should make the existing infrastructure truthful, correctly ordered, and traceable again.

## Common Pitfalls

### Pitfall 1: Fixing copy but not timing
**What goes wrong:** Settings show accurate command disclosures, but IME still records audio before consent.
**Why it happens:** `runCommandTransformOrError()` currently gates too late in the command pipeline.
**How to avoid:** Move command disclosure gating to the point after text confirmation and before `startCommandListening()` starts the recorder.
**Warning signs:** `recorderManager.start(...)` can run before `awaitFirstUseDisclosure(...)` for command mode.

### Pitfall 2: Treating command mode as one outbound request
**What goes wrong:** Disclosure shows only the text-model endpoint.
**Why it happens:** `disclosureForCommand()` currently models command mode as planned text-only behavior.
**How to avoid:** Represent command mode as a multi-endpoint flow: instruction-audio STT hop plus text-transform hop.
**Warning signs:** Disclosure copy contains “planned” or omits the STT provider endpoint.

### Pitfall 3: Reopening secure/app-policy regressions while touching command flow
**What goes wrong:** Command alignment accidentally bypasses `PRIV-01`/`PRIV-04` protections.
**Why it happens:** Disclosure-move refactors can introduce a new path that starts recording without the shared gate.
**How to avoid:** Keep `shouldBlockExternalSend()` checks before entry, before recording, before STT, and before transform.
**Warning signs:** New helper methods read prefs/providers directly but do not call the shared gate.

### Pitfall 4: Restoring checklist status without restoring artifact linkage
**What goes wrong:** `REQUIREMENTS.md` looks complete, but summaries or verification still disagree.
**Why it happens:** The audit gap is multi-artifact, not a single checklist bug.
**How to avoid:** Update summary frontmatter, requirement status, and any phase-09 verification evidence together.
**Warning signs:** Phase 01 summaries still lack `requirements-completed`, or Phase 09 closes code gaps without updating traceability files.

### Pitfall 5: Regressing the logging boundary while adding evidence
**What goes wrong:** Tests/docs mention privacy-safe logging, but code drifts toward `BODY` logging or raw payload logging.
**Why it happens:** Logging behavior is currently enforced by convention, not automated regression tests.
**How to avoid:** Add a focused unit test around logger configuration/sanitization and keep `HEADERS` as the highest enabled level.
**Warning signs:** Any change to `HttpLoggingInterceptor.Level.BODY`, raw response-body logging, or unredacted auth headers.

### Pitfall 6: Reintroducing the stale “global first-use” assumption
**What goes wrong:** Planner designs a single acknowledgment flag and fights current code/state.
**Why it happens:** Old Phase 01 plan text described one global gate, but current repo state uses mode-specific flags (`dictation`, `enhancement`, `command`).
**How to avoid:** Keep the existing per-mode disclosure flags and align command mode to them.
**Warning signs:** New work proposes replacing `DISCLOSURE_SHOWN_COMMAND_TEXT` with one cross-mode boolean.

## Code Examples

Verified patterns from repo and official docs:

### Secure-field detection signals
```kotlin
// Source: android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecureFieldDetector.kt
private fun isPasswordLike(inputType: Int): Boolean {
    val klass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    val textPasswordLike = klass == InputType.TYPE_CLASS_TEXT &&
        (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            )
    val numberPasswordLike =
        klass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

    return textPasswordLike || numberPasswordLike
}
```

### Android official meaning of `IME_FLAG_NO_PERSONALIZED_LEARNING`
```text
Flag of imeOptions: used to request that the IME should not update any personalized
data such as typing history and personalized language model based on what the user typed on
this text editing object.
```
Source: `https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING`

### Redacted logging boundary
```kotlin
// Source: android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt
private val loggingInterceptor = HttpLoggingInterceptor { message ->
    val sanitized = message.replace(Regex("([?&]key=)[^&\\s]+"), "${'$'}1REDACTED")
    Log.d("HTTP_SmartFixer", sanitized)
}.apply {
    level = HttpLoggingInterceptor.Level.NONE
    redactHeader("Authorization")
    redactHeader("x-goog-api-key")
}
```

### Dictation disclosure pattern to mirror for command pre-capture gating
```kotlin
// Source: android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
val disclosure = PrivacyDisclosureFormatter.disclosureForDictation(
    provider = provider,
    selectedModelId = modelId,
    useContext = useContext,
)
val disclosureDecision = awaitFirstUseDisclosure(
    mode = FirstUseDisclosureMode.DICTATION_AUDIO,
    disclosure = disclosure,
)
if (disclosureDecision != FirstUseDisclosureDecision.CONTINUE) {
    return@launch
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Command disclosure placeholder (“planned”) | Command mode is a shipped STT + text-transform pipeline | Phase 05 delivery | Disclosure must describe real current behavior, not a placeholder. |
| One global first-use acknowledgment (older Phase 01 planning text) | Mode-specific disclosure flags: `DISCLOSURE_SHOWN_DICTATION_AUDIO`, `...ENHANCEMENT_TEXT`, `...COMMAND_TEXT` | Current repo state / backup model | Phase 9 should align command mode to the existing per-mode pattern, not collapse it. |
| Phase 01 verification-only privacy closure | Audit now expects summary frontmatter, requirement checklist, and verification evidence to agree | Milestone audit 2026-03-12 | Traceability work must update machine-readable docs, not just code. |

**Deprecated/outdated:**
- `PrivacyDisclosureFormatter.disclosureForCommand()` copy that says command mode is planned.
- Any command disclosure that lists only one endpoint.
- Phase 01 summaries without `requirements-completed` entries for the privacy requirements they satisfied.

## Open Questions

1. **How should command disclosure represent two endpoints cleanly?**
   - What we know: command mode uploads instruction audio to STT, then sends instruction + selected text to the text model/provider.
   - What's unclear: whether `EndpointDisclosure` needs labels/purposes or whether ordered rows are enough.
   - Recommendation: extend the disclosure model so command rows are explicitly labeled (for example, `Instruction audio transcription` and `Text transform`) rather than relying on unlabeled endpoint order.

2. **Which artifacts should Phase 9 update for privacy traceability closure?**
   - What we know: the audit cites `01-VERIFICATION.md`, missing/empty `requirements-completed` in Phase 01 summaries, and unchecked rows in `REQUIREMENTS.md`.
   - What's unclear: whether roadmap/phase mapping should remain Phase 09-owned or point back to Phase 01 for PRIV-01..05.
   - Recommendation: at minimum update Phase 01 summary frontmatter plus `REQUIREMENTS.md` status/checklists, and write explicit Phase 09 verification evidence that cites the restored linkage.

3. **Where exactly should the command disclosure modal appear in the UX?**
   - What we know: it must appear before audio capture, but after the command text source is known.
   - What's unclear: whether it belongs immediately on command entry for selected text, or only at the transition from text-confirmed to listening.
   - Recommendation: trigger it from the transition into listening (`startCommandListening()` path) after `commandConfirmedText` is set and before recorder start.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 `4.13.2`, Compose UI Test, AndroidJUnitRunner `1.6.2` |
| Config file | none — Gradle/Android defaults in `android/app/build.gradle.kts` |
| Quick run command | `./android/gradlew testDebugUnitTest` |
| Full suite command | `./android/gradlew testDebugUnitTest connectedDebugAndroidTest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PRIV-01 | Secure fields block dictation/command and explanation copy stays non-empty | unit + androidTest | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.SecureFieldDetectorTest"` | ❌ Wave 0 |
| PRIV-02 | API keys stay out of serialized provider/backup payloads and are restored only through credentials flow | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsBackupRepositoryExportTest"` | ✅ |
| PRIV-03 | Network logging defaults to `NONE`, maxes at redacted `HEADERS`, never `BODY` | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.NetworkLoggingPrivacyTest"` | ❌ Wave 0 |
| PRIV-04 | Per-app send-policy rules persist and are enforced before any external send path | unit + androidTest | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.SendPolicyRepositoryTest"` | ❌ Wave 0 |
| PRIV-05 | Privacy & Safety and IME first-use flows show accurate command/audio/text disclosures and endpoints | unit + androidTest | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatterTest"` | ✅ |
| CMD-03 | Command flow discloses before instruction recording, then transcribes and transforms with effective providers | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.command.CommandDisclosureFlowTest"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./android/gradlew testDebugUnitTest`
- **Per wave merge:** `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatterTest" --tests "com.github.shekohex.whisperpp.data.SettingsBackupRepositoryExportTest"`
- **Phase gate:** `./android/gradlew testDebugUnitTest connectedDebugAndroidTest` before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/SecureFieldDetectorTest.kt` — covers `PRIV-01`
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/SendPolicyRepositoryTest.kt` — covers `PRIV-04`
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/NetworkLoggingPrivacyTest.kt` — covers `PRIV-03`
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/command/CommandDisclosureFlowTest.kt` — covers `CMD-03` + `PRIV-05`
- [ ] `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreenUiTest.kt` — covers settings disclosure/reset/send-policy UI for `PRIV-04`/`PRIV-05`

## Sources

### Primary (HIGH confidence)
- Repo source: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` — command pipeline, first-use disclosure timing, shared external-send gate
- Repo source: `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt` — current disclosure model and command placeholder copy
- Repo source: `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecureFieldDetector.kt` — secure-field detection rules
- Repo source: `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecretsStore.kt` — Keystore-backed AES/GCM secret storage
- Repo source: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt` — settings disclosure surface and per-app send-policy UI
- Repo source: `.planning/v1.0-MILESTONE-AUDIT.md` — explicit audit gap and traceability failure details
- Official docs: `https://developer.android.com/reference/android/text/InputType` — password-related input variations
- Official docs: `https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING` — no-personalized-learning flag semantics
- Official docs: `https://developer.android.com/privacy-and-security/keystore` — Android Keystore storage model and AES support
- Official docs: `https://square.github.io/okhttp/5.x/logging-interceptor/okhttp3.logging/-http-logging-interceptor/redact-header.html` — auth-header redaction support
- Official docs: `https://square.github.io/okhttp/5.x/logging-interceptor/okhttp3.logging/-http-logging-interceptor/-level/` — `NONE`/`HEADERS`/`BODY` logging level behavior

### Secondary (MEDIUM confidence)
- Repo docs: `.planning/phases/01-privacy-and-safety-controls/01-VERIFICATION.md` — verified privacy evidence base to relink
- Repo docs: `.planning/phases/05-command-mode-presets/05-03-PLAN.md` and `05-03-SUMMARY.md` — command-mode shipped intent and traceability claims
- Repo tests: `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupRepositoryExportTest.kt` — existing evidence for secret exclusion and privacy backup state

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - confirmed from current repo dependencies and platform/OkHttp docs.
- Architecture: MEDIUM - command disclosure restructuring is clear, but exact traceability artifact ownership still needs implementation choice.
- Pitfalls: HIGH - audit findings plus direct code inspection show the main failure modes unambiguously.

**Research date:** 2026-03-13
**Valid until:** 2026-04-12
