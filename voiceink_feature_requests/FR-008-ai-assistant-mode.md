# AI assistant mode

## Summary
Provide a prompt mode that returns direct answers (chat-style) instead of cleanup.

## Gap vs Whisper++
SmartFix is post-processing only; no assistant output mode.

## User Stories
- As a user, I want to ask short questions by voice and paste the answer.

## Acceptance Criteria
- Assistant prompt mode with different instruction template.
- Clear UI distinction between assistant and cleanup modes.
- Supports context capture toggles from [FR-004](FR-004-context-aware-enhancement.md).

## Dependencies
- [FR-006 Enhancement prompt library](FR-006-enhancement-prompt-library.md)
