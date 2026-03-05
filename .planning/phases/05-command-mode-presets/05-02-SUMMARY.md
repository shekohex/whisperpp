---
phase: 05-command-mode-presets
plan: 02
subsystem: command
tags: [inputconnection, selection, undo, prompt, junit]

requires:
  - phase: 03-dictation
    provides: FocusKey and focus-safety patterns
  - phase: 04-prompts-profiles-enhancement
    provides: Effective base prompt composition consumed by command mode
provides:
  - Best-effort selection resolution with snapshot + clipboard-fallback signaling
  - Timeboxed command undo entry contract (30s / next-run)
  - Deterministic prompt assembly for selected-text transforms
affects: [05-command-mode-presets, command-mode, IME]

tech-stack:
  added: []
  patterns:
    - Best-effort selection snapshot via ExtractedText selection indices
    - Selection readability detection -> NeedsClipboard(snapshot)
    - Undo lifecycle: 30s TTL and cleared on next run

key-files:
  created:
    - android/app/src/main/java/com/github/shekohex/whisperpp/command/CommandModels.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/command/SelectionResolver.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/command/CommandController.kt
    - android/app/src/main/java/com/github/shekohex/whisperpp/command/TransformPromptBuilder.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/command/SelectionResolverTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/command/CommandControllerTest.kt
    - android/app/src/test/java/com/github/shekohex/whisperpp/command/TransformPromptBuilderTest.kt
  modified: []

key-decisions:
  - "When selection indices exist but getSelectedText is null/blank, return NeedsClipboard(snapshot) to preserve apply/undo targeting."
  - "Embed selected text as a fenced ```text block and include an explicit spoken-overrides-preset rule in transform prompts."

patterns-established:
  - "SelectionResolver.resolve(editorInfo, inputConnection) returns Selected | NeedsClipboard | None(Reason)"
  - "CommandController.startRun() increments runId and clears previous undo"

requirements-completed: [CMD-02, CMD-04]

duration: 10 min
completed: 2026-03-05
---

# Phase 5 Plan 2: Command core contracts Summary

**Selection snapshot + clipboard-fallback signaling, deterministic transform prompt assembly, and a 30s/next-run undo contract (all unit-tested).**

## Performance

- **Duration:** 10 min
- **Started:** 2026-03-05T22:18:37Z
- **Completed:** 2026-03-05T22:29:06Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Added `SelectionResolver` returning Selected/NeedsClipboard/None with preserved selection snapshots.
- Added `CommandController` run lifecycle + timeboxed undo entry contract (30s / next-run).
- Added `TransformPromptBuilder` with explicit spoken-overrides-preset conflict rule and delimited selected-text embedding.

## task Commits

Each task was committed atomically:

1. **task 1: Add selection resolver + command run/undo models with unit coverage** - `4f5c189` (feat)
2. **task 2: Add TransformPromptBuilder with explicit conflict rule + unit coverage** - `7e13125` (feat)

**Plan metadata:** _pending_

## Files Created/Modified

- `android/app/src/main/java/com/github/shekohex/whisperpp/command/CommandModels.kt` - Selection snapshot, resolved selection, and command undo entry models.
- `android/app/src/main/java/com/github/shekohex/whisperpp/command/SelectionResolver.kt` - Best-effort selection snapshot + selected-text read with clipboard fallback signal.
- `android/app/src/main/java/com/github/shekohex/whisperpp/command/CommandController.kt` - Command run-id + undo lifecycle contract (30s / next-run).
- `android/app/src/main/java/com/github/shekohex/whisperpp/command/TransformPromptBuilder.kt` - Deterministic prompt assembly with override rule + delimited selected text.
- `android/app/src/test/java/com/github/shekohex/whisperpp/command/SelectionResolverTest.kt` - Unit coverage for selection resolution cases.
- `android/app/src/test/java/com/github/shekohex/whisperpp/command/CommandControllerTest.kt` - Unit coverage for undo expiry + cleared on next run.
- `android/app/src/test/java/com/github/shekohex/whisperpp/command/TransformPromptBuilderTest.kt` - Unit coverage for prompt assembly requirements.

## Decisions Made

- Preserve selection snapshot even when selected text is unreadable (`NeedsClipboard(snapshot)`) to keep apply/undo targeting stable.
- Delimit selected text in prompts and explicitly declare spoken-instruction priority over preset on conflict.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Gradle test run initially started from the wrong working directory; rerun from repo root succeeded.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Ready for 05-03 wiring: IME command mode can reuse `SelectionResolver` (selection vs clipboard fallback) and `TransformPromptBuilder` (prompt assembly), with undo state managed by `CommandController`.

## Self-Check: PASSED

- FOUND: .planning/phases/05-command-mode-presets/05-02-SUMMARY.md
- FOUND: task commits 4f5c189, 7e13125
