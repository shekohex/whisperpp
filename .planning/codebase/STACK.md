# Technology Stack

**Analysis Date:** 2026-02-26

## Languages

**Primary:**
- Kotlin (Android) - App source in `android/app/src/main/java/**` (e.g., `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`)

**Secondary:**
- Gradle Kotlin DSL - Build logic in `android/build.gradle.kts`, `android/app/build.gradle.kts`, `android/settings.gradle.kts`
- Ruby - Release automation via Fastlane in `fastlane/Fastfile` (Bundler deps in `Gemfile`, `Gemfile.lock`)
- YAML - CI workflow in `.github/workflows/android-ci.yml`

## Runtime

**Environment:**
- Android runtime (IME app/service) - manifest and components in `android/app/src/main/AndroidManifest.xml`
- Java 17 toolchain - configured in `android/app/build.gradle.kts` (`compileOptions` + `kotlinOptions.jvmTarget = "17"`)

**Package Manager:**
- Gradle (wrapper)
  - Wrapper config: `android/gradle/wrapper/gradle-wrapper.properties` (Gradle 8.5)
  - Lockfile: missing (no Gradle dependency lockfile detected)
- Bundler (Ruby) for Fastlane
  - Lockfile: present (`Gemfile.lock`)

## Frameworks

**Core:**
- Android SDK (compile/target 34, min 24) - `android/app/build.gradle.kts`
- AndroidX + Jetpack Compose (UI) - Compose enabled in `android/app/build.gradle.kts` (`buildFeatures.compose = true`)
- Android Input Method Framework - `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` (extends `InputMethodService`)

**Testing:**
- JUnit4 - `android/app/build.gradle.kts` (`testImplementation("junit:junit:4.13.2")`)
- AndroidX Test Runner / Espresso / Compose UI test - `android/app/build.gradle.kts`

**Build/Dev:**
- Android Gradle Plugin 8.1.2 - `android/build.gradle.kts`
- Kotlin Android plugin 1.9.0 - `android/build.gradle.kts`
- Compose compiler extension 1.5.1 - `android/app/build.gradle.kts` (`composeOptions.kotlinCompilerExtensionVersion`)

## Key Dependencies

**Critical:**
- `io.ktor:ktor-client-okhttp:2.3.6` - HTTP client engine used for update checks in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateManager.kt` and `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`
- OkHttp (`com.squareup.okhttp3:logging-interceptor:4.12.0`) - direct HTTP calls + request logging/redaction in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` and `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- `androidx.datastore:datastore-preferences:1.0.0` - settings persistence in `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` and `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- `androidx.work:work-runtime-ktx:2.9.0` - periodic update checks in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCheckWorker.kt`

**Infrastructure:**
- Gson (`com.google.code.gson:gson:2.10.1`) - JSON (de)serialization for providers + updates in `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` and `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`
- Quick Transfer Core (`com.github.liuyueyi:quick-transfer-core:0.2.13`) - Chinese conversion utilities used in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt` and preloaded in `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Compose Markdown (`com.github.jeziellago:compose-markdown:0.5.4`) - renders changelog markdown in `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateCard.kt`

## Configuration

**Environment:**
- User/configurable provider settings persisted via DataStore keys in `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt` and repository in `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Build-time constants for update system:
  - `BuildConfig.GITHUB_REPO` and `BuildConfig.UPDATE_CHANNEL` - defined via `buildConfigField` in `android/app/build.gradle.kts`
  - Values sourced from Gradle project properties `githubRepo` and `updateChannel` in `android/app/build.gradle.kts` (also overridden in CI via Fastlane in `fastlane/Fastfile`)
- Signing configuration via Gradle project properties in `android/app/build.gradle.kts` (`signingStoreFile`, `signingStorePassword`, `signingKeyAlias`, `signingKeyPassword`)

**Build:**
- Root/build config: `android/build.gradle.kts`, `android/settings.gradle.kts`, `android/gradle.properties`
- Module config: `android/app/build.gradle.kts`
- CI build/release pipeline: `.github/workflows/android-ci.yml` + `fastlane/Fastfile`
- Local Android SDK path (should be local-only): `android/local.properties`

## Platform Requirements

**Development:**
- Android SDK/Gradle build via wrapper `android/gradlew` (Gradle 8.5 in `android/gradle/wrapper/gradle-wrapper.properties`)
- JDK 17 (toolchain configured in `android/app/build.gradle.kts`; CI also installs JDK 17 in `.github/workflows/android-ci.yml`)

**Production:**
- Release APKs are produced per-ABI + universal via splits in `android/app/build.gradle.kts` (`splits.abi`)
- Distribution target: GitHub Releases (automated in `fastlane/Fastfile` and triggered by `.github/workflows/android-ci.yml`)

---

*Stack analysis: 2026-02-26*
