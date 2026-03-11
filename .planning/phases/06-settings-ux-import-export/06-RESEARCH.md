# Phase 6: Settings UX + Import/Export - Research

**Researched:** 2026-03-09
**Domain:** Android Compose settings IA, DataStore-backed config UX, encrypted backup/restore
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
### Restore behavior
- Import must offer a clear choice between overwrite and merge each time.
- The overwrite/merge choice should be presented in a bottom sheet immediately after file selection, then followed by a preview and explicit confirmation before applying changes.
- Merge should support including or excluding whole categories before apply.
- For merge conflicts, imported values win by default.
- If the import file is partly invalid or unsupported, restore valid parts and show a report of skipped items.
- After restore, keep the user in settings and show a completion summary.
- If imported changes make active selections invalid on the current device, clear those selections and explain what needs attention.

### Settings home structure
- Settings home should feel like grouped section cards, not a flat utility list or analytics dashboard.
- Navigation should use nested groups first rather than exposing every major area directly on the top-level screen.
- Each top-level row/card should show one concise status line.
- Setup-critical items should appear first.

### Backup contents
- Backups should be password-encrypted and required for every export; there is no plain export path.
- Export requires password entry plus confirmation.
- Encrypted backups may include sensitive/provider data.
- Import should request the password immediately after file pick, before preview/apply.
- Export must show exactly which categories are included, including sensitive contents.
- Backup identity should include app version and export timestamp.
- Cross-version imports should be attempted with warnings, not blocked outright.
- If credentials are missing or unusable after import, show a repair checklist pointing to the affected providers/selections.

### Guidance and feedback
- Incomplete setup or broken selections should surface as a persistent banner on settings home.
- Unconfigured sections should use short, action-oriented guidance copy.
- Major actions like export/import/save should show an inline summary plus toast feedback.
- The top-bar help affordance should provide contextual tips for the current settings area.

### OpenCode's Discretion
- Exact Material 3 component choices, spacing, and theming details.
- Exact labels for nested groups and section-card presentation.
- Exact preview/report layout for import summaries, skipped-item reports, and repair checklist screens.

### Deferred Ideas (OUT OF SCOPE)
None - discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| UI-01 | Settings UI uses Material 3 (Material You) components + theming | Reuse existing Compose Material 3 stack, keep `MaterialTheme`, `TopAppBar`, `Card`, `ListItem`, dialogs/bottom sheets, and polish IA rather than swapping UI stack. |
| SET-01 | All core behavior is configurable in settings (providers/models, prompts, per-app/per-language overrides, dictation/enhancement/command toggles) | Current repo already has most editors; phase should reorganize discovery, statuses, guidance, and route structure so every existing setting is reachable and summarized coherently. |
| SET-02 | User can export/import full settings (providers/models/profiles/mappings/toggles) and share the export file; settings can be backed up and restored | Requires a new versioned encrypted backup envelope, full-category snapshot coverage, category-aware merge/overwrite preview, partial-import reporting, and post-restore repair flow. |
</phase_requirements>

## Summary

Phase 6 is mostly an integration/re-architecture phase, not a new-core-capability phase. The app already uses Compose Material 3, Navigation Compose, DataStore Preferences, Gson, and SAF-style document pickers; the main work is reorganizing the current settings surfaces into a clearer information architecture and replacing the current raw JSON import/export with a versioned encrypted backup flow.

The biggest planning fact: current import/export is far from requirement-complete. `MainSettingsScreen` exports unencrypted JSON and imports with a one-step destructive confirmation; `SettingsRepository.exportSettings()` only captures providers, language profiles, and a narrow legacy preference subset; it omits prompt profiles, app mappings, per-app send rules, disclosure/reset flags, the newer provider-selection keys, and secrets restoration behavior. Also, `ServiceProvider.apiKey` is `@Transient`, so current backups cannot include provider credentials even though Phase 6 explicitly allows encrypted backups to contain them.

Plan this phase around two parallel seams: 1) settings IA/home polish and route cleanup; 2) backup domain model + import pipeline. The backup pipeline needs its own typed snapshot model, category-level diff/preview/reporting, and a post-apply validation step using existing selection validation logic so invalid imported selections are cleared with actionable repair guidance.

