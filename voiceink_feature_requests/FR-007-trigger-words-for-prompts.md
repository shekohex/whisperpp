# Trigger words to auto-switch prompts

## Summary
Detect spoken trigger words to auto-enable enhancement and select a prompt.

## Gap vs Whisper++
No trigger-word mechanism exists.

## User Stories
- As a user, I want to say “email:” to auto-apply my email prompt.
- As a user, I want the trigger word removed from the final text.

## Acceptance Criteria
- Per-prompt trigger word list.
- Trigger words stripped from output safely.
- Temporary prompt override auto-restores after completion.

## Dependencies
- [FR-006 Enhancement prompt library](FR-006-enhancement-prompt-library.md)
