# Coding Conventions

**Analysis Date:** 2026-02-26

## Naming Patterns

**Files:**
- Kotlin/Android source uses `PascalCase.kt` filenames.
  - Examples: `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`

**Functions:**
- `camelCase` for functions/methods.
  - Examples: `startAsync(...)` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`, `checkForUpdate(...)` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`

**Variables:**
- `camelCase` for locals/fields; `val` preferred for immutable values.
  - Examples: `downloadJob`, `currentPlatformInfo` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateManager.kt`

**Types:**
- `PascalCase` for classes, `enum class`, `sealed class`, and `data class`.
  - Examples: `ServiceProvider` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`, `UpdateCheckResult` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateModels.kt`

**Constants / keys:**
- Compile-time constants use `private const val` with `UPPER_SNAKE_CASE`.
  - Examples: `TAG`, `RECORDED_AUDIO_FILENAME_WAV` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Preference/DataStore keys are often declared as top-level `val` with `UPPER_SNAKE_CASE` (even when not `const`).
  - Examples: `SPEECH_TO_TEXT_BACKEND`, `LANGUAGE_CODE` in `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`; `PROVIDERS_JSON` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`

## Code Style

**Formatting:**
- Kotlin official style is enabled via Gradle properties.
  - Config: `android/gradle.properties` (`kotlin.code.style=official`)
- Indentation is 4 spaces (consistent across Kotlin sources).
  - Examples: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt`

**Linting:**
- No dedicated Kotlin linter/formatter configuration is detected (no Detekt/Ktlint/Spotless config present).
- Android lint is the primary static analysis tool (driven by the Android Gradle Plugin).
  - Module config entry point: `android/app/build.gradle.kts`

## Import Organization

**Order:**
1. `android.*`
2. `androidx.*`
3. Third-party (`io.ktor.*`, `okhttp3.*`, `com.google.*`, etc.)
4. Project imports (`com.github.shekohex.whisperpp.*`)
5. Kotlin/JDK (`kotlin.*`, `java.*`) when present

**Wildcard imports:**
- Wildcard imports are used in UI and DataStore-related code.
  - Examples: `import androidx.compose.runtime.*` and other wildcards in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
  - Examples: `import androidx.datastore.preferences.core.*` in `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`

## Error Handling

**Patterns:**
- Prefer explicit result types for domain workflows using `sealed class`.
  - Examples:
    - `sealed class UpdateCheckResult` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateModels.kt`
    - `sealed class ImportResult` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Use `try/catch` to convert exceptions into user-facing messages / result types.
  - Example: `UpdateChecker.checkForUpdate(...)` wraps the whole flow and returns `UpdateCheckResult.Error("Update check failed: ...", e)` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`
- Background work uses WorkManager `Result.success()` / `Result.retry()`.
  - Example: `UpdateCheckWorker.doWork()` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`

## Logging

**Framework:** Android `Log`

**Patterns:**
- Use a per-class tag, often as `private const val TAG = "..."`.
  - Example: `TAG` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- HTTP logging uses OkHttp `HttpLoggingInterceptor` with explicit header redaction.
  - Examples:
    - Redacts `Authorization` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
    - Redacts `Authorization` and `x-goog-api-key` in `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`

## Comments

**When to Comment:**
- License headers are present on several core files.
  - Examples: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt`, `android/app/src/main/AndroidManifest.xml`
- Inline comments are used for sectioning, guard-rail explanations, and TODO placeholders.
  - Example: `/* TODO */` in the app bar actions in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`

**JSDoc/TSDoc:**
- Not applicable (Kotlin codebase). KDoc is minimal; most documentation appears as inline comments or Android template comments in test stubs.

## Function Design

**Size:**
- Small helpers exist (`computeSha256(...)`, `getFailureReason(...)` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateDownloader.kt`), but large “feature files” also exist.
  - Large UI surface: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
  - Large service/controller: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`

**Parameters:**
- Business operations often take explicit parameter lists instead of parameter objects.
  - Example: `WhisperTranscriber.startAsync(...)` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`

**Return Values:**
- Favor explicit return types for domain outcomes (sealed results) and early returns on invalid state.
  - Example: early return for missing provider selection in `startTranscription(...)` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`

## Module Design

**Exports:**
- Data/state models use `data class` and `sealed class`.
  - Examples: `ServiceProvider`, `ModelConfig` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`; `DownloadState` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateModels.kt`
- Cross-module keys/state are commonly declared as top-level values in the package.
  - Examples: preference keys in `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`; DataStore key `PROVIDERS_JSON` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`

**Barrel Files:**
- Not used (Kotlin/Android). Some files use wildcard imports from the project package.
  - Example: `import com.github.shekohex.whisperpp.*` in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`

---

*Convention analysis: 2026-02-26*
