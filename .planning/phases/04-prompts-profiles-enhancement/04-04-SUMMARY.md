---
phase: 04-prompts-profiles-enhancement
plan: 04
subsystem: ime
tags: [enhancement, dictation, runtime-resolver, undo, compose]

# Dependency graph
requires:
  - phase: 04-prompts-profiles-enhancement
    provides: prompt composition + RuntimeSelectionResolver (phase 04 earlier plans)
provides:
  - Raw-first dictation enhancement pipeline (insert raw immediately, replace later)
  - Segment-scoped replacement with focus-safety drop behavior
  - Single latest enhancement undo to restore raw transcript
  - IME transient enhancement notices + enhancement undo quick action
affects: [05-command-mode-presets, 06-settings-ux-import-export]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - RuntimeSelectionResolver used for effective STT + TEXT selections
    - Raw-first insert then segment replacement, guarded by FocusKey
    - Enhancement undo stored as single latest entry (independent of dictation undo)

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/dictation/EnhancementUndoEntry.kt
  modified:
    - android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt

key-decisions:
  - "Allow raw insertion even when app send-policy blocks external sending; block enhancement network only."
  - "Surface resolver warnings + enhancement fallback as transient in-IME notices (info/error) with auto-dismiss."
  - "Keep enhancement undo separate from dictation undo and valid even after manual edits."

patterns-established:
  - "Captured dictation segment contract for post-insert operations (replace/undo)"
  - "Enhancement replacement requires FocusKey match; otherwise drop replacement"

requirements-completed: [ENH-01, ENH-02, ENH-03, ENH-04, PROF-03, PROF-04]

# Metrics
duration: 44 min
completed: 2026-03-05
---

# Phase 04 Plan 04: Raw-first enhancement replacement pipeline Summary

**Dictation now inserts raw text immediately, then runs enhancement with effective prompt + text model and replaces the original segment in-place with single-tap undo back to raw.**

## Performance

- **Duration:** 44 min
- **Started:** 2026-03-05T04:31:20Z
- **Completed:** 2026-03-05T05:15:30Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- Added captured segment contract to support segment-scoped replacement and undo.
- Wired RuntimeSelectionResolver precedence into dictation STT and enhancement TEXT, with warning notices and safe fallbacks.
- Implemented IME enhancement notices (info/error) and an enhancement-undo quick action.

## Task Commits

Each task was committed atomically:

1. **task 1: Add raw insertion capture + segment replacement + enhancement undo (single latest)** - `3d1ded9` (feat)
2. **task 2: Rewire WhisperInputService to apply resolver precedence for BOTH dictation STT and enhancement TEXT (raw-first) + notices** - `c93c496` (feat)
3. **task 3: Add IME UI support for enhancement notices and enhancement-undo quick action** - `1ac1b68` (feat)

## Files Created/Modified

- `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/EnhancementUndoEntry.kt` - single latest enhancement undo contract.
- `android/app/src/main/java/com/github/shekohex/whisperpp/dictation/DictationController.kt` - raw segment capture, segment replacement, enhancement undo apply.
- `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt` - resolver precedence wiring, raw-first enhancement orchestration, notices/undo state.
- `android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt` - propagate cancellation to caller for EnhancementRunner classification.
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt` - transient notice banner + enhancement undo chip.

## Decisions Made

- Used an in-IME transient notice banner (tap to dismiss + auto-dismiss) to surface resolver warnings and enhancement fallback reasons.
- Enhancement undo is a dedicated single-entry contract, independent from dictation undo, and does not validate current segment contents.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Kotlin compilation issues from `return@try` usage were fixed by rewriting try blocks to return booleans without labeled returns.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Enhancement pipeline is segment-scoped and focus-safe; ready for follow-up plans that extend UX/presets.

## Self-Check: PASSED
