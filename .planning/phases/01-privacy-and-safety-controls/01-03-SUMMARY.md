---
phase: 01-privacy-and-safety-controls
plan: 03
subsystem: security
tags: [android, keystore, datastore, gson, compose, ime]

# Dependency graph
requires:
  - phase: 01-02
    provides: Secure-field gating and centralized IME external-send flow
provides:
  - Keystore-backed provider API key storage with AES/GCM encrypted blobs
  - Provider persistence/export/import paths that exclude API keys
  - One-time plaintext providers_json migration into SecretsStore before provider deserialization
  - Settings UI + IME runtime key injection that never reveals full keys
affects: [provider-management, dictation, smart-fix, settings-export-import, privacy-controls]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - ServiceProvider carries apiKey as transient runtime-only field
    - Secrets are fetched just-in-time from SecretsStore and injected via provider.copy(apiKey=...)
    - Migration runs before provider reads in both Settings entry and IME service startup

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecretsStore.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt

key-decisions:
  - "Provider API keys are never serialized; runtime code injects keys from SecretsStore only at call time"
  - "Plaintext key migration parses raw providers_json tree and removes apiKey properties before marking migration complete"
  - "Provider edit UI exposes only key-set state + last4 and supports replace/clear without prefill"

patterns-established:
  - "SettingsNavigation blocks provider screens until migration finishes"
  - "WhisperInputService.onCreate runs migration before any provider flow is consumed"

# Metrics
duration: 8 min
completed: 2026-02-26
---

# Phase 01 Plan 03: Secure provider API key handling (Keystore, sanitized persistence/export, masked UI) Summary

**Provider API keys now live in Android Keystore-backed encrypted storage, are excluded from providers JSON/export/import, remain usable across restarts via runtime injection, and are only shown as key-set + last4 in settings.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-26T15:35:58Z
- **Completed:** 2026-02-26T15:44:14Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Added `SecretsStore` using Android Keystore AES/GCM with random IV per write and encrypted SharedPreferences payloads keyed by provider id.
- Made provider persistence secret-safe: `ServiceProvider.apiKey` is transient, export/import no longer include secret preference keys, and migration moves any legacy plaintext `apiKey` out of `providers_json`.
- Updated settings/runtime flows: provider edit never pre-fills keys, shows key-set + last4, supports replace/clear, and IME injects keys from `SecretsStore` before transcription/smart-fix network calls.

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement Keystore-backed SecretsStore for provider API keys** - `b39b3b7` (feat)
2. **Task 2: Remove apiKey from persisted providers/export + migrate existing plaintext keys** - `26bb34c` (feat)

**Plan metadata:** to be captured in docs commit for this plan.

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecretsStore.kt` - encrypted secret store API for provider keys.
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt` - marks `ServiceProvider.apiKey` as transient runtime-only.
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` - adds one-time raw JSON migration + removes legacy API key export/import preferences.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` - runs migration at settings entry; provider edit now masked with set/last4 + clear/replace actions.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - runs migration at startup and injects provider keys from `SecretsStore` before network usage.

## Decisions Made
- API keys are never persisted in DataStore provider JSON; only encrypted blobs in `SecretsStore` hold provider secrets.
- Migration is guarded by `PROVIDER_API_KEY_MIGRATION_DONE` and only marked complete after sanitized JSON write succeeds.
- Runtime networking paths keep existing provider contracts by copying providers with in-memory `apiKey` values loaded from `SecretsStore`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Ran verification from actual Gradle root (`android/`)**
- **Found during:** Task 1 verification
- **Issue:** Verification command executed from repository root failed because Gradle project root is `android/`.
- **Fix:** Re-ran equivalent debug build from `android/` (`./gradlew :app:assembleDebug` / `./gradlew assembleDebug`).
- **Files modified:** None
- **Verification:** Build succeeded from `android/`.
- **Committed in:** N/A (execution-only adjustment)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** No scope change; adjustment ensured verification executed from correct project root.

## Authentication Gates

None.

## Issues Encountered
- None beyond the auto-fixed verification root issue.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Ready for `01-04-PLAN.md`.
- No blockers carried forward.

---
*Phase: 01-privacy-and-safety-controls*
*Completed: 2026-02-26*
