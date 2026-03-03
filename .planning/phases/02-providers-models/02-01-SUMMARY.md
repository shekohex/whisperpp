---
phase: 02-providers-models
plan: 01
subsystem: data
tags: [android, kotlin, datastore, gson, okhttp]

requires:
  - phase: 01-privacy-safety-controls
    provides: SecretsStore-backed provider API keys + disclosure formatter foundation
provides:
  - Provider schema v2 (base URL + auth mode; model kind + streaming partials capability)
  - One-time migration of saved providers JSON to v2 semantics
  - Privacy disclosure paths derived from base URL + operation
affects: [providers, settings, ime, dictation, enhancement, command]

tech-stack:
  added: []
  patterns:
    - One-time DataStore JSON migrations via raw JsonElement sanitize + flag
    - Base-URL provider semantics with derived operation paths (OpenAI/Gemini)

key-files:
  created:
    - android/app/src/test/java/com/github/shekohex/whisperpp/data/ProviderSchemaV2MigrationTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatterTest.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt

key-decisions:
  - "Kept JSON field name `endpoint` for compatibility; semantics are now base URL"
  - "Default auth mode is API_KEY except WHISPER_ASR which defaults to NO_AUTH"

patterns-established:
  - "Schema migrations run before Settings/IME consume providers"
  - "Disclosures derive operation paths from basePath + mode"

requirements-completed: [PROV-01, PROV-02]

duration: 20 min
completed: 2026-03-03
---

# Phase 2 Plan 1: Provider/model schema v2 + migration + disclosure derivation tests Summary

**Provider schema v2 with base URL + auth mode, model kind/capabilities, and a one-time migration that preserves existing installs and disclosure accuracy.**

## Performance

- **Duration:** 20 min
- **Started:** 2026-03-03T03:00:26Z
- **Completed:** 2026-03-03T03:20:48Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- Extended provider/model types with auth mode + model kind/capabilities
- Added one-time schema migration to normalize legacy per-operation endpoints to base URLs and inject new fields
- Updated privacy disclosure formatting to derive operation paths from base URLs + added unit tests

## Task Commits

Each task was committed atomically:

1. **task 1: Extend provider/model types for base URL + auth mode + model kind/capabilities** - `511e35a`
2. **task 2: Add one-time provider schema migration (endpoint → base URL, default new fields)** - `9ec8556`
3. **task 3: Update privacy disclosures to derive operation paths from base URLs + add unit tests** - `bebaaf9`

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt` - Adds ModelKind/ProviderAuthMode and schema v2 fields
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt` - Updates presets to base URL semantics + model kinds
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` - Provider schema v2 one-time migration + sanitize-on-read for new fields
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - Runs schema v2 migration before rendering
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - Runs schema v2 migration before consuming providers
- `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt` - Derives operation paths from base URL + mode
- `android/app/src/test/java/com/github/shekohex/whisperpp/data/ProviderSchemaV2MigrationTest.kt` - Unit coverage for schema v2 migration rewrites/defaults
- `android/app/src/test/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatterTest.kt` - Unit coverage for disclosure path derivation

## Decisions Made
- Kept JSON field name `endpoint` for compatibility; semantics are now base URL.
- Default auth mode is `API_KEY` except `WHISPER_ASR` which defaults to `NO_AUTH`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Gradle verification failed under JDK 21 in this environment (AGP 8.1.2); ran Gradle with a local Temurin JDK 17 via `JAVA_HOME=...` for all verification steps.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Provider/model schema foundation + migration is in place; ready for 02-02 (settings UX rules + model metadata editor).

## Self-Check: PASSED
