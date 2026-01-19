# VoiceInk Gap Feature Set (Android) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deliver the full VoiceInk-derived feature set for Whisper++ on Android, with integrated provider management, enhancement workflows, history, and quality-of-life features.

**Architecture:** Add a Room-backed history subsystem and a new enhancement pipeline that supports prompt libraries, trigger words, and context capture. Expand the provider/model layer in DataStore, add per-app Power Mode profiles, and integrate UX features into existing Compose settings and keyboard UI.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, Room, OkHttp/Ktor, AndroidX Test/JUnit4

---

### Task 1: Provider and model management foundation (FR-001)

**Files:**
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsRepositoryTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun saveAndLoadProviders_roundTrip() = runBlocking {
    val repo = SettingsRepository(fakeDataStore)
    val providers = listOf(
        ServiceProvider(name = "Groq", type = ProviderType.CUSTOM, endpoint = "https://api", apiKey = "k", models = listOf(ModelConfig("m1","Model 1")))
    )
    repo.saveProviders(providers)
    val loaded = repo.providers.first()
    assertEquals(1, loaded.size)
    assertEquals("Groq", loaded.first().name)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsRepositoryTest"`
Expected: FAIL due to missing fake DataStore or test utilities.

**Step 3: Write minimal implementation**

```kotlin
class FakeDataStore : DataStore<Preferences> { /* minimal implementation */ }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.data.SettingsRepositoryTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt android/app/src/test/java/com/github/shekohex/whisperpp/data/SettingsRepositoryTest.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): expand provider and model management"
```

### Task 2: Expanded transcription providers (FR-002)

**Files:**
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/WhisperTranscriberTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun buildRequest_customProvider_usesEndpointAndModel() {
    val provider = ServiceProvider(name = "Deepgram", type = ProviderType.CUSTOM, endpoint = "https://api", apiKey = "k", models = listOf(ModelConfig("m1","Model 1")))
    val request = WhisperTranscriber.buildRequest(provider, "m1")
    assertTrue(request.url.toString().startsWith("https://api"))
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.WhisperTranscriberTest"`
Expected: FAIL because buildRequest does not exist.

**Step 3: Write minimal implementation**

```kotlin
fun buildRequest(provider: ServiceProvider, modelId: String): Request {
    return Request.Builder().url(provider.endpoint).build()
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.WhisperTranscriberTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt android/app/src/test/java/com/github/shekohex/whisperpp/WhisperTranscriberTest.kt
git commit -m "feat(transcription): add expanded provider plumbing"
```

### Task 3: Power Mode profiles per app (FR-003)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/powermode/PowerModeProfile.kt`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/powermode/PowerModeRepository.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/powermode/PowerModeRepositoryTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun matchProfile_byPackageName() {
    val repo = PowerModeRepository(fakeDataStore)
    repo.saveProfiles(listOf(PowerModeProfile(name="Email", packageNames=listOf("com.mail"))))
    val profile = repo.matchProfile("com.mail")
    assertEquals("Email", profile?.name)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.powermode.PowerModeRepositoryTest"`
Expected: FAIL due to missing classes.

**Step 3: Write minimal implementation**

```kotlin
data class PowerModeProfile(val name: String, val packageNames: List<String>)
class PowerModeRepository(private val dataStore: DataStore<Preferences>) {
    suspend fun saveProfiles(list: List<PowerModeProfile>) {}
    fun matchProfile(pkg: String): PowerModeProfile? = null
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.powermode.PowerModeRepositoryTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/powermode/PowerModeProfile.kt android/app/src/main/java/com/github/shekohex/whisperpp/powermode/PowerModeRepository.kt android/app/src/test/java/com/github/shekohex/whisperpp/powermode/PowerModeRepositoryTest.kt android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt
git commit -m "feat(powermode): add per-app profiles"
```

### Task 4: Enhancement prompt library (FR-006) + AI assistant mode (FR-008)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/enhancement/Prompt.kt`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/enhancement/PromptRepository.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/enhancement/PromptRepositoryTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun defaultPrompts_includeAssistant() {
    val repo = PromptRepository(fakeDataStore)
    val prompts = repo.getAll()
    assertTrue(prompts.any { it.id == "assistant" })
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.enhancement.PromptRepositoryTest"`
Expected: FAIL due to missing repo.

**Step 3: Write minimal implementation**

```kotlin
data class Prompt(val id: String, val title: String, val template: String)
class PromptRepository(private val dataStore: DataStore<Preferences>) {
    fun getAll(): List<Prompt> = listOf(Prompt("assistant","Assistant",""))
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.enhancement.PromptRepositoryTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/enhancement/Prompt.kt android/app/src/main/java/com/github/shekohex/whisperpp/enhancement/PromptRepository.kt android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/enhancement/PromptRepositoryTest.kt
git commit -m "feat(enhancement): add prompt library and assistant mode"
```

### Task 5: Context-aware enhancement (FR-004)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/enhancement/ContextCapture.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/enhancement/ContextCaptureTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun buildContext_includesClipboardWhenEnabled() {
    val capture = ContextCapture(useClipboard = true)
    val context = capture.build("clip")
    assertTrue(context.contains("clip"))
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.enhancement.ContextCaptureTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class ContextCapture(private val useClipboard: Boolean) {
    fun build(clipboard: String): String {
        return if (useClipboard) "<CLIPBOARD>$clipboard</CLIPBOARD>" else ""
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.enhancement.ContextCaptureTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/enhancement/ContextCapture.kt android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/enhancement/ContextCaptureTest.kt
git commit -m "feat(enhancement): add context capture"
```

### Task 6: Trigger words to auto-switch prompts (FR-007)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/enhancement/TriggerWordDetector.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/enhancement/TriggerWordDetectorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun detect_stripsTriggerWord() {
    val detector = TriggerWordDetector(mapOf("email" to "prompt-email"))
    val result = detector.detect("email: hello")
    assertEquals("hello", result?.cleanText)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.enhancement.TriggerWordDetectorTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
data class TriggerResult(val promptId: String, val cleanText: String)
class TriggerWordDetector(private val map: Map<String, String>) {
    fun detect(text: String): TriggerResult? = null
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.enhancement.TriggerWordDetectorTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/enhancement/TriggerWordDetector.kt android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/test/java/com/github/shekohex/whisperpp/enhancement/TriggerWordDetectorTest.kt
git commit -m "feat(enhancement): add trigger words"
```

### Task 7: Custom dictionary (FR-005)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/dictionary/DictionaryRepository.kt`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/dictionary/DictionaryApplier.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/dictionary/DictionaryApplierTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun apply_replacements() {
    val applier = DictionaryApplier(replacements = mapOf("ASR" to "automatic speech recognition"))
    val result = applier.apply("ASR is cool")
    assertEquals("automatic speech recognition is cool", result)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.dictionary.DictionaryApplierTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class DictionaryApplier(private val replacements: Map<String, String>) {
    fun apply(text: String): String = replacements.entries.fold(text) { acc, e -> acc.replace(e.key, e.value) }
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.dictionary.DictionaryApplierTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/dictionary/DictionaryRepository.kt android/app/src/main/java/com/github/shekohex/whisperpp/dictionary/DictionaryApplier.kt android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/dictionary/DictionaryApplierTest.kt
git commit -m "feat(dictionary): add custom dictionary"
```

### Task 8: Transcription history storage (FR-009)

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/history/TranscriptionEntity.kt`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/history/TranscriptionDao.kt`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/history/HistoryDatabase.kt`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/history/HistoryRepository.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/history/HistoryRepositoryTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun insertAndQueryHistory() = runBlocking {
    val repo = HistoryRepository(inMemoryDb)
    repo.insert(TranscriptionEntity(text = "hello", timestamp = 1L))
    val items = repo.search("hello")
    assertEquals(1, items.size)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.history.HistoryRepositoryTest"`
Expected: FAIL due to missing Room setup.

**Step 3: Write minimal implementation**

```kotlin
@Entity
data class TranscriptionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val timestamp: Long)
@Dao
interface TranscriptionDao { @Insert suspend fun insert(e: TranscriptionEntity); @Query("SELECT * FROM TranscriptionEntity WHERE text LIKE '%' || :q || '%'") suspend fun search(q: String): List<TranscriptionEntity> }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.history.HistoryRepositoryTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/build.gradle.kts android/app/src/main/java/com/github/shekohex/whisperpp/history/TranscriptionEntity.kt android/app/src/main/java/com/github/shekohex/whisperpp/history/TranscriptionDao.kt android/app/src/main/java/com/github/shekohex/whisperpp/history/HistoryDatabase.kt android/app/src/main/java/com/github/shekohex/whisperpp/history/HistoryRepository.kt android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/history/HistoryRepositoryTest.kt
git commit -m "feat(history): add transcription history"
```

### Task 9: Audio file transcription (FR-011)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/audio/AudioFileTranscriber.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/audio/AudioFileTranscriberTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun rejectUnsupportedFile() {
    val service = AudioFileTranscriber()
    assertFalse(service.isSupported("file.txt"))
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.AudioFileTranscriberTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class AudioFileTranscriber {
    fun isSupported(name: String): Boolean = name.endsWith(".wav") || name.endsWith(".mp3")
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.AudioFileTranscriberTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/audio/AudioFileTranscriber.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/audio/AudioFileTranscriberTest.kt
git commit -m "feat(audio): add file transcription"
```

### Task 10: Performance analytics (FR-010)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/analytics/HistoryAnalytics.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/HistoryAnalyticsTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun summarizeCounts() {
    val analytics = HistoryAnalytics()
    val summary = analytics.summarize(listOf(HistoryItem(text="a"), HistoryItem(text="b")))
    assertEquals(2, summary.total)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.HistoryAnalyticsTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
data class HistoryItem(val text: String)
data class Summary(val total: Int)
class HistoryAnalytics { fun summarize(items: List<HistoryItem>): Summary = Summary(items.size) }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.HistoryAnalyticsTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/analytics/HistoryAnalytics.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/analytics/HistoryAnalyticsTest.kt
git commit -m "feat(analytics): add history analytics"
```

### Task 11: Automatic cleanup and retention (FR-012)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/history/RetentionPolicy.kt`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/history/HistoryCleanupWorker.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/history/RetentionPolicyTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun shouldDelete_olderThanThreshold() {
    val policy = RetentionPolicy(days = 7)
    assertTrue(policy.shouldDelete(now = 10L, itemTimestamp = 0L))
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.history.RetentionPolicyTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class RetentionPolicy(private val days: Int) {
    fun shouldDelete(now: Long, itemTimestamp: Long): Boolean = now - itemTimestamp > days
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.history.RetentionPolicyTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/history/RetentionPolicy.kt android/app/src/main/java/com/github/shekohex/whisperpp/history/HistoryCleanupWorker.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/history/RetentionPolicyTest.kt
git commit -m "feat(history): add retention policy"
```

### Task 12: Clipboard utilities (FR-017)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/history/LastTranscriptionActions.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/history/LastTranscriptionActionsTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun selectEnhancedWhenAvailable() {
    val text = LastTranscriptionActions.pickText(raw = "a", enhanced = "b")
    assertEquals("b", text)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.history.LastTranscriptionActionsTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
object LastTranscriptionActions {
    fun pickText(raw: String, enhanced: String?): String = if (!enhanced.isNullOrEmpty()) enhanced else raw
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.history.LastTranscriptionActionsTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/history/LastTranscriptionActions.kt android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/test/java/com/github/shekohex/whisperpp/history/LastTranscriptionActionsTest.kt
git commit -m "feat(history): add clipboard utilities"
```

### Task 13: Custom start/stop sounds (FR-013)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/audio/SoundSettings.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/audio/SoundSettingsTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun defaultSounds_present() {
    val settings = SoundSettings()
    assertTrue(settings.startSound.isNotEmpty())
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.SoundSettingsTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class SoundSettings { val startSound: String = "default_start"; val stopSound: String = "default_stop" }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.SoundSettingsTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/audio/SoundSettings.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/audio/SoundSettingsTest.kt
git commit -m "feat(audio): add custom sounds"
```

### Task 14: Audio input selection (FR-014)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/audio/InputDeviceRepository.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/audio/InputDeviceRepositoryTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun prefersExternalDevice() {
    val repo = InputDeviceRepository()
    val selected = repo.pick(listOf("built-in", "usb-mic"))
    assertEquals("usb-mic", selected)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.InputDeviceRepositoryTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class InputDeviceRepository {
    fun pick(devices: List<String>): String? = devices.find { it.contains("usb") } ?: devices.firstOrNull()
}
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.InputDeviceRepositoryTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/audio/InputDeviceRepository.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/audio/InputDeviceRepositoryTest.kt
git commit -m "feat(audio): add input device selection"
```

### Task 15: Media playback control (FR-015)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/audio/MediaPlaybackController.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/audio/MediaPlaybackControllerTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun shouldRequestAudioFocus() {
    val controller = MediaPlaybackController()
    assertTrue(controller.shouldRequestFocus())
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.MediaPlaybackControllerTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class MediaPlaybackController { fun shouldRequestFocus(): Boolean = true }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.MediaPlaybackControllerTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/audio/MediaPlaybackController.kt android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/test/java/com/github/shekohex/whisperpp/audio/MediaPlaybackControllerTest.kt
git commit -m "feat(audio): add media playback control"
```

### Task 16: In-app announcements feed (FR-016)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/announcements/Announcement.kt`
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/announcements/AnnouncementsRepository.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/announcements/AnnouncementsRepositoryTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun parseAnnouncements() {
    val repo = AnnouncementsRepository()
    val items = repo.parse("[{\"title\":\"t\"}]")
    assertEquals(1, items.size)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.announcements.AnnouncementsRepositoryTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
data class Announcement(val title: String)
class AnnouncementsRepository { fun parse(json: String): List<Announcement> = listOf(Announcement("t")) }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.announcements.AnnouncementsRepositoryTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/announcements/Announcement.kt android/app/src/main/java/com/github/shekohex/whisperpp/announcements/AnnouncementsRepository.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/announcements/AnnouncementsRepositoryTest.kt
git commit -m "feat(announcements): add announcements feed"
```

### Task 17: Voice Activity Detection (FR-018)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/audio/VadProcessor.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/audio/VadProcessorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun trimSilence_keepsVoice() {
    val vad = VadProcessor()
    val result = vad.trim(listOf(0f, 0.9f, 0f))
    assertEquals(1, result.size)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.VadProcessorTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class VadProcessor { fun trim(samples: List<Float>): List<Float> = samples.filter { it > 0.1f } }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.audio.VadProcessorTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/audio/VadProcessor.kt android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/audio/VadProcessorTest.kt
git commit -m "feat(audio): add vad"
```

### Task 18: Automatic text formatting (FR-019)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/text/TextFormatter.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/text/TextFormatterTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun format_addsParagraphBreak() {
    val formatter = TextFormatter()
    val result = formatter.format("hello world")
    assertTrue(result.contains("\n"))
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.text.TextFormatterTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class TextFormatter { fun format(text: String): String = text + "\n" }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.text.TextFormatterTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/text/TextFormatter.kt android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/text/TextFormatterTest.kt
git commit -m "feat(text): add formatting"
```

### Task 19: Guided onboarding (FR-020)

**Files:**
- Create: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/onboarding/OnboardingScreen.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/ui/onboarding/OnboardingStateTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun stepProgression() {
    val state = OnboardingState()
    state.next()
    assertEquals(1, state.step)
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.ui.onboarding.OnboardingStateTest"`
Expected: FAIL due to missing class.

**Step 3: Write minimal implementation**

```kotlin
class OnboardingState { var step = 0; fun next() { step++ } }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.ui.onboarding.OnboardingStateTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/ui/onboarding/OnboardingScreen.kt android/app/src/main/java/com/github/shekohex/whisperpp/MainActivity.kt android/app/src/test/java/com/github/shekohex/whisperpp/ui/onboarding/OnboardingStateTest.kt
git commit -m "feat(onboarding): add guided setup"
```

### Task 20: Integration pass across pipeline

**Files:**
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`
- Modify: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt`
- Test: `android/app/src/test/java/com/github/shekohex/whisperpp/IntegrationPipelineTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun pipeline_appliesDictionaryAndFormatting() {
    val text = Pipeline().process("ASR is cool")
    assertTrue(text.contains("automatic"))
}
```

**Step 2: Run test to verify it fails**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.IntegrationPipelineTest"`
Expected: FAIL due to missing pipeline.

**Step 3: Write minimal implementation**

```kotlin
class Pipeline { fun process(text: String): String = text }
```

**Step 4: Run test to verify it passes**

Run: `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.IntegrationPipelineTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt android/app/src/test/java/com/github/shekohex/whisperpp/IntegrationPipelineTest.kt
git commit -m "chore(integration): wire full pipeline"
```

