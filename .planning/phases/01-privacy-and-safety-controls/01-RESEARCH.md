# Phase 01: Privacy & Safety Controls - Research

**Researched:** 2026-02-26
**Domain:** Android/Kotlin IME privacy controls (secure field gating, secrets storage, redacted networking logs, per-app send policy, disclosures)
**Confidence:** MEDIUM

## Summary

Phase 01 is cross-cutting: the IME service (`WhisperInputService`) must gate actions based on the current `EditorInfo` (secure field + target app package), the settings UI must add a dedicated “Privacy & Safety” screen plus per-app policy management, provider API keys must be removed from DataStore JSON and stored with Keystore-backed encryption, and all network/error logging must be redacted by default.

The codebase already has the right primitives (Compose UI, DataStore, OkHttp, provider models), but currently violates the phase requirements in several ways:
- API keys are stored in plaintext in DataStore via `ServiceProvider.apiKey` serialized into `providers_json` and also exported by `SettingsRepository.exportSettings()`.
- Smart Fix uses `HttpLoggingInterceptor.Level.BODY` and logs full error response bodies (likely contains user text).
- Whisper transcription errors are surfaced using raw response bodies (toast), which conflicts with “minimal safe error UI”.
- Cancel/stop does not cancel the underlying OkHttp call, so “stop immediately; no further sending” cannot be guaranteed today.

**Primary recommendation:** implement a single, central “send gate” in `WhisperInputService` driven by `EditorInfo` (secure-field + per-app policy) and enforce it *before* starting recording/transcription/smart-fix and *during* focus changes; move secrets out of provider JSON into a Keystore-backed secret store; make logging and error surfaces safe-by-default.

## Standard Stack

### Core (existing in repo)
| Library / API | Version (repo) | Purpose | Why Standard |
|---|---:|---|---|
| Android IME framework (`InputMethodService`, `EditorInfo`) | platform | Determine target app + field type | Only reliable signal an IME gets |
| Jetpack Compose + Material3 | Compose BOM `2024.02.00` | IME UI + settings UI | Existing UI stack |
| AndroidX DataStore (Preferences) | `1.0.0` | Persist non-secret settings | Existing persistence |
| OkHttp + logging-interceptor | `4.12.0` | Provider network calls + interceptors | Existing networking stack |
| Android Keystore (`AndroidKeyStore`, `KeyGenParameterSpec`, `Cipher`) | platform | Keystore-backed secret encryption | Recommended platform primitive |

### Supporting (recommended additions / patterns)
| Library / API | Version | Purpose | When to Use |
|---|---:|---|---|
| Manifest `<queries>` element | API 30+ | Enable listing installed apps under package visibility | Needed for “installed apps list” UI |
| OkHttp `HttpUrl` parsing | OkHttp `4.12.0` | Stable base URL + path disclosure; strip query | Don’t parse URLs manually |

### Alternatives considered
| Instead of | Could use | Tradeoff |
|---|---|---|
| Direct Keystore + AES/GCM wrapper | `androidx.security:security-crypto:1.1.0` | **Not recommended for new usage**: Jetpack release notes state APIs were deprecated in 1.1.0 beta/alpha in favor of platform Keystore APIs. Still widely used, but expect deprecation warnings and potential future removal. |
| `<queries>` for launcher apps | `QUERY_ALL_PACKAGES` permission | Broad visibility; Play-distribution sensitive and often rejected. Avoid unless absolutely required. |

## Architecture Patterns

### Recommended project structure (Phase 01 additions)
Keep changes localized and testable by adding small focused helpers instead of growing the monolith files further.

Suggested new packages/files:
```
android/app/src/main/java/com/github/shekohex/whisperpp/
├── privacy/
│   ├── SecureFieldDetector.kt
│   ├── SendPolicyRepository.kt
│   ├── SecretsStore.kt
│   ├── PrivacyDisclosureFormatter.kt
│   └── NetworkRedactor.kt
```

