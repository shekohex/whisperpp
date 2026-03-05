# Phase 4: Prompts, Profiles & Enhancement - Research

**Researched:** 2026-03-04
**Domain:** Android IME per-app/per-language runtime selection, post-dictation enhancement, segment replacement + undo
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Prompt composition: `global base prompt + mapped profile append + app-specific append`.
- App mapping can explicitly disable append (`no-append`) while still keeping global base prompt.
- Model/provider precedence: `app override > per-language default > global default`.
- Partial app overrides cascade missing fields from per-language, then global defaults.
- Per-language defaults resolve STT and text channels independently.
- Unmapped apps use global profile path plus per-language defaults.
- If mapped profile is deleted: fallback to global behavior and flag mapping as needing attention.
- If override references missing/invalid provider-model: fallback down precedence and surface a non-blocking warning.
- Enhancement auto-runs on every successful dictation stop, including short transcripts.
- Empty or punctuation-only transcripts skip enhancement and insert as-is.
- If enhancement cannot run (policy block, invalid model, etc.), insert raw transcript and show reason.
- Timeout strategy: balanced wait before raw fallback; offline path still attempts then times out.
- Retry policy: one quick retry for transient failures (including rate-limit), then fallback to raw.
- On fallback, show detailed notice each time; include provider + model; auto-dismiss after short duration.
- Fallback raw insertion is verbatim STT output (no local rewrite).
- If fallback reason is intentional app send-policy block, use info-style notice (not error-style).
- Keep trying enhancement on each dictation even after repeated prior failures (no auto-disable).
- Enhancement is single-flight: new dictation cannot start while active dictation/enhancement flow is running.
- If focus/app changes before enhancement returns, do not apply replacement.
- Insert raw transcript immediately, then replace that dictated segment in place on enhancement success.
- Replacement targets original captured dictated segment, not current caret location.
- If user edits raw segment before enhancement returns, still force replacement with enhancement output.
- Undo restores the exact raw dictated segment in the original replacement range.
- Undo scope is single latest enhancement replacement.
- Undo remains valid even if user manually edited enhanced text before tapping undo.
- Caret lands at end of replaced segment after successful replacement.
- Replacement is segment-scoped only; surrounding text must not be modified.
- Expose enhancement undo via temporary IME undo action/chip, available until next dictation/replacement.
- Even when replacement overwrote interim edits, undo restore source remains original raw transcript.
- App mapping picker: searchable installed-app list plus manual package-name fallback.
- Mapping granularity: one profile per app package.
- Unmapped apps remain unmapped and run default behavior.
- Per-app overrides (append/STT/text) are edited inside mapping detail screen.
- Include user and system apps in mapping picker.
- Support bulk multi-select mapping (assign one profile to multiple apps in one action).
- Keep mappings by package even if app is uninstalled (reapply if app returns).
- If profile is deleted while referenced, keep mapping row flagged and fallback behavior active.

### OpenCode's Discretion
- Exact timeout values for "balanced wait" and "quick retry".
- Exact UI component choice and wording style for detailed fallback notices and info-style policy notices.
- App picker sorting/grouping defaults and system-app visual treatment.

### Deferred Ideas (OUT OF SCOPE)
- Redo action after enhancement undo (separate capability; future phase).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PROF-01 | User can define a global base prompt | Add explicit global prompt key + editor UI; stop coupling enhancement prompt to provider-level prompt field. |
| PROF-02 | User can create prompt profiles and manually map apps (by package name) to a profile | Add prompt profile schema + per-package mapping schema; reuse installed-app picker pattern from `PrivacySafetyScreen.kt`. |
| PROF-03 | Per-app mapping can override prompt append and chosen STT/text providers/models | Implement a single runtime resolver used by both dictation and enhancement paths in `WhisperInputService.kt`. |
| PROF-04 | User can configure per-language defaults (language -> STT model/provider and text model/provider) | Reuse/extend existing `LanguageProfile` + `PROFILES_JSON`; add UI and runtime application for both channels independently. |
| ENH-01 | After dictation stops, enhancement runs by default and pastes enhanced text (fallback to raw on failure) | Convert current enhancement from pre-insert transform to raw-first then async replace/fallback notices. |
| ENH-02 | Enhancement uses effective prompt = base prompt + per-app prompt append | Add prompt composition layer with locked precedence and explicit `no-append` behavior. |
| ENH-03 | On enhancement success, auto-replace dictated segment in place | Capture insertion range at raw insert; apply replacement by original segment bounds, not caret. |
| ENH-04 | User can undo last enhancement replacement (restores raw transcript) | Add separate enhancement undo entry (single latest) independent of current dictation multi-step undo stack. |
</phase_requirements>

