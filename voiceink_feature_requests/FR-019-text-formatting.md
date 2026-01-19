# Automatic text formatting

## Summary
Auto-format transcriptions into readable paragraphs and punctuation rules.

## Gap vs Whisper++
No built-in formatting beyond SmartFix cleanup.

## User Stories
- As a user, I want long dictations split into paragraphs automatically.

## Acceptance Criteria
- Toggle to enable/disable formatting.
- Formatting runs after transcription and before SmartFix (if enabled).
- Language-aware rules with safe defaults.

## Dependencies
- [FR-009 Transcription history](FR-009-transcription-history.md)
