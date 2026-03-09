---
phase: 06-settings-ux-import-export
plan: 01
subsystem: data
tags: [android, kotlin, datastore, backup, aes-gcm, pbkdf2, gson]
requires:
  - phase: 01-privacy-safety-controls
    provides: secure provider credential storage without plaintext export
  - phase: 02-providers-models
    provides: provider/model and language profile persistence contracts
  - phase: 04-prompts-profiles-enhancement
    provides: prompt profiles, app mappings, and global prompt data
  - phase: 05-command-mode-presets
    provides: transform preset defaults and active text selection state
provides:
  - versioned encrypted backup envelope with stable category manifest metadata
  - password-based AES-GCM backup encryption compatible with API 24 KDF constraints
  - full export snapshot repository covering providers, secrets, prompts, mappings, toggles, privacy, and advanced preferences
affects: [06-02-import-analysis, 06-04-backup-restore-ui, settings-import-export]
tech-stack:
  added: []
  patterns: [typed backup envelope, credential-source separation, category-scoped backup payload]
key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupModels.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupCrypto.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupRepository.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupCryptoTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupRepositoryExportTest.kt
  modified: []
key-decisions:
  - "Use PBKDF2WithHmacSHA1 plus AES/GCM/NoPadding and persist KDF/cipher metadata in the envelope for API 24-compatible password backups."
  - "Keep provider credentials in a dedicated encrypted payload category sourced separately from provider JSON so secrets never leak back into provider serialization."
  - "Freeze the Phase 6 backup category manifest now so later import/export UI can present stable IDs, labels, and sensitive flags verbatim."
patterns-established:
  - "Backup Envelope: schema/app-version/timestamp/manifest/crypto header wrap a single encrypted payload blob."
  - "Backup Export Assembly: build a typed snapshot first, then serialize and encrypt it for file I/O."
requirements-completed: [SET-02]
duration: 8 min
completed: 2026-03-09
---

# Phase 06 Plan 01: Encrypted Backup Export Foundation Summary

**Password-encrypted backup envelopes with category metadata, AES-GCM crypto helpers, and a full export snapshot repository for all Phase 6 settings data.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-09T17:40:38Z
- **Completed:** 2026-03-09T17:48:29Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Added a versioned backup envelope and stable category manifest covering every locked Phase 6 backup bucket.
- Implemented password-derived AES-GCM encryption/decryption with explicit KDF metadata and failure coverage for wrong passwords and tampering.
- Added a dedicated backup export repository that assembles providers, credentials, selections, profiles, mappings, presets, keyboard behavior, privacy state, and advanced preferences into one encrypted snapshot.

## Task Commits

Each task was committed atomically:

1. **Task 1: Define the encrypted backup envelope and prove password-based round trips** - `48909b0` (feat)
2. **Task 2: Build the full export snapshot repository with explicit category coverage** - `66840ac` (feat)

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupModels.kt` - Typed backup envelope, crypto header, manifest, and snapshot DTOs.
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupCrypto.kt` - PBKDF2 + AES-GCM encryption/decryption helpers for backup payloads.
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupRepository.kt` - Full export snapshot builder with separate provider credential sourcing.
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupCryptoTest.kt` - Crypto round-trip, wrong-password, tamper, and metadata tests.
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsBackupRepositoryExportTest.kt` - Export manifest/category coverage and secret-isolation tests.

## Decisions Made
- Used `PBKDF2WithHmacSHA1` for password key derivation to keep the backup format compatible with the project's API 24 floor while still persisting KDF metadata for future migrations.
- Exported provider credentials through a dedicated credential source into `provider_credentials` only, keeping provider JSON secret-free inside the backup payload.
- Locked the category manifest in shared constants so later import preview and export UI can reuse the exact IDs, labels, and sensitivity flags.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- The plan's Gradle verification command is currently blocked by unrelated pre-existing settings UI work already present in the working tree: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHelp.kt` has unresolved `rememberSaveable` references and a missing `settings_help_action` string resource. Per scope boundary this was not modified.
- Verified the new backup subsystem with targeted manual Kotlin compilation plus JUnit execution for `SettingsBackupCryptoTest` and `SettingsBackupRepositoryExportTest` instead, and logged the unrelated blocker in `deferred-items.md`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Ready for 06-02 import analysis/apply semantics on top of the stable encrypted backup contract and category snapshot model.
- Repository-wide Gradle unit-test verification still needs the unrelated settings UI compile breakage resolved before module-wide test commands can pass cleanly.

## Self-Check: PASSED

---
*Phase: 06-settings-ux-import-export*
*Completed: 2026-03-09*
