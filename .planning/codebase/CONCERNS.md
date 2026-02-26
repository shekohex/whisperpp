# Codebase Concerns

**Analysis Date:** 2026-02-26

## Tech Debt

**Monolithic UI/screens (hard to change safely):**
- Issue: Single-file Compose screens with large surface area and mixed responsibilities.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` (1437 lines), `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt` (906 lines)
- Impact: High regression risk; slow reviews; recomposition/perf issues harder to localize.
- Fix approach: Split by feature/route into smaller composables + state holders; keep navigation in a small file and move each screen/section to its own file.

**Monolithic IME service doing UI + state + I/O:**
- Issue: InputMethodService owns recording, timers, provider selection, network job orchestration, UI state.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` (610 lines)
- Impact: Fragile lifecycle/cancellation; difficult to test; risk of leaks / orphaned coroutines.
- Fix approach: Introduce a dedicated controller (e.g., `TranscriptionController`) and a single structured scope owned by the service (cancel in `onDestroy`/`onWindowHidden`).

**Settings persistence as large JSON blobs with silent failure modes:**
- Issue: Providers/profiles stored as JSON strings; parse errors return empty lists without surfacing corruption.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Impact: “All providers disappeared” scenarios; difficult migration; performance degradation as JSON grows.
- Fix approach: Add explicit versioned schema + migration; surface parse failures to UI; consider Room or per-provider keys instead of a single JSON blob.

**Unused/unfinished feature surface (profiles):**
- Issue: `LanguageProfile` exists but no production usage.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Impact: Confusing UX expectations; dead code increases maintenance cost.
- Fix approach: Either implement profile selection end-to-end or remove the model/exports until needed.

**Release hardening not enabled:**
- Issue: No shrinking/obfuscation in release.
- Files: `android/app/build.gradle.kts` (`isMinifyEnabled = false`), `android/app/proguard-rules.pro`
- Impact: Larger APK; easier reverse engineering; more methods/resources shipped.
- Fix approach: Enable R8 for release and add keep rules for reflection/Compose as needed.

## Known Bugs

**Cancelling transcription does not cancel the underlying HTTP call:**
- Symptoms: UI indicates cancel, but network request may keep running and audio may still be transmitted.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` (`OkHttpClient.newCall(...).execute()` inside coroutine), `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` (`whisperTranscriber.stop()`)
- Trigger: Call `WhisperTranscriber.stop()` while a request is in-flight.
- Workaround: None.

**Recorded audio file cleanup happens only on success (and not on cancel/error):**
- Symptoms: Audio remains in external cache until overwritten/cleared.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` (deletes file only on success), `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` (no delete on cancel/discard)
- Trigger: Timeout/error/cancel during transcription; cancel/discard recording.
- Workaround: Re-record to overwrite; clear app cache.

**Whisper ASR language selection ignored:**
- Symptoms: Always sends `language=auto` for Whisper ASR webservice requests.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
- Trigger: Use `ProviderType.WHISPER_ASR` with a non-"auto" `ServiceProvider.languageCode`.
- Workaround: Server-side override only.

**Gemini transcription provider path appears unsupported but selectable:**
- Symptoms: Requests built as multipart + `Authorization: Bearer ...`, which does not match Gemini API auth/body conventions.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt` (`ProviderType.GEMINI`), `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt` (Gemini preset), `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
- Trigger: Select a `ProviderType.GEMINI` provider for transcription.
- Workaround: Use `ProviderType.OPENAI`, `ProviderType.WHISPER_ASR`, or `ProviderType.CUSTOM` for transcription.

**Provider model selection for transcription is effectively fixed to the first model:**
- Symptoms: Multiple configured models exist but transcription uses `provider.models.first().id`.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Trigger: Configure multiple models in `SettingsScreen` provider editor.
- Workaround: Put the intended model first.

## Security Considerations

**Cleartext traffic globally enabled + HTTP endpoints shipped:**
- Risk: API keys and transcripts can be transmitted over HTTP if user selects/enters such endpoints.
- Files: `android/app/src/main/AndroidManifest.xml` (`android:usesCleartextTraffic="true"`), `android/app/src/main/res/values/strings.xml` (default `http://localhost...`), `android/app/src/main/java/com/github/shekohex/whisperpp/data/Presets.kt` (HTTP preset)
- Current mitigation: None.
- Recommendations: Disable cleartext by default; allow only loopback/selected domains via network security config (debug-only if possible) and warn/block when an API key is present on non-HTTPS.

**Backups enabled without explicit includes/excludes (API keys likely backed up):**
- Risk: Provider API keys and endpoints in DataStore may be included in cloud backup/device transfer.
- Files: `android/app/src/main/AndroidManifest.xml` (`android:allowBackup="true"`, `android:dataExtractionRules`), `android/app/src/main/res/xml/data_extraction_rules.xml` (TODO sample), `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` (keys in DataStore), `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` (exports API keys)
- Current mitigation: None.
- Recommendations: Add explicit exclude rules for preferences containing secrets; consider disabling backup or separating secrets into encrypted storage.

