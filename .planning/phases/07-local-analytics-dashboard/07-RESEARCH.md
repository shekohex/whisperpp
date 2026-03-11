# Phase 7: local-analytics-dashboard - Research

**Researched:** 2026-03-09
**Domain:** Android/Kotlin local analytics dashboard, local persistence, privacy-safe backup boundaries
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Dashboard placement and structure
- Keep the existing setup banner above analytics whenever setup is broken.
- Analytics becomes the first normal section on settings home, above the rest of the grouped settings areas.
- Settings home should show a two-row mini dashboard card rather than a plain status row.
- The home card should lead with time saved, include a small visual preview, and feel like a mini dashboard.
- Tapping analytics from home should open a dedicated analytics screen.
- The dedicated analytics screen should use a visual treatment similar to Android Digital Wellbeing, with real trend visuals rather than text-only rows.

### Metrics and reporting scope
- Show lifetime totals since last reset together with a separate last-7-days trend view.
- The 7-day trend should visualize time saved.
- Track successful sent sessions and cancelled sessions separately.
- Keep sent/cancelled breakdowns on the dedicated analytics screen rather than the home card.
- Track both raw transcript words and final inserted words, but keep that richer breakdown on the dedicated analytics screen.
- Words per minute should be based on final inserted words.
- Keystrokes saved should be framed as an estimate based on inserted characters.

### Time-saved framing
- Time saved is the primary hero metric on both the home card and the analytics screen.
- Frame the value as time saved compared to typing.
- Keep the estimate caveat soft rather than making "estimated" the dominant headline wording.
- Format saved time in human-readable units (minutes, then hours/minutes as totals grow).
- Use a motivational tone rather than a purely neutral or strictly utilitarian presentation.

### Reset and empty-state behavior
- Reset should wipe all analytics data, including totals, breakdowns, and trend history.
- Reset should use a destructive confirmation dialog.
- After reset, remain on the analytics screen and immediately show the zeroed dashboard plus empty scaffold.
- If there is not enough history yet, keep the trend area visible with an empty scaffold rather than hiding it.
- Empty state messaging should emphasize the product benefit of using Whisper++ rather than making privacy the main message.

### OpenCode's Discretion
- Exact chart component choice and Material 3 visual details, as long as the screen stays close to the requested Digital Wellbeing-style feel.
- Exact copy for the soft estimate caveat while preserving the motivational tone and typing comparison.
- Exact choice of which 2-3 supporting metrics appear on the home mini dashboard, as long as the richer breakdowns stay on the dedicated screen.

### Deferred Ideas (OUT OF SCOPE)
None - discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| STATS-01 | Whisper++ tracks local-only usage stats (dictation minutes, sessions, words dictated, words/min, keystrokes saved) | Dedicated analytics repository, terminal-outcome event capture, BreakIterator-based word counting, derived WPM/time-saved formatting |
| STATS-02 | Settings home includes an analytics dashboard including an estimated "time saved" summary | Settings home first-section mini dashboard, dedicated analytics route, fixed 7-day visual, hero metric/time-saved formatter |
| PRIV-06 | Usage analytics are stored locally only, can be reset, and are not transmitted by default | Separate analytics storage boundary, backup/D2D exclusion requirement, destructive reset flow, no export/import or network path |
</phase_requirements>

## Summary

Phase 7 fits the repo’s existing architecture: local persistence via DataStore-backed repositories, Compose Material 3 settings screens, and settings navigation via `SettingsScreen` + `NavHost`. The important planning constraint is privacy, not just UI. The app currently has `android:allowBackup="true"`, empty backup/data-extraction rule files, and a shared settings DataStore. Android’s backup docs say app data is backed up by default and backup rules work at file/folder granularity, not per-key granularity. That means analytics should not live inside the existing shared settings store if PRIV-06 is meant literally.

Plan around a dedicated local analytics store, explicit reset behavior, and terminal-outcome event capture in `WhisperInputService` rather than counting during partials. Record exactly once per completed dictation outcome: successful sent session with final inserted text known, or cancelled session. Keep lifetime totals plus 7 daily buckets; derive time saved, WPM, and dashboard strings from raw counts at read time rather than persisting display text.

UI-wise, reuse the current grouped settings pattern: analytics becomes the first normal home section, then a dedicated nested analytics screen under settings navigation, plus route-aware help content. Avoid adding a large chart dependency unless compatibility is validated early; the repo’s Compose/Kotlin stack is older than current chart-library examples, and the requirement is only a fixed 7-day visual.