## Summary

Phase 4 is mostly integration work across existing pieces, not a new platform stack. The repo already has: provider/model entities and selection keys (`ProviderModels.kt`, `MainActivity.kt`), DataStore JSON persistence + migration patterns (`SettingsRepository.kt`), app package discovery and manual package fallback (`PrivacySafetyScreen.kt` + `<queries>` in `AndroidManifest.xml`), and a dictation pipeline with focus safety + insertion/undo primitives (`WhisperInputService.kt`, `DictationController.kt`).

The key gap is that enhancement is currently a single pre-insert transform (`transcriptionCallback` computes `processedText` and inserts once). Phase 4 needs a two-stage pipeline: insert raw immediately, then enhancement replacement on the original segment, with fallback notices and a separate enhancement undo contract. Also missing: per-app profile mappings, per-language defaults application at runtime, and deterministic precedence resolution wired into both STT and text model selection.

**Primary recommendation:** implement one `EffectiveRuntimeConfigResolver` (app + language + global + provider validation) and route both `startTranscription(...)` and post-dictation enhancement through it; then split undo into `dictation insertion undo` and `enhancement replacement undo` with explicit segment boundaries.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Android IME APIs (`InputMethodService`, `InputConnection`, `EditorInfo`) | SDK 34 (min 24) | Focus lifecycle, segment replacement, composing behavior | Already the runtime surface for dictation/replacement. |
| Kotlin coroutines | Existing project stdlib | Single-flight orchestration, timeout/retry envelopes | Existing async pattern in `WhisperInputService`, `WhisperTranscriber`, `SmartFixer`. |
| DataStore Preferences | `androidx.datastore:datastore-preferences:1.0.0` | Persist profiles/mappings/defaults with migration flags | Existing settings storage + migration style in repo. |
| OkHttp | `4.12.0` | STT/LLM transport + cancellation | Existing implementation in `WhisperTranscriber` and `SmartFixer`. |
| Compose Material3 | BOM `2024.02.00` | Settings screens + keyboard chips/notices | Existing UI framework in settings + IME view. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Gson | `2.10.1` | JSON encode/decode for mapping/profile payloads | Keep consistency with existing `SettingsRepository` JSON persistence. |
| Android package visibility (`<queries>`) | platform | App picker visibility for launcher apps | Already declared; required for installed-app mapping UX. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| DataStore JSON blobs | Room entities | Room is heavier for this phase; current codebase already standardizes on DataStore JSON + migration flags. |
| Enhancement notice in system Toast only | In-IME transient banner/chip | Toast is easiest; in-IME component provides richer style (info/error) required by locked notice behavior. |
| Reuse dictation undo stack for enhancement undo | Dedicated enhancement undo entry | Reuse conflicts with locked ENH behavior (single latest, restore even after edits). |

**Installation:**
```bash
# No new dependency required for baseline Phase 4 plan.
```

## Architecture Patterns

### Where to implement in this codebase

| Concern | Primary Files | Planned Change |
|--------|----------------|----------------|
| Settings keys + schema roots | `MainActivity.kt`, `data/SettingsRepository.kt`, `data/ProviderModels.kt` | Add prompt/profile/mapping keys + models + migration flags. |
| Effective runtime resolution (precedence/fallback) | `WhisperInputService.kt` (+ new resolver in `data/` or `dictation/`) | One resolver for STT/text provider/model + prompt composition + warnings. |
| Dictation raw insert + enhancement replace flow | `WhisperInputService.kt`, `dictation/DictationController.kt`, `SmartFixer.kt` | Convert to raw-first insertion + async replacement with timeout/retry/fallback notice. |
| Segment tracking + enhancement undo | `dictation/DictationUndoEntry.kt` (or new `EnhancementUndoEntry.kt`), `DictationController.kt`, `KeyboardScreen.kt` | Add separate single-entry enhancement undo state; keep dictation undo behavior intact. |
| Settings UI for prompts/profiles/mappings/language defaults | `ui/settings/SettingsScreen.kt` (new routes/screens) | Add profile CRUD, app picker (bulk + manual), per-language defaults editor. |
| App discovery/reuse patterns | `ui/settings/PrivacySafetyScreen.kt`, `AndroidManifest.xml` | Reuse launcher query + manual package fallback + uninstalled mapping retention pattern. |

