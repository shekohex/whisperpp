---
status: resolved
trigger: "Investigate issue: add-provider-save-missing"
created: 2026-03-26T00:00:00Z
updated: 2026-03-26T00:40:00Z
---

## Current Focus

hypothesis: resolved
test: human verified real add provider -> import models -> save workflow
expecting: provider persists in list and after reopening screen
next_action: archive session

## Symptoms

expected: After pressing Save, the new provider persists, appears in the providers list, and remains after leaving/reopening the screen.
actual: Imported models are visible during add flow, but after Save no provider appears in the list.
errors: No visible error, toast, snackbar, or log hint was reported.
reproduction: Open Providers, add a provider, import models, press Save, provider is missing from the list.
started: Not sure; only noticed now.

## Eliminated

## Evidence

- timestamp: 2026-03-26T00:05:00Z
  checked: ProviderEditScreen save and import logic in SettingsScreen.kt
  found: Save always calls repository.upsertProvider(newProvider); Import models also calls repository.upsertProvider but only the import/test section is shown for isEditingExisting == true
  implication: the reported flow likely uses a provider that already exists by the time models are imported, or the bug is elsewhere in the save path

- timestamp: 2026-03-26T00:07:00Z
  checked: SettingsRepository upsert/save implementation
  found: upsertProvider trims id, loads current providers via providers.first(), replaces/adds by id, then serializes full list back to PROVIDERS_JSON
  implication: persistence logic is simple and should be easy to falsify with a focused repository test

- timestamp: 2026-03-26T00:18:00Z
  checked: ServiceProvider model plus SettingsRepository.providers sanitization path
  found: ServiceProvider.apiKey is marked @Transient, but SettingsRepository.providers sanitizes entries with provider.copy(...) without explicitly overriding apiKey
  implication: after JSON round-trip, a null transient field can poison provider.copy and make the providers flow fall back to emptyList()

- timestamp: 2026-03-26T00:22:00Z
  checked: SettingsRepositoryProviderPersistenceTest via Gradle on android/
  found: both repository persistence tests failed immediately on read-back assertions after upsertProvider
  implication: persistence bug is confirmed below the UI layer; saved providers are being lost during repository read/deserialize

- timestamp: 2026-03-26T00:26:00Z
  checked: Gradle XML test report for SettingsRepositoryProviderPersistenceTest
  found: both tests failed with expected <1> but was <0>, proving repository.providers returned an empty list after save
  implication: the providers flow is swallowing a deserialization/sanitization failure and masking it as no providers saved

- timestamp: 2026-03-26T00:32:00Z
  checked: targeted Gradle verification
  found: SettingsRepositoryProviderPersistenceTest passed after fix; ProviderSchemaV2MigrationTest and ProviderSelectionsMigrationTest also passed
  implication: repository read/write regression is fixed and adjacent provider data behavior still passes

## Resolution

root_cause: SettingsRepository.providers sanitized deserialized ServiceProvider instances with provider.copy(...) but did not override transient apiKey. After Gson deserialization, apiKey is null, provider.copy throws, and the flow catches the exception and returns emptyList(), so saved providers appear to vanish.
fix: Set apiKey = "" explicitly when sanitizing providers in SettingsRepository.providers so deserialized providers remain copy-safe, then add repository regression tests covering new-provider save and import-then-save persistence.
verification: Ran ./gradlew testDebugUnitTest --tests com.github.shekohex.whisperpp.data.SettingsRepositoryProviderPersistenceTest and ./gradlew testDebugUnitTest --tests com.github.shekohex.whisperpp.data.SettingsRepositoryProviderPersistenceTest --tests com.github.shekohex.whisperpp.data.ProviderSchemaV2MigrationTest --tests com.github.shekohex.whisperpp.data.ProviderSelectionsMigrationTest; all passed. Human verification confirmed the real add/import/save workflow is fixed.
files_changed: ["android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt", "android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsRepositoryProviderPersistenceTest.kt"]