**HTTP logging prints sensitive payloads (Smart Fix):**
- Risk: Full request/response bodies can contain transcript/context and model outputs; logs can be read via logcat (debuggable devices, rooted devices, bug reports).
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt` (`HttpLoggingInterceptor.Level.BODY`)
- Current mitigation: Redacts auth headers only.
- Recommendations: Disable body logging outside debug builds; never log transcript/context; keep header-only logging if needed.

**Self-update + install-from-unknown-sources surface area:**
- Risk: Increase attack surface and policy risk; update verification relies on remotely fetched hash that can be tampered with alongside the APK if transport/source is compromised.
- Files: `android/app/src/main/AndroidManifest.xml` (`REQUEST_INSTALL_PACKAGES`, exported receiver), `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateDownloader.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateInstaller.kt`
- Current mitigation: SHA-256 comparison from `latest.json`.
- Recommendations: Verify APK signing certificate matches an embedded expected cert; sign metadata (`latest.json`) and verify signature locally; restrict receiver exposure.

**Unnecessary legacy/storage permissions:**
- Risk: `WRITE_EXTERNAL_STORAGE` is deprecated and flagged by Play policies; increases perceived risk.
- Files: `android/app/src/main/AndroidManifest.xml`
- Current mitigation: None.
- Recommendations: Remove `android.permission.WRITE_EXTERNAL_STORAGE` (app uses app-scoped external dirs via `getExternalFilesDir`/`externalCacheDir`).

## Performance Bottlenecks

**High-frequency UI timer updates on main thread:**
- Problem: 30fps-style loop updates state every ~33ms.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` (`startTimer()`)
- Cause: Tight loop to update `recordingTimeMs`.
- Improvement path: Use a lower update frequency (e.g., 100ms) or only update UI while recording and visible; consider `SnapshotFlow`/derived state.

**Smart Fix logging (BODY) can be extremely expensive:**
- Problem: Logging large JSON bodies for every request/response.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- Cause: `HttpLoggingInterceptor.Level.BODY`.
- Improvement path: Disable BODY logs; cap payload sizes if logging is required.

**Repeated heavy initialization on input view creation:**
- Problem: Preloading conversion tables at `onCreateInputView` time.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` (`ChineseUtils.preLoad(...)`)
- Cause: Runs every time IME view is created.
- Improvement path: Move to `onCreate()` or lazy-init once; offload to background thread.

## Fragile Areas

**Provider list parse failure silently becomes “no providers”:**
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Why fragile: Any JSON/schema drift or partial write turns into empty provider list with no diagnostics.
- Safe modification: Keep a last-known-good copy; add validation + error surfaced in UI; add migrations.
- Test coverage: Not detected.

**Updater receiver exported + multiple broadcast actions:**
- Files: `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/java/com/github/shekohex/whisperpp/updater/DownloadCompletedReceiver.kt`
- Why fragile: Broadcast spoofing/DoS possible; behavior depends on platform broadcast restrictions.
- Safe modification: Prefer WorkManager/job to resume and verify downloads; if keeping receiver, constrain exposure and defensively validate state/file paths.
- Test coverage: Not detected.

## Scaling Limits

**Settings export/import and provider storage scale with JSON size:**
- Current capacity: Entire provider/profile lists serialized/deserialized on access.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Limit: Large provider/model lists increase startup/compose state collection latency.
- Scaling path: Move to structured storage (Room) or incremental keys; avoid parsing on main-thread code paths.

## Dependencies at Risk

**Text conversion dependency risk:**
- Risk: Third-party library stability/maintenance; heavy init cost.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` (uses `com.github.liuyueyi.quick.transfer`), `android/app/build.gradle.kts` (`com.github.liuyueyi:quick-transfer-core:0.2.13`)
- Impact: Breakage on new Android/Compose versions; performance regressions.
- Migration plan: Evaluate built-in ICU/Android APIs for conversions or replace with a maintained library.

## Missing Critical Features

**No encryption for stored API keys:**
- Problem: API keys stored as plain strings in DataStore.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` (preference keys), `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` (editing/saving keys)
- Blocks: Strong at-rest protection for secrets.

**No robust update authenticity verification:**
- Problem: Hash is fetched from the same remote source as the APK metadata.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateDownloader.kt`
- Blocks: Strong protection against compromised distribution channel.

## Test Coverage Gaps

**Core logic untested (networking, updater, settings, IME service):**
- What's not tested: `WhisperTranscriber`, `SmartFixer`, update flow, DataStore migrations/import/export.
- Files: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/updater/*`, `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Risk: Regressions in networking/cancellation/permissions flow go unnoticed.
- Priority: High.

**Only template/example tests present:**
- Files: `android/app/src/test/java/com/github/shekohex/whisperpp/ExampleUnitTest.kt`, `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ExampleInstrumentedTest.kt`

---

*Concerns audit: 2026-02-26*