### Recommended Project Structure

```text
android/app/src/main/java/com/github/shekohex/whisperpp/
├── data/
│   ├── PromptProfiles.kt              # Prompt profile + mapping models
│   ├── RuntimeSelectionResolver.kt    # Precedence + fallback warnings
│   └── SettingsRepository.kt          # JSON persistence + migrations
├── dictation/
│   ├── DictationController.kt         # raw insert capture + enhancement hooks
│   └── EnhancementUndoEntry.kt        # single latest replacement undo contract
├── ui/settings/
│   └── SettingsScreen.kt              # new routes/screens for profiles/mappings/defaults
└── WhisperInputService.kt             # orchestration glue for dictation+enhancement
```

### Pattern 1: Single runtime resolver for precedence
**What:** Resolve effective STT/text provider+model and prompt append stack from app package + language + global defaults.
**When to use:** Every dictation send and every enhancement invocation.
**Example:**
```kotlin
val effective = runtimeResolver.resolve(
    packageName = currentInputEditorInfo?.packageName,
    languageCode = prefs[LANGUAGE_CODE] ?: "auto",
    providers = repository.providers.first(),
    settings = prefs,
)
// Source: repo pattern in validateSelections(...) + new phase resolver
```

### Pattern 2: Raw-first, replace-later enhancement pipeline
**What:** Insert raw transcript immediately, capture segment, run enhancement async, replace segment if still safe.
**When to use:** Every successful dictation stop (except empty/punctuation-only skip).
**Example:**
```kotlin
val rawEntry = dictationController.insertRaw(token, rawText)
val enhancement = enhancementRunner.run(rawText, effective)
if (enhancement is Success && focusStillSame(rawEntry.focusKey)) {
    dictationController.replaceSegment(rawEntry.range, enhancement.text)
    enhancementUndo = EnhancementUndoEntry(rawEntry.range, raw = rawText, enhanced = enhancement.text)
}
```

### Pattern 3: Enhancement undo is independent from dictation undo
**What:** Keep one latest enhancement replacement undo entry separate from existing dictation stack.
**When to use:** After successful replacement only; clear on next dictation/replacement.
**Example:**
```kotlin
data class EnhancementUndoEntry(
    val focusKey: FocusKey,
    val rangeStart: Int,
    val rangeEndAtReplace: Int,
    val rawText: String,
)
```

### Anti-Patterns to Avoid
- **Divergent resolution logic in multiple call sites:** if STT and enhancement compute precedence separately, behavior drifts and bugs are hard to trace.
- **Using caret location for enhancement replacement:** violates locked “segment-only replacement”.
- **Reusing text-equality guard from dictation undo for enhancement undo:** fails locked “undo still valid after manual edits.”
- **Treating enhancement failure as silent raw use:** violates required fallback notice behavior.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Installed app discovery | Raw package scans with broad permissions | Existing launcher-intent query pattern (`PrivacySafetyScreen.kt`) + `<queries>` manifest | Already policy-compliant and implemented. |
| Cross-field insert safety | Ad-hoc booleans | Existing `FocusKey` pattern in `DictationController` | Proven in Phase 3 for late-event safety. |
| Provider/model validity checks | Custom checks per screen/service | Centralized validation/resolver from `validateSelections` style | Prevents inconsistent fallback behavior. |
| Retry/timeout orchestration | Nested callbacks in service | Coroutine envelope around `SmartFixer.fix(...)` | Clear single-flight + deterministic fallback paths. |

