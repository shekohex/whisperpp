---
phase: 03-dictation
plan: 04
subsystem: dictation
tags: [ime, stt, websocket, okhttp, composing-text]

# Dependency graph
requires:
  - phase: 03-dictation
    provides: dictation controller + focus safety + send-only finalization
provides:
  - Streaming-gated partial dictation via composing text
  - OpenAI Realtime WebSocket STT client with best-effort finalize fallback
  - Recorder PCM chunk emission while still writing WAV fallback
affects: [03-dictation, 04-prompts-profiles-enhancement]

# Tech tracking
tech-stack:
  added: []
  patterns: [PCM chunk callback during recording, composing lifecycle finalize via set+finish]

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/dictation/OpenAiRealtimeSttClient.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RiffWaveHelper.kt

key-decisions:
  - "Gate streaming partials to OpenAI provider + realtime protocol derivable from base URL"
  - "Final insertion always uses composing replace path (setComposingText + finishComposingText)"
  - "Best-effort finalize uses last known streaming transcript if non-streaming transcription fails"

patterns-established:
  - "Streaming best-effort: keep visible composing partials if stream drops; finalize on Send"

requirements-completed: [DICT-02]

# Metrics
duration: 25 min
completed: 2026-03-03
---

# Phase 3 Plan 04: Streaming Partials Summary

**OpenAI Realtime WebSocket streaming partials inserted via throttled composing updates, with atomic finalize-on-send and WAV fallback recording.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-03-03T19:52:43Z
- **Completed:** 2026-03-03T20:17:30Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Recorder can emit PCM chunks during recording while still producing a valid WAV file.
- OpenAI Realtime STT client streams partials and captures final transcript best-effort.
- Dictation controller throttles composing partial updates and finalizes atomically via composing replace path.

## task Commits

Each task was committed atomically:

1. **task 1: Add PCM chunk callback support in RecorderManager (streaming-friendly)** - `051e1e0` (feat)
2. **task 2: Implement OpenAI Realtime streaming STT client (OkHttp WebSocket) gated by model capability** - `110b0cb` (feat)
3. **task 3: Add composing-text insertion lifecycle with throttled partial updates and atomic finalize-on-send** - `13309a2` (feat)

Auto-fix (required for correctness):

- **[Rule 1 - Bug] Best-effort finalize when streaming drops** - `6558238` (fix)

**Plan metadata:** `41fc152` (docs)

## Files Created/Modified
- `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt` - Optional PCM chunk emission while recording WAV.
- `android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RiffWaveHelper.kt` - WAV header sample rate configurable.
- `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/OpenAiRealtimeSttClient.kt` - OkHttp WebSocket client for realtime transcription events.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - Streaming gating + wiring PCM chunks to realtime client.
- `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt` - Throttled composing partials + atomic finalize via composing.

## Decisions Made
- Streaming partials only enabled when model requests it *and* provider is OpenAI with a realtime URL derivable from its base endpoint; otherwise downgrade to non-streaming with a toast.
- Finalization always uses composing replace path (`setComposingText` + `finishComposingText`) inside batch edit.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Best-effort finalize when streaming drops**
- **Found during:** task 3 (composing lifecycle + finalize)
- **Issue:** If streaming drops and non-streaming transcription fails, send could lose the best-effort transcript even though partial was visible.
- **Fix:** Track last known realtime transcript and use it to finalize on transcription error for streaming sessions.
- **Files modified:** `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`
- **Verification:** `./android/gradlew -p android :app:assembleDebug`
- **Committed in:** `6558238`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Required for DICT-02 correctness; no scope creep.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- DICT-02 behavior in place; ready to verify end-to-end on device/emulator with an OpenAI realtime-capable endpoint.

## Self-Check: PASSED

- Found: `.planning/phases/03-dictation/03-04-SUMMARY.md`
- Found commits: `051e1e0`, `110b0cb`, `13309a2`, `6558238`