**Primary recommendation:** Use a dedicated local-only analytics store with explicit backup/D2D exclusion, finalize metrics only on terminal dictation outcomes, and build a fixed 7-day Compose dashboard on top of existing settings/navigation patterns.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.datastore:datastore-preferences` | 1.0.0 in repo | Persist small local analytics snapshots | Repo already uses DataStore; Android docs recommend DataStore for small async transactional data |
| `androidx.compose:compose-bom` + Material 3 | 2024.02.00 in repo | Home card + dedicated dashboard UI | Existing settings UI is already Compose Material 3 grouped cards |
| `androidx.navigation:navigation-compose` | 2.7.7 in repo | Add analytics settings route | Existing settings flow already uses `NavHost` + `SettingsScreen` routes |
| `com.google.code.gson:gson` | 2.10.1 in repo | Encode/decode immutable analytics snapshot if using Preferences DataStore | Repo already serializes structured settings data with Gson |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.time` | JDK 17 / API 24+ | Local-day buckets and human-readable totals | Use for `LocalDate`-keyed 7-day trend aggregation |
| `java.text.BreakIterator` | JDK 17 | Better multilingual word counting | Use instead of whitespace splitting when computing raw/final word counts |
| `androidx.navigation:navigation-testing` | 2.7.7 if added | NavHost verification in androidTest | Use only if you add route-level tests for the analytics destination |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Dedicated analytics storage file | Add analytics keys to existing `settings` DataStore | Reject: backup rules exclude files/folders, not individual DataStore keys, so PRIV-06 becomes weak |
| Fixed 7-day Compose visualization | Vico `3.0.3` | Better chart primitives, but current Vico docs/releases target a much newer Kotlin/Compose stack than this repo |
| `BreakIterator` word counting | `text.trim().split("\\s+")` | Simpler, but undercounts/behaves poorly for non-whitespace-delimited languages |

**Installation:**

Baseline implementation needs no new runtime dependency.

```kotlin
dependencies {
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.7")
}
```

## Architecture Patterns

### Recommended Project Structure
```text
android/app/src/main/java/com/github/shekohex/whisperpp/
├── analytics/
│   ├── AnalyticsModels.kt        # immutable snapshot + daily bucket models
│   ├── AnalyticsRepository.kt    # local-only persistence, reset, aggregation updates
│   └── AnalyticsFormatter.kt     # time-saved, WPM, hero-summary derivation
├── ui/settings/
│   ├── AnalyticsDashboardCard.kt # settings home mini dashboard
│   └── AnalyticsDashboardScreen.kt # dedicated analytics destination
├── WhisperInputService.kt        # analytics hooks at terminal dictation outcomes
└── ui/settings/SettingsHelp.kt   # analytics help copy entry
```

### Pattern 1: Dedicated Analytics Storage Boundary
**What:** Keep analytics in a separate persisted store/file from the existing settings DataStore.
**When to use:** Always for analytics in this phase.
**Example:**
```kotlin
// Source pattern: Android DataStore docs + current repo top-level singleton pattern
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
```

Use the same singleton pattern for analytics, but not the same file as `settings`.

### Pattern 2: Finalize Metrics on Terminal Outcomes Only
**What:** Capture analytics once, when the session outcome is known: cancel, raw insert kept, or enhancement result applied/fell back to raw.
**When to use:** Every dictation session.
**Example:**
```kotlin
// Source pattern: WhisperInputService transcription + enhancement flow
val captured = dictationController.insertRawAndCaptureSegment(token, rawText)
if (captured != null) {
    analyticsRepository.recordCompletedSession(
        durationMs = recordingDurationMs,
        rawText = rawText,
        finalInsertedText = finalText,
        recordedOn = LocalDate.now(),
    )
}
```

### Pattern 3: Load Dashboard Data from the Repository, Not Navigation Args
**What:** Add a route and load analytics from the repository inside the destination; do not pass dashboard payloads around.
**When to use:** Dedicated analytics screen navigation.
**Example:**
```kotlin
// Source: https://developer.android.google.cn/develop/ui/compose/navigation?hl=en
@Composable
fun AnalyticsScreen(onBack: () -> Unit) { /* load from repository */ }

composable("analytics") {
    AnalyticsScreen(onBack = { navController.popBackStack() })
}
```

### Anti-Patterns to Avoid
- **Shared settings file for analytics:** backup exclusion is file-level, so analytics can ride along with backed-up settings.
- **Counting partial transcripts:** streaming partials would inflate minutes/words/sessions.
- **Persisting display strings:** keep raw counts/dates only; derive labels in formatter/UI.
- **Treating raw insert and enhancement replace as two sessions:** replacement must update one session, not create another.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Local-only persistence | A second ad-hoc DB or many loose preference keys | One dedicated analytics repository over a single analytics storage file | Small dataset, atomic reset/update, easier tests/migrations |
| Word counting | Regex/whitespace-only tokenizer | `BreakIterator.getWordInstance(...)` | Better behavior for punctuation and non-Latin scripts |
| Backup/privacy boundary | Key-level exclusion inside shared DataStore | Separate analytics file + explicit backup/device-transfer excludes | Android backup rules operate on files/folders, not individual keys |
| Visualization scope | A reusable in-house chart framework | A fixed 7-day dashboard composable | Requirement is narrow; generic charting is unnecessary complexity |