### Pattern 1: Centralized “Send Gate” in the IME
**What:** a single decision function that returns whether external sending is allowed for the *current editor* (secure-field), *current app* (per-app policy), and *current mode* (dictation vs smart-fix vs command).

**Where it belongs:** `WhisperInputService` because it has `currentInputEditorInfo` and owns the action lifecycle.

**Why:** prevents leakage via forgotten call sites (recording/transcription/smart-fix) and ensures focus-change stop behavior is enforced.

**Example (shape):**
```kotlin
data class SendBlock(
    val reason: Reason,
    val targetPackage: String?,
    val editorSummary: EditorSummary
) {
    enum class Reason { SecureField, PerAppPolicy }
}

fun currentSendBlock(
    editorInfo: EditorInfo?,
    perAppBlocked: Boolean
): SendBlock? = …
```

**Repo change points:**
- Add/override `onStartInput(editorInfo: EditorInfo, restarting: Boolean)` and/or `onStartInputView(editorInfo: EditorInfo, restarting: Boolean)` in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`.
- Call the gate in `onMicAction()`, `startRecording()`, `startTranscription()`, and SmartFix path inside `transcriptionCallback()`.

### Pattern 2: Secure field detection as a pure function
**What:** `SecureFieldDetector.isSecure(editorInfo)` returns a boolean + a structured reason (password-like, OTP-like, no-personalized-learning).

**Signals to use (locked decision = strict):**
- Password-like: `InputType.TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`.
- Privacy-flagged: `EditorInfo.imeOptions` contains `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING`.
- OTP-like: numeric/password variations **plus** a conservative heuristic for “verification code” fields (see pitfalls).

**Source:** `IME_FLAG_NO_PERSONALIZED_LEARNING` requests the IME not update personalized data based on what’s typed in that field.

### Pattern 3: Secrets storage split from provider config
**What:** provider config stays in DataStore JSON, but API keys are stored separately in a Keystore-backed secrets store keyed by `ServiceProvider.id`.

**Repo change points:**
- `ServiceProvider.apiKey` must not be serialized into `providers_json` (otherwise keys remain in plaintext at rest).
- `SettingsRepository.exportSettings()` / `importSettings()` must not export/import API keys.
- `SettingsScreen.ProviderEditScreen` must not prefill the current API key; show “Key set” + last4 + replace/clear.

### Pattern 4: Safe-by-default networking logs + safe error surfaces
**What:**
- A single “network logging mode” preference: default off, optional verbose.
- Verbose logs still **must not** include auth headers or request/response bodies containing user audio/text.
- Errors shown to users must be minimal and safe (provider name + status + base URL domain only).

**Repo change points:**
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
- `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`

## Don't Hand-Roll

| Problem | Don’t build | Use instead | Why |
|---|---|---|---|
| URL parsing for disclosure | manual string split | OkHttp `HttpUrl` parsing | Correctly handles scheme/host/port/path; easy to strip query |
| Installed apps enumeration on API 30+ | `QUERY_ALL_PACKAGES` by default | `<queries>` + intent-based visibility + `queryIntentActivities()` for launcher apps | Satisfies package visibility constraints; avoids broad permission |
| Crypto schemes | “custom encryption” formats | Android Keystore + AES/GCM with random IV, or (if accepted) Security Crypto | Crypto is easy to get subtly wrong; stick to standard primitives |

## Common Pitfalls

### Pitfall 1: “Stop immediately” doesn’t stop the HTTP upload
**What goes wrong:** `WhisperTranscriber.stop()` cancels a coroutine job but does not cancel `OkHttpCall.execute()`.

**Why it happens:** `execute()` is blocking; coroutine cancellation doesn’t interrupt the socket.

**How to avoid:** hold onto `Call` (or use `enqueue` + `suspendCancellableCoroutine`) and call `call.cancel()` when stopping due to secure field/per-app block/focus change.

**Repo evidence:** `.planning/codebase/CONCERNS.md` documents this bug; current implementation uses `execute()` in `WhisperTranscriber.startAsync()`.

### Pitfall 2: Provider API keys currently persist in plaintext and are exportable
**What goes wrong:** API keys are stored inside `ServiceProvider.apiKey` and serialized into `PROVIDERS_JSON`; export/import includes API key preference keys.

**Why it happens:** provider model is used as both UI state and persistence payload.

**How to avoid:** store keys in Keystore-backed secret storage keyed by provider ID; ensure provider JSON never contains the key.

**Repo evidence:**
- `ServiceProvider(apiKey: String)` in `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`
- Provider edit screen binds `apiKey = provider.apiKey` in `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Providers are persisted as JSON in `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`

