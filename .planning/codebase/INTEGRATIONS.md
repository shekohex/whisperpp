# External Integrations

**Analysis Date:** 2026-02-26

## APIs & External Services

**Transcription / LLM providers (user-configured):**
- OpenAI Whisper (speech-to-text)
  - Used for multipart audio transcription requests in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
  - Preset endpoint: `https://api.openai.com/v1/audio/transcriptions` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt`
  - Auth: `Authorization: Bearer <apiKey>` header in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
- OpenAI Chat Completions (Smart Fix post-processing)
  - Used in `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt` (`callOpenAI`)
  - Preset endpoint: `https://api.openai.com/v1/chat/completions` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt`
  - Auth: `Authorization: Bearer <apiKey>` header in `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- Google Gemini (Smart Fix post-processing)
  - Used in `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt` (`callGoogle` -> `generateContent`)
  - Preset base endpoint: `https://generativelanguage.googleapis.com/v1beta` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt`
  - Auth: `x-goog-api-key: <apiKey>` header in `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- Self-hosted Whisper ASR webservice (speech-to-text)
  - Request shape varies for `ProviderType.WHISPER_ASR` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` (query params + `audio_file` form field)
  - Preset placeholder endpoint: `http://YOUR_SERVER_IP:9000/asr` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt`
- Custom endpoints (provider-defined)
  - Provider model and endpoint fields are persisted as user settings in `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` and edited via UI in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`

**Update distribution (GitHub):**
- GitHub Releases assets (`latest.json` + APKs)
  - Client pulls `latest.json` from `https://github.com/<repo>/releases/download/<tag>/latest.json` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`
  - Tag selection: `latest` (stable) vs `nightly` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`
- GitHub REST API (release notes/changelog)
  - `GET https://api.github.com/repos/<repo>/releases/latest` and `.../releases/tags/nightly` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`

## Data Storage

**Databases:**
- Not applicable (no Room/SQLite database detected)

**File Storage:**
- Local files only
  - Recorded audio stored under cache directory (e.g., `externalCacheDir`) in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
  - Downloaded update APK stored in app external files `Download/` via `DownloadManager` in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateDownloader.kt`
  - `FileProvider` configuration for APK install sharing: `android/app/src/main/AndroidManifest.xml` + `android/app/src/main/res/xml/file_provider_paths.xml`

**Caching:**
- None (no explicit cache service)

## Authentication & Identity

**Auth Provider:**
- API key-based (user-entered)
  - OpenAI: bearer token in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` and `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
  - Google Gemini: `x-goog-api-key` header in `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- No end-user identity / OAuth flow detected

## Monitoring & Observability

**Error Tracking:**
- None detected (no Crashlytics/Sentry/etc.)

**Logs:**
- Android `Log` + OkHttp logging interceptor
  - HTTP logging with header redaction: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` and `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
  - App logs for update checks/download/install: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/**`

## CI/CD & Deployment

**Hosting:**
- GitHub Releases (APK distribution)
  - Release automation creates/updates tags/releases and uploads APKs + `latest.json` in `fastlane/Fastfile`

**CI Pipeline:**
- GitHub Actions workflow: `.github/workflows/android-ci.yml`
  - Builds on pushes to `main` and tags `v*`
  - Runs Fastlane (`bundle exec fastlane nightly` or `bundle exec fastlane release`)

## Environment Configuration

**Required env vars:**
- CI secrets for signing and release automation (consumed in `.github/workflows/android-ci.yml` and `fastlane/Fastfile`):
  - `KEYSTORE_BASE64`
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`
  - `GITHUB_TOKEN`

**Secrets location:**
- GitHub Actions secrets (`.github/workflows/android-ci.yml`)
- User API keys stored in app preferences (DataStore) via `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`

## Webhooks & Callbacks

**Incoming:**
- None detected

**Outgoing:**
- None (besides the HTTP calls to provider endpoints and GitHub URLs listed above)

---

*Integration audit: 2026-02-26*
