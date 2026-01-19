# Power Mode profiles (per-app)

## Summary
Auto-apply transcription and enhancement settings based on the foreground app context.

## Gap vs Whisper++
Whisper++ has global settings only; no per-app profiles.

## User Stories
- As a user, I want different models for email vs coding vs chat.
- As a bilingual user, I want app-specific language settings.
- As a user, I want auto-send enabled only in specific apps.

## Acceptance Criteria
- Profile creation UI with app/package matching rules.
- Auto-apply profile on app focus with manual override.
- Profile settings include model, language, prompt, enhancement, auto-send.
- Default profile and priority ordering.

## Dependencies
- [FR-006 Enhancement prompt library](FR-006-enhancement-prompt-library.md)
- [FR-004 Context-aware enhancement](FR-004-context-aware-enhancement.md)
