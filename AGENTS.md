# AGENTS.md

## Scope
- This repo is an Android/Kotlin app under `android/`.
- Commands assume repo root unless stated.
- Use Gradle wrapper in `android/gradlew`.
- Module: `:app`.

## Build
- Debug APK: `JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./android/gradlew assembleDebug`
- Release APK (unsigned): `JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./android/gradlew assembleRelease`
- Clean: `JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./android/gradlew clean`
- Build all variants: `JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./android/gradlew assemble`
- List tasks: `JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./android/gradlew tasks`

## Lint
- Android lint: `./android/gradlew lintDebug`
- All variants: `./android/gradlew lint`
- No ktlint/detekt/prettier configured.

## Unit Tests
- All unit tests: `./android/gradlew testDebugUnitTest`
- Single class: `./android/gradlew testDebugUnitTest --tests "com.example.YourTest"`
- Single test: `./android/gradlew testDebugUnitTest --tests "com.example.YourTest#testName"`
- Use JUnit4 (`junit:junit:4.13.2`).

## Instrumentation Tests
- Run on device/emulator: `./android/gradlew connectedDebugAndroidTest`
- Single class: `./android/gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.YourTest`
- Single method: `./android/gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.YourTest#testName`
- Runner: `androidx.test.runner.AndroidJUnitRunner`.

## Releasing
When the user asks for a new release, follow this protocol:

1. **Clarify Requirements** (if not provided):
   - What is the target `versionName` (e.g., "1.0.0")?
   - Should `versionCode` be incremented automatically (default)?

2. **Update Configuration**:
   - Edit `android/app/build.gradle.kts`.
   - Update `defaultConfig.versionCode` and `defaultConfig.versionName`.

3. **Commit Changes**:
   - `git add android/app/build.gradle.kts`
   - `git commit -m "chore: bump version to <versionName>"`
   - `git push origin main`

4. **Trigger CI/CD**:
   - Create annotated tag: `git tag -a v<versionName> -m "Release v<versionName>"`
   - Push tag: `git push origin v<versionName>`
   - This triggers the `release` workflow in GitHub Actions.

## Project Structure
- Main code: `android/app/src/main`.
- Tests: `android/app/src/test`.
- Instrumented tests: `android/app/src/androidTest`.
- Manifest: `android/app/src/main/AndroidManifest.xml`.
- Resources: `android/app/src/main/res`.
- Assets: `android/app/src/main/assets`.

## Code Style Summary
- Kotlin style: official (`kotlin.code.style=official`).
- Java/Kotlin target: 1.8.
- Android SDK: `compileSdk 34`, `minSdk 24`, `targetSdk 34`.
- Follow existing patterns in `android/app/src/...`.

## Formatting
- Use Android Studio default formatter for Kotlin official style.
- Indentation: 4 spaces, no tabs.
- Keep lines readable; prefer small functions over long lines.
- Avoid trailing whitespace.
- One top-level class per file.

## Imports
- Use explicit imports; avoid wildcards unless required by Android Studio.
- Group imports by standard Kotlin/Java, Android/AndroidX, third-party, local.
- Keep imports ordered and remove unused imports.

## Naming
- Packages: lowercase.
- Classes/objects/interfaces: PascalCase.
- Functions/variables: camelCase.
- Constants: UPPER_SNAKE_CASE.
- Resource IDs: lower_snake_case.
- Test classes: `*Test` suffix.

## Types
- Prefer `val` over `var`.
- Use nullability explicitly; avoid platform types in Kotlin.
- Expose immutable collections; return `List`/`Map` over mutable types.
- Avoid `Any` in public APIs.
- Use data classes for simple state holders.

## Functions
- Keep functions single-purpose.
- Small, composable helpers over large methods.
- Limit side effects; return values instead of mutating shared state.
- Prefer `when` for branching on sealed types.

## Error Handling
- Validate inputs at boundaries (UI, IPC, network, file).
- Wrap I/O and network calls in `try/catch`.
- Surface errors to UI where relevant; avoid silent failures.
- Use structured logging (Android `Log`) with tags when needed.
- Avoid swallowing exceptions; rethrow or convert to result.

## Concurrency
- Avoid blocking the main thread.
- For background work, use appropriate Android APIs consistent with existing code.
- Keep synchronization minimal and localized.

## Resources
- Keep strings in `res/values/strings.xml`.
- Use `@StringRes` and `@DrawableRes` annotations when appropriate.
- Avoid hardcoding UI text in code.

## Networking
- Use `ktor-client-okhttp` for HTTP calls.
- Handle non-2xx responses explicitly.
- Set timeouts and retries where appropriate.
- Do not do network I/O on the main thread.

## Settings/Storage
- Preferences use `androidx.datastore`.
- Keep keys centralized and type-safe.

## Dependencies
- Add new dependencies in `android/app/build.gradle.kts`.
- Prefer stable versions; minimize dependency count.
- Keep dependency scope minimal (`implementation` vs `testImplementation`).

## Security
- Do not log sensitive data (API keys, tokens, user audio).
- Validate URLs and file paths before use.
- Use HTTPS endpoints only.

## Performance
- Avoid unnecessary allocations in hot paths.
- Reuse objects where appropriate.
- Prefer lazy initialization when cost is high.

## Tests
- Prefer deterministic tests; avoid time-based flakiness.
- Keep tests fast; mock external dependencies.
- Use clear arrange/act/assert structure.

## Versioning
- Increment `versionCode` for every release.
- Keep `versionName` user-facing and stable.

## Git Hygiene
- Keep changes minimal and focused.
- No autogenerated files committed unless required.

## PTY Usage
- Use PTY for long-running Gradle tasks to avoid timeouts.
- Spawn with `JAVA_HOME` set to JDK 17.
- Read output via `pty_read` and look for `BUILD SUCCESSFUL`.
- Kill and cleanup PTY sessions when done.

## Cursor/Copilot Rules
- No `.cursor/rules`, `.cursorrules`, or `.github/copilot-instructions.md` found.

## Notes
- Use JDK 17 for CLI Gradle; JDK 25 fails parsing version `25.0.1`.
- Example: `JAVA_HOME="/Users/shady/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home" ./android/gradlew tasks`.
- If Gradle tasks fail, run `./android/gradlew --stacktrace` for details.
- If tasks are missing, check `android/build.gradle.kts` and `android/app/build.gradle.kts`.
- For tooling setup, use Android Studio with default settings.