**Key insight:** this phase fails if precedence/undo logic is fragmented; centralize resolution + segment bookkeeping first, then wire UI.

## Common Pitfalls

### Pitfall 1: Focus changed before enhancement result returns
**What goes wrong:** replacement lands in another field/app or stale field.
**Why it happens:** async enhancement outlives editor focus.
**How to avoid:** capture `FocusKey` with raw insertion; check current focus before replacement; drop replacement on mismatch.
**Warning signs:** user sees enhanced text in wrong app/field.

### Pitfall 2: Undo boundary conflicts (dictation vs enhancement)
**What goes wrong:** undo removes wrong segment or enhancement undo disappears unexpectedly.
**Why it happens:** using one shared stack for two different contracts.
**How to avoid:** keep separate enhancement undo entry (single latest) and explicit precedence when user taps undo chip.
**Warning signs:** repeated undo taps act inconsistently after enhancement.

### Pitfall 3: Overwrite-user-edits policy not honored
**What goes wrong:** enhancement skips replacement when raw segment was edited, violating locked behavior.
**Why it happens:** safety checks incorrectly require text equality.
**How to avoid:** replace by captured segment range, not by current selected text equality.
**Warning signs:** enhancement succeeds but raw text remains after user edited during wait.

### Pitfall 4: Invalid app/language overrides crash or block dictation
**What goes wrong:** null provider/model or invalid IDs stop flow.
**Why it happens:** partial override cascade not implemented centrally.
**How to avoid:** resolver must cascade `app override > language default > global`, channel-by-channel, with warning + fallback.
**Warning signs:** setup-required errors for mapped apps that should inherit defaults.

### Pitfall 5: Timeout/retry behavior creates long UI stalls
**What goes wrong:** user waits too long before raw text appears or replacement decision.
**Why it happens:** enhancement done before insertion or no bounded timeout.
**How to avoid:** raw insert first; enhancement timeout envelope + one transient retry only.
**Warning signs:** keyboard stuck in SmartFixing state.

### Pitfall 6: Notice style does not differentiate policy block vs errors
**What goes wrong:** app-policy intentional block shown as error.
**Why it happens:** single generic fallback message path.
**How to avoid:** structured enhancement result reason enum, map to info-style vs error-style UI copy.
**Warning signs:** confusing red error notices when policy is expected behavior.

## Code Examples

Verified patterns from current code and official docs:

### IME lifecycle hooks for focus-safe session updates
```kotlin
override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
    focusInstanceId += 1
    dictationController.onFocusChanged(FocusKey.from(attribute, focusInstanceId))
}
override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    focusInstanceId += 1
    dictationController.onFocusChanged(FocusKey.from(info, focusInstanceId))
}
// Source: WhisperInputService.kt
```

### Composing lifecycle for segment replacement operations
```kotlin
ic.beginBatchEdit()
try {
    ic.setSelection(start, end)
    ic.commitText(replacement, 1)
} finally {
    ic.endBatchEdit()
}
// Source: InputConnection API + existing begin/endBatchEdit pattern in DictationController.kt
```

### App picker with installed + manual package retention
```kotlin
val merged = (launcherApps + manualRules)
    .distinctBy { it.packageName }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
// Source: PrivacySafetyScreen.kt
```

## State of the Art

| Old Approach (current repo) | Current Approach needed for Phase 4 | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Enhancement transforms text before first insert | Raw-first insert, then async segment replacement with fallback notice | Phase 4 | Meets ENH-01/03/04 and avoids delayed insertion UX. |
| Global provider/model only | Deterministic layered resolver (app > language > global) | Phase 4 | Enables PROF-03/04 and reduces setup friction per app/language. |
| Generic dictation undo only | Dedicated enhancement undo contract | Phase 4 | Honors overwrite + restore semantics from locked decisions. |

**Deprecated/outdated in this phase context:**
- Enhancement prompt source = `provider.prompt` fallback chain alone.
  - Replace with explicit global base prompt + profile append + app append composition path.

## Suggested Verification Strategy

### Fast unit tests (primary)
- `RuntimeSelectionResolverTest`
  - precedence/cascade for STT and text independently
  - invalid override fallback + warning generation
  - deleted profile mapping flagged + global fallback
