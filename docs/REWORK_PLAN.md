# Whisper++ Rework Plan

> Status: planning document, produced 2026-10-12 from a read-only audit of the codebase
> (4 parallel audits + manual verification) and a study of VoiceInk (`/Users/shady/github/Beingpax/VoiceInk`).
> This file is the single source of truth for the rework. Implementation agents must read
> §0 and §7 before touching code, then pick a work package from §6.

---

## 0. Goals, non-goals, and quality bar

**Goal.** Make Whisper++ feel like a first-party, Google-quality Android app and keyboard:
polished Material 3 UI, correct Telegram-style voice gestures, a waveform that actually
follows the voice, contextual permissions, music pause/resume while recording, and a clean
architecture modelled on VoiceInk's layering.

**Non-goals.**
- NO on-device / local models (no whisper.cpp, no Gemini Nano, no Ollama). Cloud-only.
- No feature removal that breaks existing user data (providers, prompts, backups, analytics).
- No visual "redesign for its own sake" that drops Material 3 or dynamic color.

**Quality bar (definition of done for the whole rework).**
- `./android/gradlew assembleDebug lintDebug testDebugUnitTest` passes.
- Keyboard never gets stuck in a non-`Ready` state and never fails to render.
- Press-and-hold, slide-to-cancel, slide-to-lock, tap-to-stop behave exactly as §4.1.
- Waveform latency from voice to screen is under ~100 ms, 60 fps, no spring overshoot.
- Music pauses on record start and resumes on stop/cancel/error (§4.3).
- `RECORD_AUDIO` is only ever requested in a user-initiated context with rationale.
- Every user-visible string is in `strings.xml`. Every icon button has a `contentDescription`.
- No file over ~600 lines; `WhisperInputService` is a thin platform adapter.

---

## 1. Current state snapshot

| Area | Files | Lines | Notes |
|---|---|---|---|
| IME service | `WhisperInputService.kt` | 2319 | God class: IME lifecycle, Compose state, recorder, STT, enhancement, command mode, undo, clipboard, disclosure dialogs, sounds |
| Keyboard UI | `ui/keyboard/KeyboardScreen.kt` | 1819 | Gesture handler, waveform, all keyboard composables in one file |
| Settings UI | `ui/settings/SettingsScreen.kt` | 4346 | Routes + NavHost + ~10 screens + provider editor + HTTP calls from composables |
| Backup UI | `ui/settings/BackupRestoreScreen.kt` | 1151 | Full restore state machine inside a composable |
| Data | `data/SettingsRepository.kt`, `data/SettingsBackupRepository.kt` | 871 + 1473 | Keys duplicated across 3 files |
| Network | `WhisperTranscriber.kt`, `SmartFixer.kt`, `dictation/OpenAiRealtimeSttClient.kt` | 310 + 262 + 202 | 3 separate OkHttp clients, branching on `ProviderType`, no provider interface |
| Toolchain | AGP 8.2.2, Kotlin 1.9.0, Compose BOM 2024.02.00, compileSdk/targetSdk 34, Gradle 8.5 | | Stale for 2026; no version catalog |

What is already good (keep it): Material 3 + dynamic color, Compose Navigation, DataStore,
Keystore-encrypted secrets (`privacy/SecretsStore.kt`), encrypted backups, privacy
disclosure flow, analytics without raw text, header-redacting network logs, solid test
coverage for data/privacy/command layers, updater structure (DownloadManager + SHA-256 + FileProvider).

---

## 2. Problem catalogue

IDs are stable; reference them in commits (`fix(G-03): …`). Severity: **C**ritical / **H**igh / **M**edium / **L**ow.
Line numbers are as of commit `fa21dd5`.

