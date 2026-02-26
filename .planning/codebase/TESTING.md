# Testing Patterns

**Analysis Date:** 2026-02-26

## Test Framework

**Runner:**
- Local unit tests: JUnit 4
  - Dependency: `testImplementation("junit:junit:4.13.2")` in `android/app/build.gradle.kts`
- Instrumentation tests: AndroidX JUnit runner
  - Runner: `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `android/app/build.gradle.kts`
  - Dependency: `androidTestImplementation("androidx.test.ext:junit:1.1.5")` in `android/app/build.gradle.kts`

**Assertion Library:**
- JUnit `org.junit.Assert` (static assertions)
  - Example: `assertEquals(...)` in `android/app/src/test/java/com/github/shekohex/whisperpp/ExampleUnitTest.kt`

**Run Commands:**
```bash
./android/gradlew testDebugUnitTest                 # Run local JVM unit tests
./android/gradlew connectedDebugAndroidTest         # Run instrumentation tests on device/emulator
./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.ExampleUnitTest"  # Single class
```

## Test File Organization

**Location:**
- Local JVM tests: `android/app/src/test/java/...`
  - Example: `android/app/src/test/java/com/github/shekohex/whisperpp/ExampleUnitTest.kt`
- Instrumentation tests: `android/app/src/androidTest/java/...`
  - Example: `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ExampleInstrumentedTest.kt`

**Naming:**
- Test classes use `*Test` suffix.
  - Examples: `ExampleUnitTest` in `android/app/src/test/java/com/github/shekohex/whisperpp/ExampleUnitTest.kt`, `ExampleInstrumentedTest` in `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ExampleInstrumentedTest.kt`

**Structure:**
```
android/app/src/
  test/java/com/github/shekohex/whisperpp/
  androidTest/java/com/github/shekohex/whisperpp/
```

## Test Structure

**Suite Organization:**
```kotlin
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
```
Source: `android/app/src/test/java/com/github/shekohex/whisperpp/ExampleUnitTest.kt`

**Patterns:**
- Basic JUnit4 `@Test` functions; no `@Before` / `@After` patterns detected in current tests.
- Instrumentation tests use `@RunWith(AndroidJUnit4::class)`.
  - Example: `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ExampleInstrumentedTest.kt`

## Mocking

**Framework:**
- Not detected (no Mockito/MockK dependencies or usage).

**Patterns:**
- Current tests do not mock; they rely on direct assertions.
  - Examples: `android/app/src/test/java/com/github/shekohex/whisperpp/ExampleUnitTest.kt`, `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ExampleInstrumentedTest.kt`

**What to Mock:**
- Not established in-code. External calls (HTTP/OkHttp/Ktor) and platform services are used in production code.
  - HTTP: `android/app/src/main/java/com/github/shekohex/whisperpp/updater/UpdateChecker.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`

**What NOT to Mock:**
- Not established in-code.

## Fixtures and Factories

**Test Data:**
- No shared fixtures/factories detected.

**Location:**
- Not applicable (no fixture directories present under `android/app/src/test` or `android/app/src/androidTest`).

## Coverage

**Requirements:** None enforced / not configured.

**View Coverage:**
- Not configured (no JaCoCo/Kover config detected in Gradle scripts such as `android/app/build.gradle.kts`).

## Test Types

**Unit Tests:**
- JVM-only tests under `android/app/src/test` using JUnit4.
  - Example: `android/app/src/test/java/com/github/shekohex/whisperpp/ExampleUnitTest.kt`

**Integration Tests:**
- Not detected (no dedicated integration test suites or naming patterns).

**E2E Tests:**
- Not used.

**UI / Compose Tests:**
- Compose UI test dependency is present but no Compose UI tests are detected.
  - Dependency: `androidTestImplementation("androidx.compose.ui:ui-test-junit4")` in `android/app/build.gradle.kts`

## Common Patterns

**Async Testing:**
- Not detected.

**Error Testing:**
- Not detected.

---

*Testing analysis: 2026-02-26*