- `PromptComposerTest`
  - composition order, `no-append`, unmapped behavior
- `EnhancementPolicyTest`
  - punctuation-only skip
  - timeout + one retry then raw fallback
  - policy-block reason classified info-style
- `EnhancementReplacementTest` (controller-level with fake `InputConnection`)
  - raw insert capture range
  - replacement uses original segment, not caret
  - focus mismatch drops replacement
  - undo restores raw for latest replacement

### Minimal instrumentation tests (targeted)
- Compose UI test for mapping screen:
  - searchable list + bulk assign + manual package add
  - deleted-profile mapping row shows flagged state
- Optional IME interaction instrumentation:
  - replacement does not apply after focus change event

### Commands
- Quick loop: `./android/gradlew testDebugUnitTest`
- UI smoke: `./android/gradlew connectedDebugAndroidTest`

## Required Migrations / Settings Schema Changes

1. **New persisted keys/json payloads** (DataStore Preferences):
   - Global base prompt key (string)
   - Prompt profiles JSON (list: id, name, promptAppend)
   - App mapping JSON (package -> profile + per-app overrides including append mode and optional STT/text overrides)
   - (If keeping existing `PROFILES_JSON` as language defaults) formalize schema and validation for per-language defaults.

2. **Migration flags in `SettingsRepository`** (same style as phase 1/2):
   - `PROMPTS_PROFILES_SCHEMA_V1_MIGRATION_DONE`
   - optional migration to seed global base prompt from legacy `SMART_FIX_PROMPT` when present.

3. **Validation/sanitization pass on load**:
   - strip blank package names/profile IDs
   - keep orphan mappings (deleted profile) but tag as invalid reference
   - clear impossible provider/model overrides only for that override scope (not whole mapping)

4. **Import/export compatibility**:
   - extend export payload to include new prompt/profile/mapping settings (current export has explicit key allowlists + `profiles` payload)
   - consider export version bump if schema changes are non-backward-compatible.

## Open Questions

1. **Can enhancement undo be guaranteed after arbitrary external edits in all editors?**
   - What we know: locked behavior requires restore even if user edited enhanced text.
   - What's unclear: some editors provide weak selection/range guarantees via `InputConnection`.
   - Recommendation: design around explicit segment anchors + best-effort range replacement; add focused instrumentation on common editors.

2. **How to surface non-blocking warnings in IME reliably across host apps?**
   - What we know: IME surface reliability has prior issues (already solved with fallback activity in privacy flow).
   - What's unclear: whether fallback notices should be toast-only or in-keyboard chip/banner for style differentiation.
   - Recommendation: implement in-keyboard transient notice with fallback-to-toast path.

3. **Legacy prompt behavior compatibility**
   - What we know: current enhancement uses `provider.prompt`/`SMART_FIX_PROMPT`.
   - What's unclear: migration priority when both provider prompt and smart-fix prompt exist.
   - Recommendation: prefer explicit one-time migration rule and document precedence in settings UI.

## Sources

### Primary (HIGH confidence)
- Repository source of truth:
  - `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/PrivacySafetyScreen.kt`
  - `android/app/src/main/AndroidManifest.xml`
- Android official docs:
  - `InputMethodService` API: https://developer.android.com/reference/android/inputmethodservice/InputMethodService
  - `InputConnection` API: https://developer.android.com/reference/android/view/inputmethod/InputConnection
  - Manifest `<queries>`: https://developer.android.com/guide/topics/manifest/queries-element

### Secondary (MEDIUM confidence)
- Android package visibility overview (official URL referenced via search):
  - https://developer.android.com/guide/components/package-visibility
- DataStore migration behavior (official docs family, verified by search grounding):
  - https://developer.android.com/topic/libraries/architecture/datastore

### Tertiary (LOW confidence)
- Community discussions around IME selection/range edge behavior across editors (used only to flag risks; not normative).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - fully derived from current Gradle + existing code usage.
- Architecture: MEDIUM - core patterns are clear; editor-specific range behavior can vary.
- Pitfalls: MEDIUM - strongly supported by current IME flow and locked decisions; some host-editor variance remains.

**Research date:** 2026-03-04
**Valid until:** 2026-04-03
