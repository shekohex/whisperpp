# Context-aware AI enhancement

## Summary
Inject context into enhancement prompts using clipboard, selected text, and on-screen content when allowed.

## Gap vs Whisper++
Whisper++ SmartFix has no context capture beyond optional input-context prefix.

## User Stories
- As a user, I want the AI to understand what I’m editing to improve relevance.
- As a user, I want optional privacy-safe context controls.

## Acceptance Criteria
- Toggles for clipboard, selected text, and screen context.
- Explicit permission flow and visibility of what context is sent.
- Context is only captured at transcription time.
- Clear privacy messaging and opt-in defaults.

## Dependencies
- [FR-006 Enhancement prompt library](FR-006-enhancement-prompt-library.md)