**Key insight:** the privacy guarantee drives the storage shape. If analytics share a backed-up file, the UI can still look right while PRIV-06 is technically wrong.

## Common Pitfalls

### Pitfall 1: Analytics Leak into Backup or Device Transfer
**What goes wrong:** “Local-only” analytics end up in Google Drive backup or device-to-device restore.
**Why it happens:** `allowBackup` is enabled, the XML rules are effectively empty, and Android backup includes app data by default.
**How to avoid:** Put analytics in a separate store/file and add explicit excludes in both `backup_rules.xml` and `data_extraction_rules.xml`.
**Warning signs:** Analytics survive reinstall/device migration when the user never explicitly exported them.

### Pitfall 2: Double Counting Enhanced Sessions
**What goes wrong:** Raw insert counts once, then enhancement replacement counts again.
**Why it happens:** The service inserts raw text first and may later replace it in place.
**How to avoid:** Treat one dictation run as one session record with `rawWords` and `finalInsertedWords` fields.
**Warning signs:** Session count grows faster than completed sends; WPM/time-saved spikes after enhancement is enabled.

### Pitfall 3: Naive Word Counting Breaks for Multilingual Text
**What goes wrong:** Arabic/CJK/punctuation-heavy transcripts produce poor word totals and WPM.
**Why it happens:** `split(" ")`-style logic assumes whitespace-separated words.
**How to avoid:** Use `BreakIterator` and keep keystrokes/time-saved based on final inserted characters.
**Warning signs:** Long inserted text shows zero/very low words or inconsistent WPM.

### Pitfall 4: Reset Clears Totals but Leaves Trend State
**What goes wrong:** Hero values zero out but the 7-day chart still shows bars.
**Why it happens:** Reset logic clears aggregates but forgets the daily buckets/history.
**How to avoid:** Reset one immutable snapshot object, not individual counters.
**Warning signs:** Empty state and chart disagree immediately after reset.

## Code Examples

Verified patterns from official sources:

### Preferences DataStore Singleton
```kotlin
// Source: https://developer.android.google.cn/topic/libraries/architecture/datastore?hl=en
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
```

### Navigation Callback Separation
```kotlin
// Source: https://developer.android.google.cn/develop/ui/compose/navigation?hl=en
@Composable
fun ProfileScreen(
    userId: String,
    navigateToFriendProfile: (String) -> Unit,
) {
}
```

### Word Boundary Counting
```kotlin
// Source: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/BreakIterator.html
val boundary = BreakIterator.getWordInstance(locale)
boundary.setText(text)
```

