# Architecture

**Analysis Date:** 2026-02-26

## Pattern Overview

**Overall:** Single-module Android app with dual entry points (launcher `Activity` + IME `InputMethodService`) using Jetpack Compose for UI, Kotlin coroutines/Flow for async/state, and small service/repository classes for network + persistence.

**Key Characteristics:**
- Two primary “surfaces”: settings app UI (`Activity`/Compose) and keyboard UI hosted inside an IME service (`InputMethodService` + `ComposeView`).
- Preferences/persistence centered on `DataStore<Preferences>` exposed via a top-level extension (`val Context.dataStore`) in `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`.
- Network operations split by feature: transcription via OkHttp (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`), smart-fix LLM calls via OkHttp (`android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`), update checks via Ktor (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`).

## Layers

**Platform entry points (Android components):**
- Purpose: App lifecycle, IME lifecycle, background work, broadcasts.
- Location:
  - `android/app/src/main/AndroidManifest.xml`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/DownloadCompletedReceiver.kt`
- Contains: `Activity`, `InputMethodService`, `CoroutineWorker`, `BroadcastReceiver`, `FileProvider` config.
- Depends on: Compose UI, repositories, recorder/network utilities.
- Used by: Android OS (launcher/IME binding, WorkManager, DownloadManager broadcasts).

**UI layer (Jetpack Compose):**
- Purpose: Render settings UI and keyboard UI; emit callbacks for actions.
- Location:
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` (includes `SettingsNavigation` and multiple screens)
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/components/*`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/theme/*`
- Contains: Composables, Compose Navigation graph (`NavHost`) for settings, gesture-driven IME UI.
- Depends on: `DataStore`, `SettingsRepository`, updater classes (`UpdateManager`), shared preference keys from `MainActivity.kt`.
- Used by:
  - `MainActivity.setContent { ... }` in `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`
  - `WhisperInputService.onCreateInputView()` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`

**Persistence/config layer (DataStore + models):**
- Purpose: Store user configuration (providers, profiles, toggles) and updater state.
- Location:
  - `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` (`val Context.dataStore` + preference keys)
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` (provider/profile JSON + import/export)
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt` (provider/model/profile types)
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt` (default providers)
  - `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateRepository.kt` (persisted update download state)
- Contains: `Flow`-based accessors and `DataStore.edit` writes.
- Depends on: Gson (`SettingsRepository`), AndroidX DataStore.
- Used by: Settings UI, IME service, updater flows.

**Domain/services (feature logic):**
- Purpose: Recording, transcription, smart-fix post-processing, self-update operations.
- Location:
  - Recording: `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RiffWaveHelper.kt`
  - Transcription: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
  - Smart Fix: `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
  - Updates: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateManager.kt`, `UpdateChecker.kt`, `UpdateDownloader.kt`, `UpdateInstaller.kt`, `UpdateModels.kt`
- Contains: OkHttp/Ktor clients, DownloadManager integration, hashing/verification, coroutine jobs.
- Used by: `WhisperInputService`, settings UI update section.

## Data Flow

**Speech-to-text via IME (record → transcribe → optional smart-fix → commit):**

1. IME UI is created in `WhisperInputService.onCreateInputView()` (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`) using `ComposeView` and `KeyboardScreen` (`android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt`).
2. User gestures trigger callbacks wired in `KeyboardScreen(...)` → `onMicAction()` / `onSendAction()` / `onCancelAction()` in `WhisperInputService`.
3. Recording is performed by `RecorderManager.start(recordedAudioFilename)` writing PCM + WAV header (`android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt`). Output path is built from `externalCacheDir` in `WhisperInputService.updateAudioFormat()`.
4. When transcription starts (`WhisperInputService.startTranscription(...)`), provider selection and settings are loaded from DataStore (`dataStore.data.first()` and `SettingsRepository.providers.first()` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`).
5. Network transcription runs through `WhisperTranscriber.startAsync(...)` (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`), performing a multipart request (OkHttp) based on `ServiceProvider.type` from `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`.
6. Transcription callback `transcriptionCallback(...)` commits text into the current app via `currentInputConnection?.commitText(...)` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`.
7. If Smart Fix is enabled, `SmartFixer.fix(...)` is executed on `Dispatchers.IO` before committing (`android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`). Default prompt loads from `android/app/src/main/res/raw/smart_fix_prompt.txt`.
8. Optional “switch back” behavior calls `onSwitchIme()` (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`).

**Settings/config flow (Compose settings app → DataStore → consumed by IME):**

1. Launcher entry point `MainActivity` hosts the settings UI via `SettingsNavigation(dataStore, ...)` in `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` and `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`.
2. Settings screens read preferences using `dataStore.data.collectAsState(...)` and provider/profile lists via `SettingsRepository` flows (`android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`).
3. Provider definitions are stored as JSON in preferences (`PROVIDERS_JSON`) and parsed into `List<ServiceProvider>` (`android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`).
4. IME service consumes the same DataStore (`repository = SettingsRepository(dataStore)` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`) to pick providers/models at runtime.

**Self-update flow (Worker/Settings UI → GitHub latest.json → DownloadManager → install):**

1. Background schedule is started in `MainActivity.onCreate()` via `UpdateCheckWorker.schedule(this)` (`android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`).
2. `UpdateCheckWorker.doWork()` invokes `UpdateChecker.checkForUpdate(...)` (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`), which fetches `latest.json` from GitHub releases derived from `BuildConfig.GITHUB_REPO` (set in `android/app/build.gradle.kts`).
3. If available, a notification is shown with a `PendingIntent` opening `MainActivity` with `EXTRA_SHOW_UPDATE` (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`).
4. Settings UI `UpdateSettingsSection(...)` uses `UpdateManager` state flows (`android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateManager.kt`).
5. Download uses `DownloadManager` via `UpdateDownloader.download(...)` and persists `downloadId/signature/path` via `UpdateRepository` (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateDownloader.kt`, `UpdateRepository.kt`).
6. Verification computes SHA-256 and compares to `LatestRelease.platforms[*].signature` (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateDownloader.kt`, `UpdateModels.kt`).
7. Installation uses `FileProvider` with paths in `android/app/src/main/res/xml/file_provider_paths.xml` and `UpdateInstaller.install(...)` (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateInstaller.kt`).
8. `DownloadCompletedReceiver` listens for `DownloadManager.ACTION_DOWNLOAD_COMPLETE` to verify downloads even when the app UI is not active (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/DownloadCompletedReceiver.kt`).

**State Management:**
- IME runtime UI state uses Compose state holders in the service (`mutableStateOf(...)` fields in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`) and renders via `KeyboardScreen`.
- Settings UI is primarily driven by `DataStore` Flows collected into Compose (`collectAsState`) in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`.
- Updater state uses `StateFlow` in `UpdateManager` (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateManager.kt`).

## Key Abstractions

**ServiceProvider (transcription + LLM provider config):**
- Purpose: Unified configuration for different backend types (OpenAI, Whisper ASR, Gemini, Custom).
- Examples: `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`, presets in `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt`.
- Pattern: Data class + enums; stored as JSON in DataStore via `SettingsRepository`.

**RecorderManager (local audio capture to WAV):**
- Purpose: Owns `AudioRecord` lifecycle, file writing, amplitude reporting.
- Examples: `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt`.
- Pattern: Imperative manager with internal thread + coroutine job for periodic UI updates.

**UpdateManager (UI-facing update orchestration):**
- Purpose: Orchestrates check/download/verify/install with `StateFlow` outputs.
- Examples: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateManager.kt`.
- Pattern: Coordinator class used directly from Compose (no ViewModel layer).

## Entry Points

**Launcher Activity:**
- Location: `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`
- Triggers: App launch from launcher (`android/app/src/main/AndroidManifest.xml`).
- Responsibilities: Permissions prompt, schedules periodic update checks, hosts settings UI via Compose.

**IME Service:**
- Location: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Triggers: OS binds IME (`android.permission.BIND_INPUT_METHOD`) with metadata `android/app/src/main/res/xml/method.xml`.
- Responsibilities: Render keyboard UI, manage recording/transcription lifecycle, commit text to current input connection.

**Periodic update worker:**
- Location: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`
- Triggers: WorkManager periodic work scheduled from `MainActivity`.
- Responsibilities: Check GitHub release info and show notification.

**Broadcast receiver (download completion/boot):**
- Location: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/DownloadCompletedReceiver.kt`
- Triggers: `android.intent.action.BOOT_COMPLETED`, `android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE` (`android/app/src/main/AndroidManifest.xml`).
- Responsibilities: Verify completed update downloads against expected signature.

## Error Handling

**Strategy:** Exceptions are generally caught at feature boundaries and converted to UI feedback (Toast/state reset) while logging details.

**Patterns:**
- Transcription/network errors: `try/catch` in `WhisperTranscriber.startAsync(...)` returning `(text, exceptionMessage)` and invoking `exceptionCallback(...)` (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`).
- Smart Fix errors: `try/catch` around Smart Fix path with fallback to raw transcription (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`).
- Update worker errors: `Result.retry()` on failure (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`).

## Cross-Cutting Concerns

**Logging:** Android `Log` with per-component tags (e.g., `TAG = "WhisperInputService"` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`) and HTTP logging interceptors in `WhisperTranscriber`/`SmartFixer`.

**Validation:** Endpoint presence checks exist in `WhisperTranscriber.startAsync(...)` (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`) and Smart Fix configuration checks in `SmartFixer.fix(...)` (`android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`).

**Authentication:** API keys are stored in DataStore preferences and applied as request headers (`Authorization` for OpenAI-like endpoints in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`; `x-goog-api-key` for Gemini in `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`).

---

*Architecture analysis: 2026-02-26*