**Primary recommendation:** Keep the existing Compose/DataStore/Gson stack, add a dedicated encrypted backup envelope + category-aware import/export repository, and rebuild settings home around grouped section cards with status lines, persistent setup banner, and nested destinations.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose Material 3 | BOM `2024.02.00` | Settings UI, dialogs, sheets, cards, app bars | Already in repo; satisfies UI-01 without introducing a new settings framework. |
| Navigation Compose | `2.7.7` | Nested settings destinations | Already powers current settings routing; fits grouped-home -> detail flows. |
| Preferences DataStore | `1.0.0` | Persist app settings transactionally | Official Android replacement for `SharedPreferences`; async + transactional edits. |
| Gson | `2.10.1` | Snapshot/backup JSON serialization | Already used for provider/profile persistence; lowest-friction path for backup payloads. |
| Storage Access Framework | platform | Export/import file picking | Official Android path for user-selected document create/open. |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Android JCA (`Cipher`, `GCMParameterSpec`) | platform / Java 17 APIs | AEAD encryption for backup payload | Use for password-encrypted backup envelope. |
| Android Keystore (`SecretsStore` pattern) | platform | Existing secure local secret storage | Reuse for on-device provider keys; do not regress secret handling into plain prefs JSON. |
| Activity Compose | `1.8.2` | `rememberLauncherForActivityResult` wrappers | Keep for `OpenDocument` / `CreateDocument` flows. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Existing Compose screens + custom IA | PreferenceFragmentCompat | Faster for plain forms, but inconsistent with current Compose app and weaker for custom preview/import UX. |
| Gson backup payloads | kotlinx.serialization / Proto | Cleaner schemas long-term, but unnecessary dependency/churn for this phase. |
| SAF document picker | raw file paths / MANAGE_EXTERNAL_STORAGE | Wrong UX/security model on Android; do not do this. |

**Installation:**
```bash
# No new dependency is required for the baseline plan.
```

## Architecture Patterns

### Recommended Project Structure
```text
android/app/src/main/java/com/github/shekohex/whisperpp/
├── ui/settings/              # Settings screens, grouped-home UI, import/export flow UI
├── data/                     # SettingsRepository + backup snapshot/apply logic
├── privacy/                  # SecretsStore; secret restore helpers
└── ...
```

### Pattern 1: Grouped Settings Home + Nested Detail Routes
**What:** Keep one polished home screen with section cards/status lines, then navigate into focused detail screens.
**When to use:** Always for Phase 6 top-level settings IA.
**Use here:** `MainSettingsScreen` becomes a true overview surface: setup banner, setup-critical sections first, concise status line per card, contextual help action.

### Pattern 2: Typed Backup Snapshot + Envelope
**What:** Separate the backup file model from raw DataStore keys.
**When to use:** Export/import, preview, merge, skipped-item reporting, cross-version handling.
**Use here:** Introduce a versioned backup envelope like:
- metadata: schema version, app version, export timestamp
- crypto header: algorithm, salt, IV, KDF params
- payload categories: providers, providerSecrets, selections, languageDefaults, prompts, promptProfiles, appMappings, presets, keyboard toggles, privacy/send-policy, disclosure/debug flags, update settings

This avoids the current fragile `EXPORTABLE_*_KEYS` lists in `SettingsRepository.kt:68`.

### Pattern 3: Analyze Before Apply
**What:** Import is a pipeline: decrypt -> decode -> validate -> diff -> preview -> apply -> repair report.
**When to use:** Every import.
**Use here:** Build an `ImportAnalysis` object before any `dataStore.edit` call. It should include:
- detected categories
- overwrite vs merge result preview
- conflicts resolved by imported-value-wins
- invalid/unsupported/skipped items
- post-apply warnings and repair checklist entries

### Pattern 4: Category-Scoped Apply Functions
**What:** One apply function per settings category.
**When to use:** Merge/overwrite implementation.
**Use here:** Functions like `applyProviders`, `applyPromptProfiles`, `applyAppMappings`, `applySelections`, `applySendPolicies`, `applyToggles`. This keeps merge semantics explicit and makes partial-import reporting possible.