### Minimal Vico Alternative Example
```kotlin
// Source: https://raw.githubusercontent.com/patrykandpatrick/vico/master/guide/v3.x.x/compose/cartesian-charts/starter-examples.md
val modelProducer = remember { CartesianChartModelProducer() }
LaunchedEffect(Unit) {
    modelProducer.runTransaction {
        lineSeries { series(13, 8, 7, 12, 0, 1, 15) }
    }
}
CartesianChartHost(
    rememberCartesianChart(
        rememberLineCartesianLayer(),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(),
    ),
    modelProducer,
)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `SharedPreferences` for small local state | DataStore + Flow + transactional updates | Current Android docs | Keep analytics in DataStore-style storage, not legacy prefs |
| `allowBackup="false"` as the only privacy control | Android 12+ separate `cloud-backup` and `device-transfer` rules | Android 12 | PRIV-06 needs explicit `data_extraction_rules.xml` handling |
| Plain settings row summaries | Rich overview cards + nested destinations | Repo Phase 6 settings redesign | Analytics should be a mini dashboard card plus dedicated screen, not a ListItem |

**Deprecated/outdated:**
- Storing analytics inside the existing shared settings DataStore file.
- Assuming empty backup XML means “nothing is backed up.”
- Passing loaded dashboard objects through navigation instead of re-reading from the repository.

## Open Questions

1. **What baseline should define “time saved compared to typing”?**
   - What we know: the metric must be a soft estimate and the hero number on both screens.
   - What's unclear: exact typing baseline constant (words/minute vs characters/second).
   - Recommendation: centralize one app-owned constant in `AnalyticsFormatter` and test it; do not scatter math across UI.

2. **How should the analytics store avoid backup most safely?**
   - What we know: file-level backup rules mean analytics must be isolated from backed-up settings data.
   - What's unclear: whether implementation should rely on a dedicated no-backup file path, explicit XML excludes, or both.
   - Recommendation: plan for both a dedicated analytics file and explicit backup/D2D excludes; validate the exact file path in Wave 0.

3. **Is an external chart library worth it on this toolchain?**
   - What we know: current Vico docs/releases are current, but this repo is on an older Compose/Kotlin stack.
   - What's unclear: compatibility without dependency churn.
   - Recommendation: start with a fixed 7-day Compose visual; only add a library after an early compatibility check.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 4.13.2 + AndroidX Compose UI test + AndroidJUnitRunner |
| Config file | none — Gradle defaults in `android/app/build.gradle.kts` |
| Quick run command | `./android/gradlew testDebugUnitTest` |
| Full suite command | `./android/gradlew testDebugUnitTest connectedDebugAndroidTest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| STATS-01 | Aggregate local dictation minutes, sent/cancelled sessions, raw/final words, WPM, keystrokes saved | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.AnalyticsRepositoryTest" -x lint` | ❌ Wave 0 |
| STATS-02 | Render settings-home mini dashboard, dedicated analytics route, 7-day trend, and reset flow | androidTest | `./android/gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.shekohex.whisperpp.ui.settings.AnalyticsDashboardUiTest` | ❌ Wave 0 |
| PRIV-06 | Keep analytics local-only, exclude from backup/export defaults, and fully reset state | unit | `./android/gradlew testDebugUnitTest --tests "com.github.shekohex.whisperpp.analytics.AnalyticsPrivacyTest" -x lint` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./android/gradlew testDebugUnitTest`
- **Per wave merge:** `./android/gradlew testDebugUnitTest` plus targeted `connectedDebugAndroidTest` for analytics UI
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsRepositoryTest.kt` — covers STATS-01
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsFormatterTest.kt` — covers STATS-01 and time-saved/WPM math
- [ ] `android/app/src/test/java/com/github/shekohex/whisperpp/analytics/AnalyticsPrivacyTest.kt` — covers PRIV-06 backup/reset boundaries
- [ ] `android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/settings/AnalyticsDashboardUiTest.kt` — covers STATS-02 visual/reset flow
- [ ] `androidx.navigation:navigation-testing:2.7.7` — if route-level NavHost tests are added

## Sources

### Primary (HIGH confidence)
- Local repo: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt` — grouped home-card pattern and section ordering
- Local repo: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt` — route whitelist, nested settings destinations, shared `SettingsGroup`
- Local repo: `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHelp.kt` — route-aware help-sheet pattern
- Local repo: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` — dictation lifecycle, recording duration, raw insert, enhancement replacement, cancel hooks
- Local repo: `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt` — insert/replace/undo semantics and terminal session behavior
- Local repo: `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsBackupModels.kt` + `android/app/src/main/res/xml/backup_rules.xml` + `android/app/src/main/res/xml/data_extraction_rules.xml` + `android/app/src/main/AndroidManifest.xml` — current backup/privacy surface
- https://developer.android.google.cn/topic/libraries/architecture/datastore?hl=en — DataStore small-dataset guidance, singleton rules, preferences vs typed storage
- https://developer.android.google.cn/develop/ui/compose/navigation?hl=en — navigation arguments, callback separation, navigation testing guidance
- https://developer.android.google.cn/guide/topics/data/autobackup?hl=en — default backup participation, backed-up files, include/exclude behavior, `getNoBackupFilesDir()` exclusion
- https://developer.android.google.cn/about/versions/12/backup-restore?hl=en — Android 12+ `cloud-backup` vs `device-transfer` rule split
- https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/BreakIterator.html — word-boundary analysis behavior

### Secondary (MEDIUM confidence)
- https://raw.githubusercontent.com/patrykandpatrick/vico/master/guide/v3.x.x/getting-started.md — Vico modules, minSdk 23, current install guidance
- https://raw.githubusercontent.com/patrykandpatrick/vico/master/guide/v3.x.x/compose/cartesian-charts/starter-examples.md — Compose chart starter example
- https://raw.githubusercontent.com/patrykandpatrick/vico/master/guide/v3.x.x/compose/cartesian-charts/cartesianchartmodelproducer.md — model producer persistence/update guidance
- https://github.com/patrykandpatrick/vico — current release signal (`v3.0.3`, 2026-03-07)

### Tertiary (LOW confidence)
- No low-confidence external claims used. The typing-baseline constant remains a product decision, not a source-backed fact.

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM — repo patterns are clear, but the best backup-safe storage split still needs Wave 0 validation
- Architecture: MEDIUM — terminal-outcome capture points are clear, but time-saved math and storage isolation details need one implementation decision each
- Pitfalls: HIGH — backup defaults, Android 12 rule changes, and dictation replacement semantics are well supported by official docs and local code

**Research date:** 2026-03-09
**Valid until:** 2026-04-08