### Pitfall 3: Smart Fix logs and error handling can leak user text
**What goes wrong:** `HttpLoggingInterceptor.Level.BODY` logs JSON bodies (transcript/context/output). Also `Log.e(TAG, "OpenAI error response: $body")` prints full response bodies.

**How to avoid:** never use BODY logging; keep logs to NONE/BASIC/HEADERS with header redaction; never log response bodies.

**Repo evidence:** `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`.

### Pitfall 4: Secure field detection is imperfect across apps
**What goes wrong:** some apps mislabel OTP/PIN fields as plain number/text; some use custom views (inputType=TYPE_NULL); some don’t set meaningful hints.

**How to avoid:** use a strict baseline (password variations + `IME_FLAG_NO_PERSONALIZED_LEARNING`), then add a conservative OTP heuristic and treat false-negatives as acceptable; prioritize preventing false-positives that would break normal typing.

### Pitfall 5: Installed apps list is restricted by Android 11+ package visibility
**What goes wrong:** `getInstalledApplications()` / `getInstalledPackages()` returns a filtered list unless you declare visibility.

**How to avoid:** use intent-based enumeration (launcher apps) and declare `<queries>` for the intents used.

## Code Examples

### Secure field detection (IME)
Source: Android API refs for `EditorInfo` / `InputType`.
```kotlin
fun isSecureField(editorInfo: EditorInfo): Boolean {
    val inputType = editorInfo.inputType
    val klass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    val passwordLike = (klass == InputType.TYPE_CLASS_TEXT &&
        (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
         variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
         variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
        (klass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

    val noPersonalizedLearning =
        (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

    return passwordLike || noPersonalizedLearning || isOtpHeuristic(editorInfo)
}

private fun isOtpHeuristic(editorInfo: EditorInfo): Boolean {
    val hint = editorInfo.hintText?.toString()?.lowercase(Locale.US).orEmpty()
    return hint.contains("otp") || hint.contains("one-time") || hint.contains("verification") ||
        hint.contains("2fa") || hint.contains("code") || hint.contains("passcode") || hint.contains("pin")
}
```

### Package visibility + launcher app enumeration
Source: Android package visibility docs + `<queries>` element docs.

Manifest snippet (allow querying launcher activities):
```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

Enumerate launchable apps:
```kotlin
val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
val activities = context.packageManager.queryIntentActivities(intent, 0)
val apps = activities
    .map { it.activityInfo.applicationInfo }
    .distinctBy { it.packageName }
```

### Redacted network logging (never log bodies)
Source: OkHttp interceptor docs.
```kotlin
class SafeHttpLogger(
    private val enabled: Boolean,
    private val tag: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.newBuilder().query(null).build()

        if (enabled) {
            Log.d(tag, "${request.method} ${url.scheme}://${url.host}${url.encodedPath}")
            request.headers.names().forEach { name ->
                if (name.equals("Authorization", ignoreCase = true)) return@forEach
                if (name.equals("x-goog-api-key", ignoreCase = true)) return@forEach
                Log.d(tag, "$name: ${request.header(name)}")
            }
        }

        return chain.proceed(request)
    }
}
```

### OkHttp cancellation for privacy “stop immediately”
```kotlin
private var inFlightCall: Call? = null

fun start(…){
    val call = client.newCall(request)
    inFlightCall = call
    val response = call.execute() // if kept, must call cancel() to stop
}

