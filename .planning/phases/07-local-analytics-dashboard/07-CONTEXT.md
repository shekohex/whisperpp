# Phase 7: Local Analytics Dashboard - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver a local-only analytics dashboard inside settings so users can review Whisper++ usage, see a time-saved summary, inspect recent trends, and reset all analytics data. This phase clarifies how analytics is presented and reset; it does not add cloud telemetry or unrelated new metrics.

</domain>

<decisions>
## Implementation Decisions

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

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt`: already has grouped home sections, setup banner handling, and reusable overview-card patterns that can host the analytics home card.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`: provides shared `SettingsGroup` styling and the existing nested settings navigation pattern for a dedicated analytics destination.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHelp.kt`: route-aware help sheets can be extended for analytics guidance.
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`: DataStore-backed repository pattern is the established place for persisted local settings/summary data access.

### Established Patterns
- Settings UI is Material 3 with grouped cards and nested screens, not a flat list.
- Settings home uses concise status summaries on cards, with deeper detail moved into dedicated screens.
- Destructive or important settings actions already use dialogs and in-place feedback rather than separate wizard flows.
- Persistence is local via DataStore flows collected directly in Compose screens.

### Integration Points
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsHomeScreen.kt`: add the first-class analytics mini dashboard section here.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`: add a dedicated analytics route/screen to the existing settings navigation.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`: current dictation flow already exposes recording duration, successful insertions, enhancement outcomes, and command apply points where local analytics events can be captured.
- `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt`: session lifecycle, cancellation, raw insertion, and replacement hooks exist for analytics counting.
- No analytics-specific storage or UI currently exists, so Phase 7 will introduce the first local analytics data model and presentation flow.

</code_context>

<specifics>
## Specific Ideas

- Dedicated analytics screen should feel similar to Android Digital Wellbeing.
- The home entry should feel like a mini dashboard card, not a plain settings row.
- Time saved should be the main story, with a 7-day trend that makes the dashboard feel active and recent.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope.

</deferred>

---

*Phase: 07-local-analytics-dashboard*
*Context gathered: 2026-03-09*
