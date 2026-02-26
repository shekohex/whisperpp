# Stack Research

**Domain:** Android IME (keyboard) with STT + LLM rewrite (BYO providers)
**Researched:** 2026-02-26
**Confidence:** MEDIUM

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.2.21 | Language/tooling baseline | Stable late-2025 Kotlin; aligns with modern AndroidX + KSP + kotlinx libs. |
| Jetpack Compose | BOM 2025.12.01 | UI for settings + keyboard chrome | Compose is the standard for new UI; BOM pins a coherent set of Compose artifacts. |
| Compose compiler integration | Use Compose Compiler Gradle plugin (Kotlin 2.0+) | Compose compiler wiring | With Kotlin 2.0+, avoids manual “Compose↔Kotlin compatibility” chasing. |
| Kotlin Coroutines | 1.10.0 | Concurrency for audio/STT/streaming/IME updates | Structured concurrency + Flow/Channel are the de-facto Android async stack. |
| OkHttp | 5.3.2 | HTTP(S) client + streaming (SSE / chunked) | Mature on Android, easiest path to implement provider streaming + retries + interceptors. |
| Kotlinx Serialization | 1.9.0 | JSON encoding/decoding + typed models | First-party Kotlin; good fit for multiple provider schemas + strict parsing. |
| AndroidX DataStore | 1.2.0 | Preferences + structured config persistence | Modern replacement for SharedPreferences; transactional + coroutine-first. |
| AndroidX Room | 2.8.4 | Persist prompt profiles / history / telemetry (optional) | Standard SQLite layer; use when profiles/history outgrow DataStore. |
| AndroidX Lifecycle | 2.10.0 | Lifecycle scopes/state for UI + services | Standard lifecycle plumbing; integrates with Compose + coroutines. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AndroidX Activity (+ activity-compose) | 1.12.0 | Compose hosting + activity result APIs | Settings UI and any activity-based flows (permissions, onboarding). |
| AndroidX Core (core-ktx) | 1.16.0 | Kotlin-friendly platform helpers | Everywhere; prefer KTX for clarity/safety. |
| AndroidX Security Crypto | 1.1.0 | Keystore-backed encryption helpers | If you want fast “encrypt-at-rest” for API keys; note deprecations below. |
| Google KSP | 2.2.21-2.0.4 | Annotation processing for Room (and others) | Use instead of KAPT for Room; faster and Kotlin-first. |
| OkHttp Logging Interceptor | 5.3.2 | Debug HTTP logging | Debug builds only; redact secrets. |
| WorkManager | 2.11.0 | Deferrable background work (optional) | Upload crash reports, periodic cleanup, or queued provider calls outside IME session. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio (2025.x+) | IDE + Gradle integration | Use bundled JDK 17 for builds; keep AGP aligned with Studio. |
| JDK | 17 | Required for modern AGP | Keep consistent across local/CI. |
| Gradle Version Catalogs | Centralize versions | Put the table above into `libs.versions.toml` to avoid drift. |

## Installation

```kotlin
// build.gradle.kts (module)
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.0")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("androidx.datastore:datastore:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
}

// build.gradle.kts (plugins)
plugins {
    kotlin("android") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("com.google.devtools.ksp") version "2.2.21-2.0.4"
}
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| OkHttp 5.x | Ktor Client 3.2.x/3.3.x | If you already standardize on Ktor across platforms or want built-in client plugins (auth/retry/SSE) and accept extra surface area. |
| Kotlinx Serialization | Moshi | If you need extremely lenient parsing of inconsistent provider payloads or you already have Moshi adapters. |
| DataStore + (optional) Room | DataStore only | If per-app profiles stay small and you don’t need queries/history. |
| Compose UI | Views/XML | If you must support very old UI flows or rely on legacy view libraries; otherwise Compose is default. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| KAPT (for Room/new code) | Slower builds; worse with Kotlin-heavy codebases | KSP (2.2.21-2.0.4) |
| Retrofit for streaming token/partial endpoints | Retrofit is great for request/response, but streaming often devolves into manual `ResponseBody` parsing | OkHttp streaming (`BufferedSource`) or Ktor streaming APIs |
| Storing API keys unencrypted in DataStore/Prefs | IME has broad reach; credentials are high-value | Keystore-backed encryption (prefer platform APIs; short-term Security Crypto if needed) |
| Security Crypto as a long-term plan | `androidx.security:security-crypto` APIs are marked deprecated in favor of platform APIs | Direct Android Keystore + your own envelope encryption (or Tink directly if you need cross-platform key formats) |

## Stack Patterns by Variant

**If provider supports streaming partials (STT or LLM):**
- Use OkHttp streaming (SSE or chunked JSON lines) + coroutines `Flow`.
- Because IME composing-text updates must be low-latency and cancellation-friendly.

**If provider is OpenAI-compatible (base URL + key):**
- Use a single “OpenAI-compatible” client implementation with provider capability flags.
- Because it reduces per-provider branching while still allowing opt-in features (streaming, tool calls, etc.).

**If provider only supports non-streaming:**
- Use simple request/response + optimistic UI (show “listening/thinking” state; insert only final text).
- Because composing partials without server support leads to poor UX (jitter/undo/replace complexity).

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Kotlin 2.2.21 | kotlinx-serialization 1.9.0 | Serialization 1.9.0 targets Kotlin 2.2.0. |
| Kotlin 2.2.21 | KSP 2.2.21-2.0.4 | Match KSP to Kotlin version for KSP1-era releases. |
| Compose | Kotlin 2.0+ | Use the Compose Compiler Gradle plugin; avoids manual composeOptions pinning. |
| OkHttp 5.3.2 | Ktor OkHttp engine (if used) | Ktor 3.3.0+ notes mention OkHttp 5 usage; keep versions aligned if mixing. |

## Sources

- Kotlin release history (Kotlin 2.2.21, 2025-10-23) — https://kotlinlang.org/docs/releases.html
- Compose BOM mapping (BOM list includes 2025.12.01) — https://developer.android.com/jetpack/compose/bom/bom-mapping
- Compose↔Kotlin compatibility guidance (Kotlin 2.0+ uses Compose Compiler Gradle plugin) — https://developer.android.com/jetpack/androidx/releases/compose-kotlin
- OkHttp tags (OkHttp 5.3.2, 2025-11-18) — https://github.com/square/okhttp/tags
- DataStore release notes (1.2.0 stable) — https://developer.android.com/jetpack/androidx/releases/datastore
- Room release notes (2.8.4 stable) — https://developer.android.com/jetpack/androidx/releases/room
- Lifecycle release notes (2.10.0 stable) — https://developer.android.com/jetpack/androidx/releases/lifecycle
- Security release notes (security-crypto 1.1.0 + deprecation notes) — https://developer.android.com/jetpack/androidx/releases/security
- KSP releases (2.2.21-2.0.4) — https://github.com/google/ksp/releases
- kotlinx.coroutines releases (1.10.0) — https://github.com/Kotlin/kotlinx.coroutines/releases
- kotlinx.serialization releases (1.9.0) — https://github.com/Kotlin/kotlinx.serialization/releases

---
*Stack research for: Android IME speech-to-text keyboard + LLM rewrite*
*Researched: 2026-02-26*
