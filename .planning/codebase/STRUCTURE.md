# Codebase Structure

**Analysis Date:** 2026-02-26

## Directory Layout

```
[project-root]/
├── android/                 # Android app project (Gradle)
│   ├── app/                 # :app module
│   │   └── src/
│   │       ├── main/        # Production app code + resources
│   │       ├── test/        # JVM unit tests
│   │       └── androidTest/ # Instrumented tests
│   ├── gradle/              # Gradle wrapper support files
│   ├── build.gradle.kts     # Top-level Android build config
│   └── settings.gradle.kts  # Module includes + repositories
├── .github/workflows/       # CI/CD pipeline definitions
├── fastlane/                # Release automation (nightly/tagged)
└── .planning/codebase/      # Generated mapper docs (this output)
```

## Directory Purposes

**`android/app/src/main/java/com/github/shekohex/whisperpp/`:**
- Purpose: App/IME entry points and feature logic.
- Contains: Android components (`Activity`, `InputMethodService`, Workers/Receivers) and service classes.
- Key files:
  - `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`

**`android/app/src/main/java/com/github/shekohex/whisperpp/ui/`:**
- Purpose: Jetpack Compose UI.
- Contains: Feature screens + shared components + theme.
- Key files:
  - Settings screens + nav graph: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
  - Keyboard UI: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt`
  - Shared components: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/components/*`
  - Theme: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/theme/*`

**`android/app/src/main/java/com/github/shekohex/whisperpp/data/`:**
- Purpose: Persistence helpers and configuration models.
- Contains: `SettingsRepository`, provider/profile models, presets.
- Key files:
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt`

**`android/app/src/main/java/com/github/shekohex/whisperpp/recorder/`:**
- Purpose: Audio recording and WAV formatting.
- Contains: `AudioRecord` integration and WAV header utilities.
- Key files:
  - `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RiffWaveHelper.kt`

**`android/app/src/main/java/com/github/shekohex/whisperpp/updater/`:**
- Purpose: Self-update feature (check/download/verify/install).
- Contains: Worker, receiver, update orchestration + UI card.
- Key files:
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateManager.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateDownloader.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateInstaller.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateRepository.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateModels.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCard.kt`

**`android/app/src/main/res/`:**
- Purpose: Android resources.
- Contains:
  - Strings/constants: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values/constants.xml`
  - IME metadata: `android/app/src/main/res/xml/method.xml`
  - FileProvider paths: `android/app/src/main/res/xml/file_provider_paths.xml`
  - Smart Fix default prompt: `android/app/src/main/res/raw/smart_fix_prompt.txt`

**`.github/workflows/`:**
- Purpose: CI/CD automation.
- Key files: `/.github/workflows/android-ci.yml`

**`fastlane/`:**
- Purpose: Build + GitHub release automation.
- Key files: `fastlane/Fastfile`, `fastlane/Appfile`

## Key File Locations

**Entry Points:**
- `android/app/src/main/AndroidManifest.xml`: Declares `MainActivity`, IME service `.WhisperInputService`, FileProvider, update receiver.
- `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`: Launcher activity + `Context.dataStore` definition + preference keys.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`: IME service; hosts Compose keyboard UI and coordinates record/transcribe.
- `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`: Periodic update checks + notification.
- `android/app/src/main/java/com/github/shekohex/whisperpp/updater/DownloadCompletedReceiver.kt`: Download complete broadcast handling.

**Configuration:**
- `android/app/build.gradle.kts`: Android app config + dependencies + `BuildConfig` fields for updater.
- `android/settings.gradle.kts`: module includes and repositories.
- `.github/workflows/android-ci.yml`: CI build/release via Fastlane.
- `fastlane/Fastfile`: release lanes that generate `latest.json` consumed by `UpdateChecker`.

**Core Logic:**
- Transcription: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
- Smart Fix: `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- Settings persistence: `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Recording: `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt`
- Updates: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/*`

**Testing:**
- Unit tests: `android/app/src/test/java/com/github/shekohex/whisperpp/ExampleUnitTest.kt`
- Instrumented tests: `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ExampleInstrumentedTest.kt`

## Naming Conventions

**Files:**
- Kotlin source: `PascalCase.kt` (e.g., `MainActivity.kt`, `WhisperInputService.kt`, `SettingsRepository.kt`).
- Compose screens/components: `*Screen.kt` and component files under `ui/components/` (e.g., `ui/keyboard/KeyboardScreen.kt`, `ui/components/SplashScreen.kt`).
- Updater feature files grouped under `updater/` with `Update*` prefixes (e.g., `updater/UpdateManager.kt`).

**Directories:**
- Feature grouping by package under `android/app/src/main/java/com/github/shekohex/whisperpp/`: `ui/`, `data/`, `recorder/`, `updater/`, `keyboard/`.

## Where to Add New Code

**New Settings screen / app UI feature:**
- Primary code: add composables (and optional route) in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` or split into `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/`.
- Shared UI components: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/components/`.

**New IME keyboard UI behavior:**
- UI changes: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt`.
- Service wiring/state: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`.
- Shared state enum additions: `android/app/src/main/java/com/github/shekohex/whisperpp/keyboard/KeyboardState.kt`.

**New backend/provider type or configuration field:**
- Data model updates: `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`.
- Default presets: `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt`.
- Persistence/migration: `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`.
- Runtime usage:
  - transcription: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
  - smart fix: `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`

**New updater capability (e.g., alternate host/source):**
- Network + parsing: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`, `UpdateModels.kt`.
- Download/verification: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateDownloader.kt`.
- Install + permissions: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateInstaller.kt`.
- UI: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCard.kt` and `UpdateSettingsSection` inside `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`.

**Utilities:**
- Recorder/audio utilities: `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/`.
- Cross-feature utilities typically live at `android/app/src/main/java/com/github/shekohex/whisperpp/` (top-level package) alongside `WhisperTranscriber.kt` / `SmartFixer.kt`.

## Special Directories

**`android/.gradle/`, `android/app/build/`, `android/build/`:**
- Purpose: Gradle caches and build outputs.
- Generated: Yes.
- Committed: No.

**`.planning/codebase/`:**
- Purpose: Generated mapper docs used by GSD planning/execution.
- Generated: Yes.
- Committed: Depends on repo policy.

---

*Structure analysis: 2026-02-26*