fun stop(){
    inFlightCall?.cancel()
    inFlightCall = null
}
```

## State of the Art

| Old approach | Current approach | When changed | Impact |
|---|---|---|---|
| Enumerate all installed apps freely | Package visibility filtering + `<queries>` | Android 11 (API 30) | Installed-apps UI must declare visibility or use intent-based listing |
| Use Jetpack Security Crypto wrappers | Prefer direct platform Keystore APIs | Security Crypto 1.1.0 release notes | New code should expect deprecations; consider Keystore-based wrapper |

**Outdated in this repo (must be fixed for Phase 01):**
- Smart Fix BODY logging (`SmartFixer`) and logging raw error bodies.
- Plaintext API key persistence/export.
- User-facing toasts containing raw provider error response bodies.

## Open Questions

1. **OTP detection strictness**
   - What we know: `InputType` password variations and `IME_FLAG_NO_PERSONALIZED_LEARNING` are reliable signals; OTP fields are inconsistently flagged.
   - What’s unclear: best heuristic to identify OTP/PIN fields without causing false positives.
   - Recommendation: start conservative (numeric-password + strong hint matches), log only non-sensitive editorInfo summaries in debug builds if needed.

2. **Installed apps list completeness**
   - What we know: using launcher intent enumeration avoids `QUERY_ALL_PACKAGES` and works with `<queries>`.
   - What’s unclear: whether the requirement expects listing non-launchable packages.
   - Recommendation: ship launcher-app list + show current target package even if not in list; add an “add by package name” fallback only if required later.

## Suggested Verification (planning-time)

### Automated
```bash
JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./android/gradlew assembleDebug
./android/gradlew lintDebug
./android/gradlew testDebugUnitTest
```

### Manual QA checklist
- Secure fields:
  - In password fields (Chrome login, system settings password, etc): mic disabled with lock; tap shows details sheet; dictation cannot start.
  - In OTP fields (SMS OTP, 2FA code): same behavior.
  - Move focus into secure field while recording/transcribing/smart-fixing: stop immediately; verify no more network traffic.
- Secrets:
  - Provider edit screen never shows full API key; “Key set + last4”; replace/clear works across restarts.
  - Export settings does not include keys.
- Logging:
  - Default: no auth headers, no request/response bodies with user audio/text.
  - Verbose: still redacted; still no bodies.
- Per-app policy:
  - Set blocked for an app; return to that app; mic disabled and tap explains + deep links to that app rule.
- Disclosure:
  - Privacy & Safety screen shows base URL + endpoint paths actually used (dictation vs smart-fix) based on current configuration.
  - First-use disclosure appears once per mode (dictation audio; smart-fix text) and then never again (unless reset).

## Sources

### Primary (HIGH confidence)
- Repo code (source of truth):
  - `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- Android `EditorInfo` docs (IME flag definitions, including `IME_FLAG_NO_PERSONALIZED_LEARNING`):
  - https://developer.android.google.cn/reference/android/view/inputmethod/EditorInfo?hl=en
- Android `InputType` docs (password/number password variations):
  - https://developer.android.google.cn/reference/android/text/InputType?hl=en
- Android package visibility (API 30+):
  - https://developer.android.google.cn/training/package-visibility?hl=en
- Manifest `<queries>` element:
  - https://developer.android.google.cn/guide/topics/manifest/queries-element?hl=en
- Android Keystore overview + examples:
  - https://developer.android.google.cn/privacy-and-security/keystore?hl=en
- OkHttp interceptors overview (custom logging interceptor pattern):
  - https://square.github.io/okhttp/features/interceptors/

### Secondary (MEDIUM confidence)
- Jetpack Security release notes (security-crypto artifact table + deprecation note in 1.1.0 beta/alpha):
  - https://developer.android.google.cn/jetpack/androidx/releases/security?hl=en
- Google Maven metadata for versions:
  - https://dl.google.com/dl/android/maven2/androidx/security/security-crypto/maven-metadata.xml

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH (repo + official docs)
- Architecture: MEDIUM (repo is monolithic; proposed split is a planning convenience)
- Pitfalls: HIGH (directly evidenced in repo + official docs)

**Valid until:** 2026-03-26