### A. Keyboard disappears / does not show (IME lifecycle)

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| A-01 | H | `WhisperInputService.kt:712-714`, `WhisperTranscriber.kt:163-167` | `ChineseUtils.preLoad(...)` ×2 runs synchronously on the main thread inside `onCreateInputView()` (loads dictionaries from `quick-transfer-core`). Delays first frame; on slow devices the IME window can be shown with an empty view. The library IS a real feature (Traditional↔Simplified post-processing option), so it stays. | Remove the `preLoad` calls from `onCreateInputView()`; lazy-load on `Dispatchers.IO` the first time the Chinese post-processing option is actually selected (in the pipeline's `TranscriptFilter`), never in the IME view path. |
| A-02 | H | `WhisperInputService.kt:300-302` | `runBlocking { repository.migrateProviderSchemaV2IfNeeded() }` in `onCreate()` blocks IME creation on DataStore I/O. | Run migration from `MainActivity`/`App` startup or a `WorkManager` one-shot; the service should only read already-migrated state asynchronously. |
| A-03 | M | `WhisperInputService.kt:205-226, 291-295, 740-750, 2256-2275` | Service is its own `LifecycleOwner`; `ON_START/ON_RESUME` fire only in `onWindowShown()`, and `ON_DESTROY` only in `onDestroy()`. `ComposeView` uses `DisposeOnViewTreeLifecycleDestroyed`, so if the system calls `onCreateInputView()` again (config change, theme change, display change) the old composition is never disposed and the new one may attach to a lifecycle in `STOPPED` state → blank keyboard. No `onFinishInputView`, no `onEvaluateInputViewShown`, no `onConfigurationChanged` handling. | Introduce a per-input-view lifecycle: create a fresh `LifecycleOwner`/`SavedStateRegistryOwner`/`ViewModelStoreOwner` wrapper per `onCreateInputView()`, move it to `RESUMED` in `onStartInputView`, `STARTED/CREATED` in `onFinishInputView`, and `DESTROYED` when the view is replaced or in `onDestroy`. Override `onConfigurationChanged` to call `setInputView(onCreateInputView())`. Add `onEvaluateInputViewShown()` returning `true` to ignore hardware-keyboard hiding. |
| A-04 | M | `KeyboardScreen.kt:621-626` | Keyboard surface hard-codes 72 dp + navigation-bar inset + 15 dp padding; disclosure sheets / command overlay stack above. Height may exceed the IME window or double-apply insets. | Define one `KeyboardMetrics` (height in dp per state, min 56 dp touch targets) and apply `WindowInsets.navigationBars` exactly once via `Modifier.navigationBarsPadding()` at the root. |
| A-05 | M | `WhisperInputService.kt:2270-2289` | `onWindowHidden()` stops recorder/transcriber and resets to `Ready` only when not "external sending"; otherwise it pauses. State and in-flight jobs can diverge from UI when the window returns. | Fold into the `DictationSession` state machine (§4.2): `onWindowHidden` → `session.suspend()`; `onWindowShown` → `session.resume()` or `reset()`. One place decides. |
| A-06 | L | `WhisperInputService.kt:332-359` | `focusInstanceId` incremented in both `onStartInput` and `onStartInputView` → two focus keys per editor; `DictationController.onFocusChanged` fires twice. | Increment only in `onStartInput(restarting=false)`. |
| A-07 | L | `WhisperInputService.kt:291-295` | `performRestore(null)` with no matching save; saved-state owner is a lie. | Drop the owner or implement a real save/restore in the per-view lifecycle wrapper. |

### B. Gesture system (press / cancel / lock / stop)

Current state machine (verified):

| State at pointer-down | Current behaviour |
|---|---|
| `Ready` | Haptic + `onMicAction()` immediately on **down**. On release: left ≥ 420 px → discard; up ≥ 220 px → lock; else `onMicAction()` **again**. |
| `Recording` | On release: left → discard; up → lock; else `onMicAction()` → pause. |
| `RecordingLocked` | Release within 400 ms → unlock (→ `PausedLocked`); otherwise drag → left discards. |
| `Paused` | Release within 400 ms → send; hold > 400 ms → resume then cancel/lock on release. |
| `PausedLocked` | **No handler.** Button renders but does nothing. |

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| B-01 | C | `KeyboardScreen.kt:1316-1383`, `WhisperInputService.kt:1196-1260` | Double-start race. `onMicAction()` on `Ready` launches a coroutine (DataStore read + provider resolution + optional first-use disclosure) **before** state changes. A quick release calls `onMicAction()` again while state is still `Ready` → second coroutine, two disclosures, or start-then-immediate-pause. Root cause of "sometimes it records, sometimes it doesn't". | Gesture emits intents (`PressStarted`, `PressReleased`, `DragUpdated`, `Cancelled`), not toggles. Session owns a `Starting` state that swallows a release by cancelling the pending start. Only one in-flight start at a time (`Mutex`/job reference). |
| B-02 | H | `KeyboardScreen.kt:1306-1310, 1316-1561` | `PausedLocked` has no gesture branch; icon/action mismatch across states. | Exhaustive `when` over `KeyboardState` in the gesture handler; unit-test each state × (tap, hold, slide-left, slide-up). |
| B-03 | H | `KeyboardScreen.kt:1264-1265` | Thresholds `420f` / `220f` are **raw pixels** → different physical distance on every device. Left/up progress are compared against each other even though thresholds differ, so direction flips mid-drag. | Thresholds in dp via `LocalDensity` (cancel ≈ 120 dp of button-relative travel, lock ≈ 80 dp). Decide axis after touch slop (`viewConfiguration.touchSlop`) and lock it for the gesture with hysteresis. |
| B-04 | H | `KeyboardScreen.kt:1330-1383` | Actions commit only on release, pointer-leave is not a cancel, no handling of `PointerEventType.Release` vs system cancel (window hide mid-drag). | Treat pointer cancel / window hide as `GestureCancelled` → restore previous state. Commit cancel the moment the threshold is crossed (Telegram) with a bounce-back animation if the user drags back before release. |
| B-05 | M | `KeyboardScreen.kt:1321-1377` | Haptics fire on down, on threshold, again on release; threshold haptic can repeat on 0.9 ↔ 1.0 jitter. | One haptic per committed transition: `start` (LongPress), `lock` (Confirm), `cancel` (Reject), `stop` (Confirm). Use `HapticFeedbackConstants.CONFIRM/REJECT` on API 30+. |
| B-06 | M | `KeyboardScreen.kt:639-647, 701-722` | Cancel hint is a static "swipe to discard" + pulsing trash. Not progressive. | Telegram-style: hint text + chevron slide with finger, alpha fades with progress, trash icon scales/shakes at threshold, red tint. |
| B-07 | M | `WhisperInputService.kt:1473-1481` | Timer uses `System.currentTimeMillis()` and a 33 ms loop. | `SystemClock.elapsedRealtime()`; drive the label from `withFrameMillis` or a 100 ms tick. |
| B-08 | M | `WhisperInputService.kt:1433-1443`, `DictationController.kt` | Short recording (< 500 ms) resets UI to `Ready` via `pauseRecording()` but leaves `DictationController.activeSession` set. | Terminal `Cancelled` transition clears controller + recorder + UI in one place. |
| B-09 | L | `KeyboardScreen.kt:1316-1561` | Gesture code duplicated for `Ready` and `Recording` branches (~120 lines each). | One drag-tracking helper used by all branches. |

### C. Waveform not synchronized with voice

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| C-01 | H | `RecorderManager.kt:194-207` | Peak (max abs sample) per `AudioRecord.read()` buffer, not RMS. Jumpy, poorly correlated with loudness. | Compute RMS per chunk (and keep peak for a clip indicator). |
| C-02 | H | `res/values/constants.xml:4`, `RecorderManager.kt:209-220` | Amplitude published every **150 ms** via a `Dispatchers.Main` polling coroutine. ~6.6 updates/s → visibly behind the voice. | Publish from the recording thread into a `MutableStateFlow<Float>` (or `AtomicInteger`) on every chunk; the UI samples it per frame. Remove the polling job and the resource integer. |
| C-03 | M | `KeyboardScreen.kt:1183-1197` | `log10(peak)` normalisation over a hand-picked 10–25 000 range; `animateDpAsState(spring(StiffnessLow))` adds hundreds of ms of lag and overshoot. | Convert to dBFS: `20·log10(rms/32768)`, clamp to [-50, 0] dB → [0,1]. Smooth with asymmetric attack/release EMA (attack ≈ 0.6 new, release ≈ 0.15 new; VoiceInk uses 0.6/0.4). Draw with `Canvas` + `withFrameNanos`, no per-bar `animate*AsState`. |
| C-04 | M | `RecorderManager.kt:76-79` | `lastAmplitude` written on recorder thread, read on main thread without `@Volatile`/atomic. | `AtomicInteger` or `MutableStateFlow`. Reset to 0 on pause/stop. |
| C-05 | M | `KeyboardScreen.kt:1178-1207` | 5 fixed bars with static multipliers `[0.5,0.8,1,0.8,0.5]` — a VU meter, not a waveform. | Telegram/VoiceInk style: scrolling history of N (e.g. 40–60) RMS samples at ~50 Hz, mirrored bars, rounded caps, `primary` colour, fades at edges. See §4.4. |
| C-06 | L | `ui/components/VoiceWaveform.kt` vs `KeyboardScreen.kt:1179` | Two different composables named `VoiceWaveform`; the `ui/components` one is a decorative Google-coloured loop with an unused `isDark`. | Rename to `DecorativeWaveform` / `LiveWaveform`. |

### D. Permission timing

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| D-01 | H | `MainActivity.kt:78-82, 114-123` | `checkPermissions()` on every `onCreate()` requests `RECORD_AUDIO` **and** `POST_NOTIFICATIONS` back-to-back with the legacy `requestPermissions` API, before the user has seen anything (and behind a 2.5 s fake splash). | Delete. Permissions are requested only from the onboarding "Microphone" step and from the keyboard's permission trampoline (D-02), using `ActivityResultContracts.RequestPermission`, with rationale UI and a "denied → open app settings" state. |
| D-02 | H | `WhisperInputService.kt:1352-1355, 2224-2230` | When permission is missing the IME launches `MainActivity` (splash + settings home) and silently resets to `Ready`. User is dumped out of their app with no explanation. | Add a transparent `PermissionRequestActivity` (`Theme.Translucent.NoTitleBar`, `excludeFromRecents`, `noHistory`) that shows a short rationale, requests `RECORD_AUDIO`, and finishes. The IME shows an inline "Microphone access needed — Allow" chip in the keyboard and retries the pending action on return. |
| D-03 | M | `RecorderManager.kt:51-54, 267-278` | `POST_NOTIFICATIONS` is treated as **required for recording**. Denying notifications blocks the mic. | `requiredPermissions()` = `RECORD_AUDIO` only. Notifications are requested only when the user enables update notifications (`UpdateCheckWorker`). |
| D-04 | M | `AndroidManifest.xml:26, 29` | `WRITE_EXTERNAL_STORAGE` (backup uses SAF) and `RECEIVE_BOOT_COMPLETED` (receiver is a no-op) are unused. | Remove both, plus the boot intent filter. |
| D-05 | L | `SettingsHomeScreen.kt:139-154`, `SettingsScreen.kt:3986-4093` | Mic-permission UI duplicated (`SuggestedCard` is unused). | Single `SetupChecklist` component (§4.6). |

### E. Audio focus / pause music while recording  (new feature)

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| E-01 | H | `RecorderManager.kt`, `WhisperInputService.kt:2172` | No `AudioFocusRequest` anywhere; only `playSoundEffect` uses `AudioManager`. Music keeps playing and bleeds into the recording. | New `AudioFocusController` (§4.3): request `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` with `AudioAttributes(USAGE_ASSISTANCE_ACCESSIBILITY? no → USAGE_VOICE_COMMUNICATION, CONTENT_TYPE_SPEECH)` before `AudioRecord.startRecording()`, abandon on stop / cancel / error / `onDestroy`. Media apps that honour focus pause and resume automatically. Setting: "Pause media while recording" (default on). |
| E-02 | M | `WhisperInputService.kt:2144-2175` | Start/stop/pause sounds via ad-hoc `MediaPlayer`s, not coordinated with focus; can be captured by the mic. | Use `SoundPool` with `USAGE_ASSISTANCE_SONIFICATION`, play the start cue **before** `startRecording()` and the stop cue after `stop()`. Respect the user's "Recording sounds" toggle and silent mode. |

### F. Threading, cancellation, error handling

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| F-01 | H | `RecorderManager.kt:223-250` | `stop()` does `recordingThread.join(1000)` on the main thread → up to 1 s keyboard freeze / ANR risk. | Recorder runs on a dedicated single-thread dispatcher; `suspend fun stop()`; UI transitions immediately, file finalisation completes asynchronously and reports via the session. |
| F-02 | H | `RecorderManager.kt:98-138`, `WhisperInputService.kt:1344-1365` | `start()` returns `Unit`; on `AudioRecord` init failure the object leaks and the service already set `Recording` + started the timer → fake recording state. | `start(): Result<Unit>`; release partial resources in `finally`; UI enters `Recording` only on success; failure → `Error(MicUnavailable)` state with message. |
| F-03 | M | `RecorderManager.kt:120-125, 209-220`, `WhisperInputService.kt:324-329, 1473-1481` + many | Unowned `CoroutineScope(Dispatchers.Main/Default)` everywhere; `onDestroy` cancels only some jobs. | Service-level `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` cancelled in `onDestroy`; children for recorder/session. |
| F-04 | M | `WhisperInputService.kt:967-980` | `runBlocking` DataStore read in `refreshExternalSendBlock()` on focus changes. | Collect send policy into a `StateFlow` once; read the cached value. |
| F-05 | M | `RecorderManager.kt:116-125, 158-163` | Realtime PCM channel capacity 8, `trySend` result ignored → silent audio drops; stream diverges from WAV. | Count drops; if > 0 mark stream degraded and force batch fallback for the final result. |
| F-06 | M | `RecorderManager.kt:136-173` | Write/read failures only logged; no error callback → UI stays in `Recording`. | Recorder emits `RecorderEvent.Error`; session transitions to `Error`. |
| F-07 | M | `WhisperInputService.kt:1778-1800` | Command-mode transcription failure becomes `""` → "silence, retry?" instead of an error. | Keep `Result.failure` distinct from blank text. |
| F-08 | M | `WhisperInputService.kt:1196, 1756-1760` | Mic action has no guard during command-mode listening; two recordings can collide. | Command listening is a `DictationSession` state; mic is disabled/ignored while active. |

### G. Dictation pipeline & network

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| G-01 | H | `dictation/EnhancementRunner.kt:66-89`, `SmartFixer.kt:117-137` | `withTimeout(2500/1200)` wraps a **blocking** `Call.execute()`; the timeout cannot interrupt it, so the user waits for the 30 s OkHttp timeout. One shared `inFlightCall` across retries. | `suspend fun` via `Call.await()` (cancellable, cancels the call on coroutine cancel), one call per attempt. |
| G-02 | H | `WhisperTranscriber.kt:196-204`, `WhisperInputService.kt:363-379, 632-654` | On failure the null-result callback runs **before** the exception callback and clears realtime state, so a usable realtime transcript is thrown away. | Replace the two-callback API with `suspend fun transcribe(...): Result<Transcript>`; snapshot the realtime fallback before clearing. |
| G-03 | H | `dictation/DictationController.kt:253-284, 312-340` | Streaming mode: insertion range captured from selection while a composing region exists → wrong range; `replaceCapturedSegment` can clobber unrelated text. `FakeInputConnection` doesn't model composing regions. | `finishComposingText()` (or track composing start) before snapshot; extend the fake and add a regression test. |
| G-04 | H | `data/RuntimeSelectionResolver.kt:38-62`, `WhisperTranscriber.kt:242-302` | `MULTIMODAL` (Gemini) models are selectable for STT but the request is always Whisper multipart → guaranteed 404/400. | Capability flags on `ModelConfig` (`supportsStt`, `supportsText`, `supportsStreaming`), validated at selection time. Either implement Gemini audio (`generateContent` with inline audio) as a `TranscriptionProvider` or block it. |
| G-05 | M | `WhisperTranscriber.kt:61-68, 111`, `SmartFixer.kt:41-46`, `OpenAiRealtimeSttClient.kt`, provider test in `SettingsScreen.kt` | Four separate OkHttp clients; a new one per transcription. | One `OkHttpClient` in `AppContainer`; per-request timeouts via `newBuilder()`/`Call.timeout()`. |
| G-06 | M | `WhisperTranscriber.kt:181-203` | Cancellation is converted into `(null, null)` and still invokes the callback; a hide-triggered cancel can commit fallback text. | Cancellation is terminal; no completion callbacks after cancel. |
| G-07 | M | `dictation/DictationController.kt:66-74, 163-183` | Focus change while paused: later send binds to the **new** focus key → text lands in the wrong field. | Bind session to its start `FocusKey`; on mismatch, offer "Insert here / Copy / Discard". |
| G-08 | M | `SmartFixer.kt:103` | Unsupported enhancement provider type silently returns the original text. | Surface `EnhancementSkipped(reason)` notice. |
| G-09 | M | `WhisperTranscriber.kt:181-184` | Audio file deleted only on success. | Delete in `finally` unless "keep last recording for retry" is enabled (VoiceInk feature; see §5). |
| G-10 | L | `SmartFixer`, strings | "SmartFix" / "Smart Fix" / "enhancement" / "post-processing" used interchangeably. | One term everywhere: **Enhancement** (code: `Enhancement*`). |
| G-11 | H | `dictation/OpenAiRealtimeSttClient.kt`, `WhisperInputService.kt:283-288, 1248-1415` | Realtime (WebSocket) STT exists but is OpenAI-only, selected by a string heuristic (`supportsOpenAiRealtime`), and wired into the service through six mutable fields (`realtimeSttClient`, `realtimeToken`, `realtimeFinalTranscript`, `realtimeBestEffortTranscript`, `pendingRealtimeProvider`, `pendingRealtimeModelId`). No protocol abstraction, no reconnect, no timeout, no explicit commit/finalise contract, fallback to batch is implicit and racy (G-02). | `StreamingTranscriptionSession` protocol (§4.5.1) implemented per provider; selected from model capabilities, owned by `DictationSession`. Also apply the `isTransportAllowed` guard (WP-0.3) to the `ws://` upgrade path, which the old client lacks. |
| G-12 | M | `ui/settings/SettingsScreen.kt:2845-2934, 3315-3323` | "Import models" is a one-off HTTP call inside a composable that assumes the OpenAI `/v1/models` shape, dumps raw JSON in a dialog, and does not classify models (STT vs text vs realtime). Users otherwise hand-type model IDs. | `ModelDiscovery` capability per provider (§4.5.2) with classification + caching; UI "Refresh models" in the provider editor and model pickers. |

### H. Settings app UI/UX

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| H-01 | H | `SettingsHomeScreen.kt:158-250, 286-360` | No guided first run. Checklist checks "IME enabled" but not "IME selected", not mic, not credentials. Setup banner and device card repeat each other. | Onboarding flow (§4.6) + one `SetupChecklist` card on Home that re-evaluates on `ON_RESUME`. |
| H-02 | H | `MainActivity.kt:87-103`, `ui/components/SplashScreen.kt` | Fixed 2.5 s Compose splash. | `androidx.core:core-splashscreen`, `installSplashScreen()` with `setKeepOnScreenCondition` tied to real init; delete `SplashScreen.kt`. |
| H-03 | H | `SettingsScreen.kt` (4346 lines) | Routes, NavHost, 10+ screens, provider editor (`2420-3587`) doing HTTP from composables, URL building, JSON parsing. | Split per §4.7; provider test/import moves to `ProviderConnectionTester` in domain; each screen gets a `ViewModel`. |
| H-04 | M | `SettingsHomeScreen.kt:286-493` | Home is long, mixes setup, analytics, maintenance, updates; advanced options (verbose logs, timeouts, thinking config, model import) not separated. | Information architecture in §4.8: Home = setup status + 6 destinations; "Advanced" sub-screen. |
| H-05 | M | all settings screens | Repositories created with `remember`, flows collected with `collectAsState`, writes via `rememberCoroutineScope`; drafts live in composables and die on recreation. `lifecycle-viewmodel-compose` is a dependency but unused. | `ViewModel` + `collectAsStateWithLifecycle` + `SavedStateHandle` for drafts. |
| H-06 | M | `SettingsScreen.kt`, `SettingsHomeScreen.kt:271-479`, `PrivacySafetyScreen.kt:199-423`, `BackupRestoreScreen.kt:307-982`, `WhisperInputService.kt` toasts | Most settings/privacy/backup copy is hard-coded English in Kotlin; toasts in the service too. No `values-ar`. `supportsRtl` alone doesn't help. | Move everything to `strings.xml`; add `values-ar/strings.xml`; use `Icons.AutoMirrored.*`; test in RTL. |
| H-07 | M | `ui/theme/Type.kt`, `Color.kt`, many screens | Typography defines only `bodyLarge`; `sp` sizes inline; shapes/spacing set per card; fallback (pre-12) palette is default purple. | Full M3 type scale, `Shapes`, `Spacing` tokens; branded fallback palette generated from the app-icon blue. |
| H-08 | M | `AnalyticsDashboardCard.kt:122-213`, custom rows | Chart bars without semantics; custom clickable cards without `Role.Button`. | `Modifier.semantics { contentDescription = … }` summarising the 7 days; `Role.Button` on cards; TalkBack pass. |
| H-09 | M | all screens | Phone-only single column, fixed padding. | `WindowSizeClass`; constrain content width to 600 dp on wide screens; list/detail for providers on expanded. |
| H-10 | M | `SettingsScreen.kt:154-169, 2224-2228`, `AnalyticsDashboardScreen.kt:56-67` | Migration failure has no recovery UI; analytics "not loaded" looks like zero; provider empty state is plain text. | Explicit `Loading / Empty / Error(retry)` states; actionable empty states. |
| H-11 | M | `BackupRestoreScreen.kt:105-1151` | Restore wizard state machine in a composable. | `BackupRestoreViewModel` with a sealed `RestoreStage`. |
| H-12 | M | `SettingsHomeScreen.kt:467-481` | "Updates" card with internal-guidance copy and a no-op click. | Replace with a real `UpdateStatusRow`. |
| H-13 | L | build.gradle.kts | Navigation 2.7.7, Compose BOM 2024.02 → no predictive-back animations; no `SharedTransition`. | Toolchain upgrade (M-01) then verify predictive back; enable `android:enableOnBackInvokedCallback="true"`. |
| H-14 | L | `WhisperInputService.kt:2127-2140` | AppCompat `AlertDialog` from the IME for language menu / disclosures. | In-keyboard Compose bottom-sheet-style overlays (already exist for disclosures) for everything. |
| H-15 | L | `KeyboardScreen.kt:661-805, 1566-1620` | Icon buttons with `contentDescription = null`; gesture-only mic has no accessibility actions. | Labels + `customActions` (start, stop, cancel, lock, send). |
| H-16 | L | `KeyboardScreen.kt:1098, 1121` | Hard-coded `Color(0xFFFFD600)` and literal strings ("Transcribing...", "Undo"). | Theme colours + resources. |

### I. Architecture & code health

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| I-01 | H | `WhisperInputService.kt` | 2319-line god class (see §1). | Thin adapter over `DictationSession`, `KeyboardViewModel`, `AudioFocusController`, `TextCommitter`, `CommandModeController`. Target < 400 lines. |
| I-02 | H | `WhisperTranscriber`, `SmartFixer`, `OpenAiRealtimeSttClient` | No provider interface; branching on `ProviderType`. | `TranscriptionProvider` / `EnhancementProvider` / `StreamingTranscriptionSession` interfaces (§4.5). |
| I-03 | M | `MainActivity.kt:27-70`, `SettingsRepository.kt:39-47`, `SettingsBackupRepository.kt:19-47` | DataStore keys defined in three places. | `data/PreferenceKeys.kt` single object. |
| I-04 | M | `SettingsBackupRepository.kt` (1473), `SettingsRepository.kt` (871) | Oversized. | Split: `BackupCodec`, `BackupMergeResolver`, `RestoreRepairPlanner`, `BackupApplier`; `ProviderRepository`, `PromptRepository`, `AppMappingRepository`, `LegacyImportExport`. |
| I-05 | M | everywhere | Manual wiring per screen/service. | `AppContainer` (manual DI, no Hilt needed) exposed from `Application`. |
| I-06 | L | `WhisperInputService.kt:1000-1010, 1452-1458`, `DictationController.kt:112-113`, `SettingsScreen.kt:3986-4093` | Dead code: `stopExternalSendingForBlockedPolicy`, `cancelRecording`, empty `onWindowShown`, `SuggestedCard`. | Delete. |
| I-07 | L | `build.gradle.kts` | Verified unused: `constraintlayout`, `runtime-livedata` (removed in 39f2d0a). `quick-transfer-core` is used by the Chinese post-processing option (A-01) and stays. `appcompat` is used only for the IME `PopupMenu`/`AlertDialog` (goes away with H-14). `material` (views) is still needed by `themes.xml` (`Theme.Material3.DayNight` parent). | Remove the three unused deps now; drop `appcompat` after H-14; keep `material`. |

### J. Security & privacy

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| J-01 | H | `AndroidManifest.xml:49` | `usesCleartextTraffic="true"` globally while uploading audio + API keys. README recommends `http://` for self-hosted. | `network_security_config.xml`: cleartext **off** by default; per-provider "Allow insecure HTTP (local network only)" toggle that adds a domain-config for that host at runtime is not possible, so instead: allow cleartext only for RFC1918/loopback hosts via a config + validate in provider editor; document it. |
| J-02 | M | `res/xml/backup_rules.xml`, `data_extraction_rules.xml`, `SecretsStore.kt:110` | Encrypted secrets prefs are included in OS backup but the Keystore key isn't → restored keys undecryptable and silently "missing". | Exclude `whisperpp_secrets.xml` from backup; on decrypt failure show "re-enter API key". |
| J-03 | M | `data/SettingsBackupCrypto.kt:73-120` | KDF params (iterations, key length) trusted from the imported file. | Clamp iterations to [100k, 2M], key length to 256. |
| J-04 | L | `WhisperInputService.kt` | Toasts show provider host on errors — fine — but ensure no transcript text in logs after refactor. | Keep `NetworkLoggingPrivacyTest`; add log-redaction lint rule or test for `Log.*` in dictation package. |

| H-17 | M | `analytics/AnalyticsRepository.kt:21-43, 161-169`, `AnalyticsFormatter.kt:20-26` | "Time saved so far" is a 7-day rolling total presented as lifetime. | Lifetime counters in `AnalyticsStore` (WP-3.9). |

### K. Updater

| ID | Sev | Where | Problem | Fix |
|---|---|---|---|---|
| K-01 | M | `updater/UpdateCheckWorker.kt:46-54` | Every error → `Result.retry()`, including permanent "no APK for ABI". | Classify transient vs permanent. |
| K-02 | L | `updater/UpdateCard.kt:64-67` | `pubDate.substringBefore("T")` NPE-prone via Gson. | Validate at parse boundary; fallback text. |
| K-03 | L | manifest | Boot receiver no-op (see D-04). | Remove. |

### L. Tests

| ID | Sev | Gap | Add |
|---|---|---|---|
| L-01 | H | Gesture state machine | Pure-Kotlin `RecordGestureStateMachine` tests: each state × tap/hold/slide-left/slide-up/cancel; threshold hysteresis. |
| L-02 | H | `DictationSession` | State transition tests incl. hide mid-transcribe, focus change while paused, start failure, cancel during enhancement. |
| L-03 | H | Streaming replacement (G-03) | `FakeInputConnection` with composing-region semantics + regression. |
| L-04 | M | Network | MockWebServer tests: non-2xx mapping, timeout cancellation (G-01), callback ordering (G-02), Gemini request shape. |
| L-05 | M | Waveform | `AmplitudeMeter` tests: RMS, dBFS mapping, attack/release. |
| L-06 | M | Audio focus | Fake `AudioManager` wrapper: request before start, abandon on every terminal path. |
| L-07 | M | Updater | Metadata parse, ABI selection, retry classification, hash mismatch. |
| L-08 | L | Compose UI | Keyboard screenshot/semantics tests per state; onboarding flow test. |

### M. Build & toolchain

| ID | Sev | Problem | Fix |
|---|---|---|---|
| M-01 | H | AGP 8.2.2 / Kotlin 1.9.0 / Compose compiler 1.5.1 / BOM 2024.02 / compileSdk 34 / Gradle 8.5. Blocks predictive back, M3 1.3+ components, `collectAsStateWithLifecycle`, K2. | Upgrade to current stable: AGP 8.x latest, Kotlin 2.x with Compose compiler Gradle plugin, Compose BOM latest, compileSdk/targetSdk 36 (or latest stable), Gradle 8.x latest. Add `gradle/libs.versions.toml`. Fix behaviour changes (foreground service types, edge-to-edge enforcement on 35+, `BroadcastReceiver` export rules). |
| M-02 | M | `isMinifyEnabled = false` in release. | Enable R8 with rules for Gson models (or migrate to `kotlinx.serialization`). |
| M-03 | L | AGENTS.md says Java 1.8 but the build uses 17. | Fix AGENTS.md. |

---

## 3. Root-cause summary (why the user sees what they see)

| Symptom | Root causes |
|---|---|
| Keyboard sometimes disappears / doesn't render | A-01 (blocking dictionary preload), A-02 (`runBlocking` migration), A-03 (lifecycle tied to service, no re-creation path), A-04 (height/inset doubling) |
| Gestures unreliable: "didn't record", "recorded then stopped", lock does nothing | B-01 (double-start race), B-02 (no `PausedLocked` handler), B-03 (px thresholds), B-04 (commit-on-release only), B-08 (controller state leak) |
| Waveform out of sync | C-01 (peak), C-02 (150 ms polling), C-03 (spring lag), C-04 (unsynchronised field) |
| Permissions asked at odd times | D-01 (on launch), D-02 (IME dumps you into the app), D-03 (notifications gate the mic) |
| Music keeps playing | E-01 (no audio focus) |
| Random freezes while stopping | F-01 (`join(1000)` on main), F-04 (`runBlocking` on focus) |
| Enhancement "hangs" | G-01 (uncancellable blocking call) |

---

## 4. Target design

### 4.1 Gesture spec (Telegram-like)

```
                 ┌──────────── pointer down ───────────┐
Idle ───────────►│ Pressing (t < HOLD_MS=180)          │
                 └──┬──────────────────────┬───────────┘
      release < HOLD_MS │                  │ t ≥ HOLD_MS (haptic LongPress)
                        ▼                  ▼
                 Tap → toggle*       Recording(hold)
                                      │  drag left ≥ CANCEL_DP (120dp)   → Cancelled (haptic Reject, trash anim)
                                      │  drag up   ≥ LOCK_DP   (80dp)    → RecordingLocked (haptic Confirm, lock anim)
                                      │  release                          → Stopping → Transcribing
                                      │  pointer cancel / window hidden   → Cancelled
RecordingLocked: tap main button → Stopping; tap trash → Cancelled; tap pause → PausedLocked
PausedLocked:    tap main (send) → Stopping; tap mic → RecordingLocked; tap trash → Cancelled
```

- `*` Tap in `Idle` starts a **locked** recording directly (hands-free). Configurable: "Tap to record" on/off (default on, like Telegram's tap-to-switch-mode? No — Telegram requires hold; we offer the setting because keyboard users often prefer tap).
- Axis lock: after `touchSlop`, whichever of |dx|,|dy| is larger wins for the rest of the gesture; progress on the other axis is zero.
- Cancel UI: "‹ Slide to cancel" text tracks the finger with alpha `1 - progress`, trash icon scales `1 → 1.4` and turns `error` colour at threshold; crossing threshold commits immediately.
- Lock UI: lock icon rises with progress; at threshold it "clicks" shut.
- Haptics: `LongPress` on start, `Confirm` on lock/stop, `Reject` on cancel; never repeated within one gesture.
- Timer: `mm:ss` from `elapsedRealtime`, red dot blinking at 1 Hz.
- All thresholds and durations in one `GestureTokens` object; `RecordGestureStateMachine` is pure Kotlin and unit-tested (L-01).

### 4.2 `DictationSession` state machine (replaces state spread across Service/Controller/Recorder)

```kotlin
sealed interface SessionState {
  data object Idle
  data class Starting(val focus: FocusKey, val locked: Boolean)
  data class Recording(val focus: FocusKey, val startedAt: Long, val locked: Boolean, val paused: Boolean)
  data class Transcribing(val focus: FocusKey, val audio: File, val partial: String?)
  data class Enhancing(val focus: FocusKey, val raw: String)
  data class Committing(val focus: FocusKey, val text: String)
  data class Error(val kind: ErrorKind, val recoverable: Boolean)
  data object Cancelled
}
```
- Single owner: `DictationSession` (domain, no Android UI deps; `InputConnection` behind `TextCommitter`).
- Inputs: gesture intents, recorder events, provider results, `onWindowHidden/Shown`, focus changes, permission results.
- Every terminal transition releases: recorder, audio focus, in-flight calls, temp file (per G-09), timer.
- `KeyboardState` (UI enum) becomes a projection: `SessionState → KeyboardUiState`.
- Pipeline (VoiceInk): `record → transcribe (stream|batch, fallback) → filter/replacements → enhance (optional, failure keeps raw) → commit → history`.

### 4.3 `AudioFocusController`

```kotlin
class AudioFocusController(audioManager: AudioManager, settings: Flow<Boolean /*pauseMedia*/>) {
  fun acquire(): Boolean   // AudioFocusRequest(GAIN_TRANSIENT_EXCLUSIVE) + AudioAttributes(USAGE_VOICE_COMMUNICATION, CONTENT_TYPE_SPEECH), acceptsDelayedFocusGain=false, willPauseWhenDucked=true
  fun release()            // abandonAudioFocusRequest; idempotent
}
```
- Called by `DictationSession` on `Starting → Recording` and on every terminal state.
- Ownership rule (VoiceInk `PlaybackController`): only release what we acquired; never call `abandon` if `acquire` failed.
- `AUDIOFOCUS_LOSS` while recording → pause recording and show "Interrupted" (phone call).
- Setting "Pause media while recording" (default **on**); "Recording sounds" (default on) use `SoundPool` + `USAGE_ASSISTANCE_SONIFICATION` (E-02).

### 4.4 Live waveform

- `AmplitudeMeter` (pure Kotlin): per chunk compute RMS and peak → dBFS → normalised 0..1 with floor -50 dB; EMA attack 0.6 / release 0.15; `StateFlow<Float>` updated per chunk (16 kHz, 20 ms chunks → 50 Hz).
- `LiveWaveform` composable: `Canvas`; keeps a ring buffer of the last 48 levels sampled via `withFrameNanos` (sample at 50 Hz, render at 60 fps, interpolate); mirrored rounded bars 3 dp wide / 2 dp gap, `primary` colour with edge fade; min bar height 2 dp so silence still reads as "live".
- Zero allocations per frame (pre-sized `FloatArray`).

### 4.5 Provider abstraction (cloud-only)

```kotlin
interface TranscriptionProvider {
  val id: ProviderId; val capabilities: Capabilities // languages, streaming, vocabulary, prompt
  suspend fun transcribe(audio: File, req: TranscriptionRequest): Result<Transcript>
  fun openStream(req: TranscriptionRequest): StreamingTranscriptionSession? // null if unsupported
  suspend fun verify(credentials: Credentials): Result<Unit>
}
interface EnhancementProvider {
  suspend fun enhance(req: EnhancementRequest): Result<String>  // system prompt + <TRANSCRIPT> + context
  suspend fun verify(credentials: Credentials): Result<Unit>
}
```
- `ModelConfig` gains `capabilities: ModelCapabilities(stt, text, realtime, thinking, vocabulary)` replacing the loose `kind`/`streamingPartialsSupported`/`isThinking` flags (with a DataStore migration). Capabilities are filled by discovery (§4.5.2) or edited manually.
- Implementations: `OpenAiCompatibleTranscription` (OpenAI, Groq, Whisper ASR webservice, NIM, custom), `GeminiTranscription` (inline audio `generateContent`) if we keep Gemini STT, `OpenAiRealtimeStreaming`; `OpenAiCompatibleChat`, `GeminiChat`, `AnthropicMessages` (optional). Streaming implementations follow §4.5.1.
- One `OkHttpClient` (shared, in `AppContainer`), `Call.await()`, typed `NetworkError { Unauthorized, RateLimited, Timeout, Http(status, host), Offline, InvalidResponse }` → `strings.xml` messages.
- Enhancement: ≥ 1 s spacing, exponential backoff on 429/5xx (VoiceInk), total budget configurable, failure keeps raw text with a notice.
- Prompt composition (VoiceInk `AIEnhancementService`): general system template + selected prompt + custom vocabulary + context sources (selected text/clipboard; opt-in per mode), transcript wrapped in `<TRANSCRIPT>`, strip reasoning blocks, reject empty.

#### 4.5.1 Realtime / streaming protocol

Every provider that supports realtime STT exposes it through one contract (VoiceInk `TranscriptionSession` + `TranscriptionServiceRegistry`):

```kotlin
interface StreamingTranscriptionSession {
  val events: Flow<StreamEvent>            // Connected, Partial(text), Final(text), Error(NetworkError), Closed
  suspend fun start()                      // open WS, send session config (model, language, vocabulary, VAD off)
  suspend fun sendAudio(pcm16: ByteArray)  // 16 kHz mono PCM chunks straight from AudioRecorder
  suspend fun commit(): Result<Transcript> // "input_audio_buffer.commit" (OpenAI) / Finalize (Deepgram) / end-of-stream; awaits the final transcript with a timeout
  suspend fun cancel()                     // close without committing
}
```
Rules:
- `DictationSession` decides streaming vs batch from `ModelConfig.capabilities.realtime` (set by discovery or by the user), never from URL heuristics.
- The recorder always writes the WAV too. On `commit()` timeout/error, or if PCM drops were counted (F-05), the session falls back to **batch** with the same file. Fallback is explicit and tested.
- Partials go to the composing region via `TextCommitter.setComposing()`; `Final` replaces the composing region atomically (fixes G-03).
- Reconnect once on transient WS failure before `commit()`; never after.
- Implementations: `OpenAiRealtimeSession` (`wss://…/v1/realtime?intent=transcription`, `transcription_session.update`, `input_audio_buffer.append/commit`, events `conversation.item.input_audio_transcription.delta/completed`), `DeepgramLiveSession` (`wss://api.deepgram.com/v1/listen`, `Finalize`/`CloseStream`), `ElevenLabsScribeSession`, `MistralVoxtralSession` (as in VoiceInk). Ship OpenAI first, with the interface and fallback path proven; others as follow-ups.
- Provider editor shows a "Realtime" badge per model and a toggle "Use realtime transcription when available" in Dictation settings.

#### 4.5.2 Model discovery

```kotlin
interface ModelDiscovery {
  suspend fun listModels(creds: Credentials): Result<List<DiscoveredModel>>
}
data class DiscoveredModel(val id: String, val displayName: String, val kinds: Set<ModelKind>, val realtime: Boolean, val thinking: Boolean, val raw: Map<String,String>)
```
- `OpenAiCompatibleDiscovery`: `GET {base}/v1/models` → classify by id patterns (`whisper*`, `*transcribe*` → STT; `*realtime*` → realtime STT; `gpt*`, `o*`, `llama*`, `mixtral*` → TEXT; `*tts*`, `*embedding*`, `dall-e*`, `*moderation*` → ignored). Works for OpenAI, Groq, OpenRouter, vLLM, LM-Studio-style servers.
- `GeminiDiscovery`: `GET https://generativelanguage.googleapis.com/v1beta/models?key=…` → use `supportedGenerationMethods` (`generateContent` → TEXT/MULTIMODAL; `bidiGenerateContent` → realtime), `inputTokenLimit`, `displayName`.
- Providers without a listing endpoint (Whisper ASR webservice, NIM, custom) return `Unsupported` → UI keeps manual entry.
- Results cached in `ProviderRepository` with a timestamp; "Refresh models" button; discovered models can be added individually or "Add all STT models" / "Add all text models". Manual models are never deleted by a refresh; the editor marks models that disappeared from the listing as "not found".
- Discovery is also used by the onboarding "Choose a provider" step and by the provider **Test connection** action (a successful listing proves auth + reachability).

### 4.6 Permission & onboarding flow

Onboarding (first launch, 4 steps, Material 3 pager, skippable except step 1):
1. **Welcome** — what it does, privacy one-liner (audio goes to the provider you choose).
2. **Enable keyboard** — button opens `ACTION_INPUT_METHOD_SETTINGS`; detects `enabled` via `InputMethodManager.enabledInputMethodList`, `selected` via `Settings.Secure.DEFAULT_INPUT_METHOD`; second button `showInputMethodPicker()`.
3. **Microphone** — rationale → `RequestPermission`; denied-permanently → "Open settings".
4. **Choose a provider** — OpenAI / Groq / Gemini / Custom; key field (secure), "Get API key" link, **Test connection** with progress + result (VoiceInk `AIProviderVerificationCard`).
Finish → Home with `SetupChecklist` (enabled ✓, selected ✓, mic ✓, provider ✓) re-checked on resume.

Keyboard-side: if mic is missing when the user presses record → inline chip "Allow microphone" → `PermissionRequestActivity` (translucent) → return → retry pending action. Never launch the full app from the IME for permissions.

### 4.7 Package structure (target)

```
com.github.shekohex.whisperpp
├── app/            WhisperApp.kt (Application, AppContainer), MainActivity.kt
├── di/             AppContainer.kt (manual DI)
├── core/
│   ├── audio/      AudioRecorder.kt (suspend start/stop, events), AmplitudeMeter.kt, AudioFocusController.kt, SoundCues.kt, RiffWaveHelper.kt
│   ├── network/    HttpClientFactory.kt, CallAwait.kt, NetworkError.kt
│   └── text/       WordReplacements.kt, TranscriptFilter.kt (fillers, trim), PromptComposer.kt
├── data/
│   ├── PreferenceKeys.kt
│   ├── settings/   SettingsRepository.kt (split), AppMappingRepository.kt, PromptRepository.kt
│   ├── providers/  ProviderRepository.kt, ProviderModels.kt, SecretsStore.kt
│   ├── backup/     BackupCodec.kt, BackupMergeResolver.kt, RestoreRepairPlanner.kt, BackupApplier.kt, SettingsBackupCrypto.kt
│   ├── history/    TranscriptionHistoryStore.kt (new, Room or DataStore-proto)
│   └── analytics/  (as is)
├── domain/
│   ├── dictation/  DictationSession.kt, SessionState.kt, DictationPipeline.kt, TextCommitter.kt, FocusKey.kt, UndoStack.kt
│   ├── providers/  TranscriptionProvider.kt, EnhancementProvider.kt, StreamingTranscriptionSession.kt, ProviderConnectionTester.kt
│   ├── command/    (existing command/*)
│   ├── privacy/    (existing privacy/*)
│   └── setup/      SetupStatusChecker.kt (ime enabled/selected, mic, provider)
├── providers/      openai/, gemini/, realtime/  (implementations)
├── ime/
│   ├── WhisperInputService.kt (thin), ImeViewLifecycle.kt, PermissionRequestActivity.kt
│   └── ui/         KeyboardScreen.kt, RecordButton.kt, RecordGestureStateMachine.kt, GestureTokens.kt, LiveWaveform.kt, RecordingTimer.kt, CancelHint.kt, LockHint.kt, KeyboardOverlays.kt (disclosure, command), KeyboardViewModel.kt
├── ui/
│   ├── theme/      Theme.kt, Color.kt, Type.kt, Shape.kt, Spacing.kt
│   ├── components/ SettingsRow.kt, SectionCard.kt, StatusChip.kt, EmptyState.kt, ErrorState.kt, SecureTextField.kt, DecorativeWaveform.kt
│   ├── onboarding/ OnboardingScreen.kt, OnboardingViewModel.kt, steps/*
│   ├── home/       HomeScreen.kt, SetupChecklist.kt, HomeViewModel.kt
│   ├── providers/  ProviderListScreen.kt, ProviderEditScreen.kt, ProviderEditViewModel.kt, ModelPicker.kt
│   ├── dictation/  DictationSettingsScreen.kt (language, tap-to-record, sounds, pause media, auto-send)
│   ├── enhancement/ EnhancementScreen.kt, PromptsScreen.kt, PromptEditScreen.kt, PresetsScreen.kt
│   ├── apps/       AppMappingsScreen.kt, AppMappingDetailScreen.kt
│   ├── history/    HistoryScreen.kt (new)
│   ├── privacy/    PrivacySafetyScreen.kt
│   ├── backup/     BackupRestoreScreen.kt, BackupRestoreViewModel.kt
│   ├── analytics/  (as is, with semantics)
│   ├── about/      AboutScreen.kt (version, updates, licenses, advanced/diagnostics)
│   └── navigation/ AppNavHost.kt, Destinations.kt
└── updater/        (as is + fixes)
```

### 4.8 Screen map / information architecture

```
Onboarding (first run)  →  Home
Home (Dashboard, §4.9)   greeting · hero time-saved card · setup checklist · recent transcripts
├── Insights             stat tiles, activity calendar, peak hours, model usage/performance (§4.9)
├── Modes                mode cards with chips + toggles; add/edit mode; per-app assignment (§4.10)
├── History              search, list (raw/enhanced), copy, select-all/delete/export, retry from audio (if kept)
├── Dictionary           Word Replacements | Vocabulary; settings sheet with Auto-Learn, export/import (§4.11)
├── AI Models            providers list → edit (key, endpoint, Test connection, Refresh models, model capabilities)
├── Audio                microphone source, start/stop sounds (pick + preview), pause media while recording, resume delay, haptics
└── Settings
    ├── Dictation        language, tap-to-record vs hold, hold threshold, auto-send, keyboard height/theme
    ├── Privacy & Safety (existing) + disclosures + Auto-Learn data note
    ├── Backup & Restore (existing)
    └── About            version, update channel + check/download, Advanced (verbose logs, timeouts, thinking, diagnostics), licenses

Bottom navigation (phone): Home · Modes · History · Dictionary · More (AI Models, Audio, Settings). Navigation rail on expanded widths.
```

---

### 4.9 Home dashboard (VoiceInk Dashboard, Android-native)

Reference: VoiceInk `Features/Dashboard/*` (screenshots reviewed 2026-10-12). Home becomes a dashboard, not a settings list.

Layout (single `LazyColumn`, M3 cards, `WindowSizeClass`-aware two-column on expanded):
1. **Greeting** — "👋 Hi" + one-line momentum copy ("You're on a roll this week." / "Welcome back." / "Dictate something to unlock insights.") computed from the last 7 days.
2. **Hero card** (`DashboardHeroCard`): large "You have saved **41h 30m** with Whisper++", sub-line "Dictated 165.8K words, equivalent to Homer's Odyssey.", button "View insights →". Background: a soft gradient/illustration in `primaryContainer` tones (no photo), dynamic-color aware. Empty state: "Continue using Whisper++ to unlock stats and insights."
   - Time saved = `max(words / 40 wpm × 60 − recordingDurationSec, 0)`, summed **lifetime** (fixes the 7-day "so far" bug in `AnalyticsRepository`). Store lifetime `words`, `sessions`, `recordingSeconds`, `timeSavedSeconds` in `AnalyticsStore` alongside the rolling buckets.
   - Milestones (verbatim from `DashboardStatsModels.swift`): The Metamorphosis 21 180 · Animal Farm 29 966 · The Great Gatsby 47 094 · Homer's Iliad 114 715 · Homer's Odyssey 121 365 · Iliad and Odyssey 236 080 · repeat benchmark War and Peace 700 000 (×N). Below the first milestone: "12.3K words to go until The Metamorphosis".
3. **Setup checklist** card — only while something is missing (§4.6); disappears when all green.
4. **Recent transcripts** — last 5 from History (§4.11), relative time ("14 minutes ago"), two-line preview, tap → History detail, long-press → copy. Mixed-direction text must render correctly (`textDirection = TextDirection.Content`).
5. **Insights screen** (the existing Analytics screen, redesigned): period selector (7d / 30d / 90d / all), stat tiles (words, sessions, recording time, time saved), **Activity calendar** heatmap ("Consistency, at a glance", Less→More legend, 12–16 weeks), **Peak dictation hours** histogram (24 bins), **Model usage** (share per provider/model) and **Model performance** (avg latency per model). All charts drawn with `Canvas`, with semantics summaries for TalkBack. Data: extend `AnalyticsStore` with per-hour counts and per-model latency buckets (still no raw text).

### 4.10 Modes (unifies presets, per-app rules, language defaults)

Reference: VoiceInk `Features/Modes/*` and the Modes screenshot. A **Mode** bundles everything a dictation session needs:
`name, icon, sttProvider/model, language, enhancementEnabled, enhancementProvider/model, promptId, formatting (filler removal, paragraphs), contextSources (selectedText, clipboard), outputMode (insert | replaceSelection | clipboard), autoSend, appPackages[] (Android package names), isDefault`.

- Screen: list of mode cards; each card has title + icon, an enable toggle (except default), sub-line "3 apps", and a chip row: `🎙 model` · `🌐 language` · `✨ enhancement model` · `📄 prompt` · `⌨ apps`. Red "Unavailable" chip when a referenced provider/model/credential is missing (resolved by `RuntimeSelectionResolver`).
- Resolution at session start: explicit mode selected in the keyboard (mode chip in the keyboard toolbar) → mode mapped to `EditorInfo.packageName` → default mode. This replaces the current trio `AppMappings + LanguageProfiles + Presets` in the UI; the data layer keeps those stores and migrates them into `Mode` rows once (one-way, with a backup export prompt first). Seed modes: Dictation (default), Chat, Email, Rewrite, Assistant (prompts from §5).
- Keyboard: a small mode chip next to the language label; tap → bottom-sheet picker.

### 4.11 Dictionary: word replacements, vocabulary, Auto-Learn

Reference: VoiceInk `Features/Dictionary/*` (incl. `AutoLearn/*`, 3 453 lines) and the Dictionary screenshots.

**Dictionary screen** — segmented control `Word Replacements | Vocabulary`.
- Replacements: inline add row "Original text (use commas for multiple) → Replacement text", list with swipe-to-delete, case-insensitive, longest-first, word-boundary aware except CJK/Thai (`core/text/WordReplacements.kt`). Applied in the pipeline **before** enhancement.
- Vocabulary: chips of terms; sent to providers that accept a prompt/vocabulary (`capabilities.vocabulary`), and injected into the enhancement system prompt as "Vocabulary: …".
- Settings sheet (gear icon): Auto-Learn toggle, provider + model picker (text model), "Review corrections: Immediately | Daily | Manually", "Corrections to review: N" + "Review now", Export / Import dictionary (JSON via SAF).

**Auto-Learn (Android design)** — learns from what the user fixes by hand after a dictation.
1. **Capture.** When `DictationSession` commits text into a field, `AutoLearnObserver` records `baseline = (fieldTextSnapshot, committedRange, committedText)` using `InputConnection.getExtractedText` (fallback: `getTextBeforeCursor/AfterCursor` with a 2 000-char window). While the same `FocusKey` stays active, `onUpdateSelection`/`onUpdateExtractedText` keep a cheap "dirty" flag. On `onFinishInput`, focus change, window hidden, or a 90 s deadline, take `final = fieldText` and persist an `AutoLearnRevision(original, corrected)` if the committed region changed. Auto-send (Enter) cancels the capture (text left the field). Never capture in secure fields (`SecureFieldDetector`) or blocked apps (`SendPolicyRepository`).
2. **Diff.** `CorrectionDiffEngine` (port of the Swift one): tokenise into word/boundary segments, LCS hunks, merge adjacent hunks, expand each hunk by up to 3 unchanged words on each side → `DetectedCorrectionCandidate(originalText, correctedText)`. Limits: max 400 segments per revision, max 20 candidates, skip pure whitespace/punctuation/case-only hunks.
3. **Queue.** `AutoLearnPendingQueue` (DataStore-proto or Room): candidates persisted with `claimed` flag; batches of ≤ 25; retried when a provider becomes available.
4. **AI review.** `AutoLearnAiReviewer` sends the batch to the configured text model with the VoiceInk review prompt (copy verbatim from `AutoLearnAIReviewer.swift:431-474`; it encodes the "phonetic evidence" and "no semantic rewrite" gates, personal-name rules, and the exact JSON output contract: `[{candidateID, learningAction, incorrectTextToReplace, correctedVocabularyTerm}]` with actions `addReplacementAndVocabulary | addReplacementOnly | addVocabularyOnly | rejectCorrection`). Validate every decision: `incorrectTextToReplace` must be a contiguous substring of the candidate's original, `correctedVocabularyTerm` must come from a corrected text; conflicting accept+reject for one ID → unresolved; unknown IDs ignored. Strip reasoning blocks before JSON parse.
5. **Proposals + apply.** Accepted decisions become `AutoLearnReviewProposal`s. "Immediately" → a notification/in-app banner "Learned 2 corrections" with Undo; "Daily"/"Manually" → badge on Dictionary + "Review now" sheet listing `original → corrected` with per-row action chips (Replacement / Vocabulary / Both / Dismiss). Applying writes to `WordReplacements`/`Vocabulary`; an `AutoLearnMutationSummary` supports undo for 30 s.
6. **Privacy.** Only the diff snippets (edit ± 3 words) leave the device, and only to the user-chosen provider; show this in the Auto-Learn info dialog and in Privacy & Safety. Off by default.

## 5. What to take from VoiceInk (and what not)

**Port (concept, in Kotlin):**
1. Layering: `Engine/Session` → `Pipeline` → provider registry → delivery. Immutable "resolved runtime config" per session (`ModeRuntimeConfiguration`).
2. Separate roles for transcription vs enhancement providers with capability metadata.
3. Graceful enhancement failure: deliver raw text + notice; backoff on 429/5xx; min 1 s between calls.
4. Prompt library (Default / Chat / Email / Rewrite / Assistant) + general system template that treats context as data, not instructions; `<TRANSCRIPT>` wrapping. Chat template (verbatim from `PromptTemplates.swift`):
   ```
   <TASK>
   Rewrite <TRANSCRIPT> as an informal, concise, and conversational chat message.
   </TASK>

   <RULES>
   - Keep emotive markers and emojis if present; don't invent new ones.
   - Format lists only when distinct items are clear: number ordered steps or explicitly numbered items; otherwise use bullets. A count alone does not make a list.
   - Format like a modern chat message - short lines, natural breaks, emoji-friendly.
   - Do not add greetings or sign-offs.
   </RULES>
   ```
5. Dictionary: word replacements (longest-first, case-insensitive, word-boundary aware; skip boundaries for CJK/Thai) and custom vocabulary passed to providers that support it and into the enhancement prompt.
6. History with raw + enhanced text, provider/model, duration, failure state; search; retry from saved audio (opt-in "keep last recording").
7. Media pause/resume with **ownership** semantics (only undo what we did) → Android: audio focus (§4.3).
8. Level meter: RMS/peak in the audio callback, EMA smoothing, 16 ms render tick decoupled from audio cadence.
9. Provider setup UX: secure key field, "Get API key" link, Test connection with distinct auth/URL/reachability errors; HTTPS required except loopback/LAN.
10. Shortcut semantics as settings: push-to-talk (hold) vs toggle vs hybrid (hold threshold 0.5 s in VoiceInk; we use 180 ms for touch).
11. Auto-send option: after commit, send `KEYCODE_ENTER` / `performEditorAction(IME_ACTION_SEND)` after ~150 ms; per-app override.
12. Onboarding sequencing and copy style (explain → act → verify), not the macOS screens.
13. Streaming as a protocol: `TranscriptionSession` with prepare/append/finalize/cancel and automatic batch fallback (`TranscriptionSession.swift`, `TranscriptionServiceRegistry.swift`); per-provider realtime implementations (OpenAI, Deepgram, ElevenLabs, Mistral).
14. Model discovery / "Import models" as a provider capability with classification, feeding the model picker and the connection test (`ProviderCloudManagementView.swift`, `CustomModelConnectionTester.swift`).
15. Dashboard: hero "time saved" card with the 40-wpm formula and literary milestones, recent transcripts, activity calendar, peak hours, model usage/performance (`Features/Dashboard/*`). See §4.9.
16. Modes as the single user-facing concept bundling STT/enhancement/prompt/apps (`Features/Modes/*`). See §4.10.
17. Dictionary with Word Replacements + Vocabulary and **Auto-Learn** (`Features/Dictionary/AutoLearn/*`: `CorrectionDiffEngine`, `AutoLearnAIReviewer` prompt, pending queue, review proposals). See §4.11.

**Do not port:** notch/mini-window UI, Accessibility-API paste, foreground app/URL detection beyond `EditorInfo.packageName`, system-wide mute, Ollama/local models, licensing.

---

## 6. Phased implementation plan

Each work package (WP) is sized for one agent session, lists its problem IDs, files, and acceptance criteria.
Order within a phase is flexible unless a dependency is noted. Commit per WP with the WP id in the message.

### Phase 0 — Foundation (unblocks everything) — DONE (39f2d0a, 975582c, 6ac0ced)

**WP-0.1 Toolchain upgrade** — M-01, M-02, M-03, H-13
- `android/build.gradle.kts`, `android/app/build.gradle.kts`, `gradle-wrapper.properties`, new `gradle/libs.versions.toml`, `AGENTS.md`.
- Kotlin 2.x + Compose compiler plugin, latest AGP/BOM, compileSdk/targetSdk latest stable, `core-splashscreen`, `lifecycle-runtime-compose`, `material3-window-size-class`, `kotlinx-coroutines-test`, `mockwebserver`.
- Remove unused deps (I-07): `constraintlayout`, `runtime-livedata`. Keep `quick-transfer-core` (real feature, see A-01), `appcompat` until H-14, and `material` (needed by `themes.xml`). DONE in 39f2d0a.
- Accept: `assembleDebug lintDebug testDebugUnitTest` green; APK installs on the test device; no behaviour change.

**WP-0.2 AppContainer + shared HTTP + PreferenceKeys** — I-03, I-05, G-05
- New `app/WhisperApp.kt`, `di/AppContainer.kt`, `core/network/*`, `data/PreferenceKeys.kt`.
- Move all DataStore keys; one `OkHttpClient`; `Call.await()` helper.
- Accept: all existing tests pass; no duplicated key constants remain (grep).

**WP-0.3 Manifest & security hygiene** — J-01, J-02, J-03, D-04, K-03
- `network_security_config.xml`, remove unused permissions/receiver filter, exclude secrets from backup, clamp KDF params, provider editor HTTPS rule + LAN exception.
- Accept: lint clean; `SettingsBackupCryptoTest` extended; README updated.

### Phase 1 — Keyboard core (the user-facing bugs)

**WP-1.1 Audio core** — C-01..C-04, F-01, F-02, F-05, F-06, E-01, E-02, L-05, L-06
- New `core/audio/AudioRecorder.kt` (suspend API, events, dedicated dispatcher, `Result` on start), `AmplitudeMeter.kt`, `AudioFocusController.kt`, `SoundCues.kt`. Delete `RecorderManager.kt` after migration.
- Accept: unit tests for meter/focus; manual: music pauses on start, resumes on stop/cancel; no main-thread `join`.

**WP-1.2 DictationSession state machine** — A-05, B-08, F-03, F-07, F-08, G-02, G-06, G-07, L-02
- New `domain/dictation/DictationSession.kt`, `SessionState.kt`, `TextCommitter.kt`. Absorb `DictationController` + the dictation parts of `WhisperInputService`.
- Accept: transition tests incl. hide/focus/cancel paths; service only forwards platform events.

**WP-1.3 Gesture rework** — B-01..B-07, B-09, L-01, H-15
- New `ime/ui/RecordGestureStateMachine.kt` (pure), `GestureTokens.kt`, `RecordButton.kt`, `CancelHint.kt`, `LockHint.kt`, `RecordingTimer.kt`.
- Depends on WP-1.2 (intents go to the session).
- Accept: state-machine tests; manual Telegram parity checklist in §4.1; accessibility custom actions present.

**WP-1.4 Live waveform** — C-05, C-06, H-16
- `LiveWaveform.kt` per §4.4; rename decorative one.
- Accept: visually tracks speech with no perceptible lag; 60 fps in GPU profiler; zero per-frame allocations.

**WP-1.5 IME lifecycle hardening** — A-01..A-04, A-06, A-07
- `ime/ImeViewLifecycle.kt`; per-view owners; `onConfigurationChanged`, `onEvaluateInputViewShown`, `onFinishInputView`; remove preload/`runBlocking`.
- Accept: rotate / theme switch / switch IME and back / split-screen → keyboard always renders; log transitions in debug builds.

**WP-1.6 Permissions from the keyboard** — D-01, D-02, D-03, D-05
- `ime/PermissionRequestActivity.kt` (translucent), inline "Allow microphone" chip, remove `MainActivity.checkPermissions()`, `requiredPermissions = RECORD_AUDIO`.
- Accept: fresh install → first press shows rationale chip → grant → recording starts without leaving the host app.

### Phase 2 — Providers & pipeline

**WP-2.1 Provider interfaces + OpenAI-compatible impls** — I-02, G-01, G-04, G-08, G-09, G-10, L-04
- `domain/providers/*`, `providers/openai/*`, `providers/realtime/*`; `WhisperTranscriber`/`SmartFixer` → thin adapters then deleted. Rename "SmartFix" → "Enhancement" in code and strings.
- Accept: MockWebServer tests for errors/timeouts/cancellation; enhancement cancellable within budget.

**WP-2.2 Gemini provider** — G-04
- `providers/gemini/GeminiChat.kt` (exists in SmartFixer, move) and `GeminiTranscription.kt` (inline audio) **or** capability-block Gemini for STT. Decide by testing the API once; prefer implementing.
- Accept: capability validation at selection; test for request shape.

**WP-2.3 Streaming correctness** — G-03, L-03
- Composing-region-safe commit; `FakeInputConnection` upgrade.
- Accept: regression test green.

**WP-2.5 Streaming protocol + OpenAI Realtime** — G-11, G-02, F-05, §4.5.1
- `domain/providers/StreamingTranscriptionSession.kt`, `providers/realtime/OpenAiRealtimeSession.kt`; remove the six `realtime*` fields from the service; `DictationSession` owns the session and the batch fallback. Delete `OpenAiRealtimeSttClient.kt`.
- Accept: fake-WS unit tests for partial/final/commit-timeout/fallback; manual: partials appear as composing text and the final transcript replaces them without duplication; airplane mode mid-stream falls back to batch or shows a clean error.

**WP-2.6 Model discovery** — G-12, §4.5.2
- `domain/providers/ModelDiscovery.kt`, `providers/openai/OpenAiCompatibleDiscovery.kt`, `providers/gemini/GeminiDiscovery.kt`, cache in `ProviderRepository`; replace `importModels()` in the provider editor with a `ModelDiscoveryViewModel`-driven sheet (refresh, filter STT/Text/Realtime, add selected / add all, "not found" markers).
- Accept: MockWebServer tests for both listing shapes and the classifier; manual: OpenAI key → list shows whisper/gpt-4o-transcribe/realtime models correctly classified; Gemini key → list shows flash/pro models.

**WP-2.4 Pipeline extras from VoiceInk** — §5 items 3–5, 11
- `core/text/TranscriptFilter.kt`, `WordReplacements.kt`, vocabulary in prompt/provider, prompt library seeds, backoff policy, auto-send option.
- Accept: unit tests for replacements/filter; prompts visible in Enhancement screen.

### Phase 3 — App UI rework

**WP-3.1 Theme & components** — H-07, H-08 (components part)
- `ui/theme/*` full M3 scale, shapes, spacing, branded fallback palette; shared `SettingsRow`, `SectionCard`, `StatusChip`, `EmptyState`, `ErrorState`, `SecureTextField`.
- Accept: light/dark/dynamic screenshots; contrast ≥ 4.5:1.

**WP-3.2 Splash + navigation shell + ViewModels** — H-02, H-03 (split), H-05, H-10
- `core-splashscreen`; `ui/navigation/AppNavHost.kt`; split `SettingsScreen.kt` into feature packages (§4.7) with `ViewModel`s and `collectAsStateWithLifecycle`; delete `SplashScreen.kt`.
- Accept: no file > 600 lines in `ui/`; navigation + back + predictive back verified.

**WP-3.3 Onboarding + SetupChecklist** — H-01, D-01, §4.6
- `ui/onboarding/*`, `domain/setup/SetupStatusChecker.kt`, `ui/home/SetupChecklist.kt`.
- Accept: first-run flow completes end-to-end on device; checklist reflects enabled vs selected IME.

**WP-3.4 Home + IA + About/Advanced** — H-04, H-12
- Accept: Home ≤ one screen on a 6" phone when setup is complete.

**WP-3.5 Providers screens** — part of H-03; §5 item 9
- List/detail, edit with Test connection (`ProviderConnectionTester` in domain), model picker.
- Accept: no network code in composables.

**WP-3.6 Backup/Restore ViewModel** — H-11
**WP-3.7 Strings, RTL, accessibility, adaptive** — H-06, H-08, H-09, H-14
- All strings to resources; `values-ar`; `AutoMirrored` icons; `WindowSizeClass` width constraint; TalkBack pass; keyboard overlays replace `AlertDialog`.
**WP-3.8 History screen** — §5 item 6 (new feature; Room or proto DataStore; opt-in "keep audio").
**WP-3.9 Dashboard home + Insights** — §4.9; fixes the analytics "so far" bug (lifetime totals), adds per-hour and per-model buckets to `AnalyticsStore` (migration), hero card with milestones, activity calendar, peak hours, model usage/performance charts with semantics.
**WP-3.10 Modes** — §4.10; `Mode` model + repository + one-way migration from AppMappings/LanguageProfiles/Presets (after prompting a backup export), Modes screen with cards/chips/toggles, mode chip in the keyboard, resolver update. Largest Phase 3 WP; run with the `orchestrator` profile.
**WP-3.11 Dictionary UI** — §4.11 (replacements + vocabulary tabs, settings sheet, export/import). Depends on WP-2.4 for `WordReplacements`.
**WP-3.12 Auto-Learn** — §4.11 steps 1–6: `AutoLearnObserver` (IME capture), `CorrectionDiffEngine` port with tests ported from VoiceInk's cases, pending queue, AI reviewer with the verbatim prompt + validation tests, proposals review sheet, undo, privacy note. Off by default. Depends on WP-3.11 and WP-1.2.

### Phase 4 — Polish & hardening

**WP-4.1 Updater fixes + tests** — K-01, K-02, L-07
**WP-4.2 Dead code & service slimming audit** — I-01, I-06 (verify `WhisperInputService` < 400 lines)
**WP-4.3 Keyboard UI tests & screenshots** — L-08
**WP-4.4 Release prep** — R8 rules, version bump per AGENTS.md "Releasing", README rewrite (no `http://` advice, new setup steps).

---

## 7. Execution notes for implementation agents

- **Agents / profiles.** Run WPs with the Paseo `build` profile (Claude Sonnet 5.5, medium) or `orchestrator` (Claude Opus 5.5, medium) for cross-cutting WPs (1.2, 2.1, 3.2). UI WPs (1.3, 1.4, 3.x) → `ui` profile. Use `review` profile on each WP diff before commit.
- **One WP per agent session**, in a worktree (`isolation: worktree`, branch `rework/wp-x-y`). Rebase on `main` before PR.
- **Read first:** this file §0/§4, `AGENTS.md`, and every file listed in the WP. Do not read `VoiceInk` unless the WP cites §5.
- **Commands:** `./android/gradlew -p android assembleDebug lintDebug testDebugUnitTest` must pass before every commit (Gradle 9 needs `-p android` when run from the repo root). Instrumented tests: `connectedDebugAndroidTest` when a device is attached. Use a PTY for long Gradle runs (AGENTS.md).
- **Conventions:** Kotlin official style, 4 spaces, explicit imports, one top-level class per file, strings in resources, `val` over `var`, sealed types + `when`, no `runBlocking` on the main thread, no unowned `CoroutineScope`, no `GlobalScope`.
- **Don't:** add local/on-device models; add Hilt/Koin (manual `AppContainer` is enough); change DataStore key names (migrations only); log transcript text or keys; leave TODOs without an ID from §2.
- **Manual test checklist for the user (device):** cold start → onboarding; enable + select IME; mic grant from inside another app; hold/slide-left/slide-up/tap/tap-when-locked; rotate while recording; music playing → record → stop; airplane mode → record → error state and recovery; switch to Gboard and back; dark/light; Arabic locale + RTL.