### Anti-Patterns to Avoid
- **Monolithic import function:** current `importSettings()` overwrites immediately and cannot preview, merge, or report partial failures.
- **Raw preference-key backup as product contract:** current export list is incomplete and tied to legacy keys.
- **Silent cleanup after import:** clearing invalid selections without a user-facing repair summary violates locked decisions.
- **Secrets inside normal provider JSON:** keep provider API keys out of `providers_json`; include them only inside encrypted backup payload and restore via `SecretsStore`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| File picking/sharing | custom storage permissions/path picker | SAF `OpenDocument` / `CreateDocument` | Official Android flow; user-controlled access; no broad storage permission. |
| Settings persistence | ad-hoc JSON file as runtime source of truth | DataStore + repository helpers | Transactional, reactive, already established in repo. |
| Secret-at-rest storage | plaintext API keys in prefs/export DTOs | `SecretsStore` + encrypted backup payload | Existing repo already avoids serializing local secrets into provider JSON. |
| Cryptography | custom obfuscation or custom cipher format | Standard JCA primitives (`Cipher`, AEAD, KDF) | Avoids insecure homegrown crypto. |
| Merge/report UI | giant raw JSON diff viewer | category summaries + targeted detail rows | Matches product requirement and keeps UX readable. |

**Key insight:** The custom work in this phase should be domain merge/report logic, not storage access, persistence, or cryptography primitives.

## Common Pitfalls

### Pitfall 1: Assuming current export is nearly done
**What goes wrong:** Planning treats import/export as light UI work.
**Why it happens:** There is already an export button and `SettingsRepository.exportSettings()`.
**How to avoid:** Treat backup as a new subsystem. Current code only exports a subset and uses plain JSON (`SettingsScreen.kt:285`, `SettingsRepository.kt:415`).
**Warning signs:** Prompt profiles, app mappings, send policy, active v2 selections, global base prompt, or provider secrets are not represented in the backup DTO.

### Pitfall 2: Losing provider credentials on backup/restore
**What goes wrong:** Imported providers restore without usable API keys.
**Why it happens:** `ServiceProvider.apiKey` is `@Transient` in `ProviderModels.kt:47`; current provider JSON intentionally excludes keys.
**How to avoid:** During export, read decrypted keys from `SecretsStore`; place them only inside the encrypted backup payload; on import, restore them back into `SecretsStore`.
**Warning signs:** Restored providers exist but validation/repair summary reports missing credentials.

### Pitfall 3: Merge semantics become implicit and inconsistent
**What goes wrong:** Some categories overwrite, others append, others partially merge with no user-visible rule.
**Why it happens:** Categories are heterogeneous: lists by id, maps by package name, scalar toggles, selections, and secrets.
**How to avoid:** Define merge semantics per category in planning before coding. Imported values win on conflict, but category inclusion/exclusion is user-controlled.
**Warning signs:** Merge code directly mutates raw prefs without an intermediate analysis model.

### Pitfall 4: Invalid imported selections are cleared silently
**What goes wrong:** Import succeeds, but dictation/enhancement/command stop working with no explanation.
**Why it happens:** Providers/models can be missing, incompatible, or secret-dependent on the current device.
**How to avoid:** Reuse `validateSelections()` after apply, collect cleared keys, and surface a repair checklist on completion and settings home.
**Warning signs:** `keysToClear` is non-empty but user sees only a generic success toast.

### Pitfall 5: Preview screen becomes unreadable
**What goes wrong:** A massive key-by-key diff overwhelms the user.
**Why it happens:** Backup contains many heterogeneous values.
**How to avoid:** Preview by category first: counts, affected selections, conflicts, skipped items, and only a few targeted details.
**Warning signs:** Preview is basically raw JSON or a long ungrouped list.

### Pitfall 6: KDF choice breaks minSdk 24
**What goes wrong:** Password-based decryption works on newer devices but fails on API 24-25.
**Why it happens:** `PBKDF2WithHmacSHA256` support on older Android releases is not well verified here.
**How to avoid:** Plan an API24-compatible derivation strategy up front; validate on minSdk devices/emulator before locking the file format.
**Warning signs:** Backup implementation assumes one KDF without compatibility testing.

## Code Examples

Verified patterns from official/current sources:

### SAF Export / Import Entry Points
```kotlin
val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
) { uri ->
    if (uri != null) {
        scope.launch {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(encryptedBackupBytes)
            }
        }
    }
}

val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
) { uri ->
    if (uri != null) {
        pendingImportUri = uri
        showPasswordSheet = true
    }
}
```
Source: Android Storage Access Framework docs (`ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT`).

### AES-GCM Envelope Usage
```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, secretKey)
val iv = cipher.iv
val ciphertext = cipher.doFinal(plaintext)

val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
val restored = decryptCipher.doFinal(ciphertext)
```
Source: Oracle Java 17 docs for `Cipher` and `GCMParameterSpec`.

### DataStore Transactional Apply
```kotlin
dataStore.edit { prefs ->
    prefs[ACTIVE_STT_PROVIDER_ID] = providerId
    prefs[ACTIVE_STT_MODEL_ID] = modelId
}
```
Source: official Android DataStore guidance; already matches repo usage in `SettingsScreen.kt` and `SettingsRepository.kt`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Flat-ish settings list with direct actions | Grouped section-card home with nested destinations and concise status lines | Phase 6 target | Better discoverability, setup guidance, and lower top-level complexity. |
| Plain JSON export/import | Password-encrypted versioned backup envelope | Phase 6 target | Meets sensitive backup requirement and enables forward-compatible restore reporting. |
| Immediate overwrite import | Bottom-sheet mode choice -> preview -> confirm -> apply | Phase 6 target | Makes merge/overwrite explicit and safer. |
| Raw key-list export contract | Typed category snapshot contract | Phase 6 target | Enables full coverage, merge semantics, cross-version warnings, and partial restore. |

**Deprecated/outdated:**
- Current `SettingsRepository.exportSettings()/importSettings()` should be treated as legacy implementation to replace, not extend in place.

## Open Questions

1. **Password KDF for API 24-25 compatibility**
   - What we know: AES-GCM usage is straightforward and already mirrored in local secret storage; PBKDF2 algorithm support on minSdk devices is the main compatibility risk.
   - What's unclear: Whether `PBKDF2WithHmacSHA256` is safe to lock in for API 24-25 without fallback.
   - Recommendation: Validate on API 24 emulator/device before file-format lock; if uncertain, choose an API-gated/fallback KDF strategy and encode KDF metadata in the backup header.

2. **Exact category list for “full settings”**
   - What we know: Requirements/context imply providers/models, selections, prompts, profiles, per-app/per-language overrides, dictation/enhancement/command toggles, send policy, and sensitive/provider data.
   - What's unclear: Whether first-use disclosure/reset flags and updater channel belong in user-visible backup categories or only in a low-level “advanced” bucket.
   - Recommendation: Decide once in planning and show the exact included categories in export confirmation copy.

## Sources

### Primary (HIGH confidence)
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - current settings IA, help TODO, plain import/export flow
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` - current export/import scope, migration/validation helpers
- `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecretsStore.kt` - existing AES-GCM + Android Keystore secret handling pattern
- `android/app/build.gradle.kts` - actual stack/versions in use
- https://developer.android.com/training/data-storage/shared/documents-files - SAF create/open document guidance
- https://developer.android.com/topic/libraries/architecture/datastore - DataStore guidance
- https://developer.android.com/develop/ui/compose/designsystems/material3 - Material 3 in Compose guidance
- https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html - AEAD/AES-GCM behavior and IV uniqueness
- https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html - GCM tag/IV parameter contract

### Secondary (MEDIUM confidence)
- Google-grounded search results pointing to official Android docs for Material 3, DataStore, and SAF usage patterns
- Google-grounded search results pointing to Oracle standard algorithm names for `PBKDF2WithHmacSHA256`

### Tertiary (LOW confidence)
- Community search results suggesting `PBKDF2WithHmacSHA256` support may vary on older Android releases; requires direct runtime validation on minSdk 24 devices

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - repo stack is explicit and official Android APIs fit the phase
- Architecture: MEDIUM - import/report/category design is clear, but KDF compatibility and exact category boundaries still need planning decisions
- Pitfalls: HIGH - most pitfalls are directly visible in current code and locked user decisions

**Research date:** 2026-03-09
**Valid until:** 2026-04-08
