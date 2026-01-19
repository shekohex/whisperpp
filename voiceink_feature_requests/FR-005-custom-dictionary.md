# Custom dictionary (spellings + replacements)

## Summary
Allow users to define custom spellings and text replacements applied after transcription.

## Gap vs Whisper++
No built-in dictionary or replacement system exists.

## User Stories
- As a user, I want domain terms to be recognized correctly.
- As a user, I want automatic replacements (e.g., product names, abbreviations).

## Acceptance Criteria
- Two lists: correct spellings and replacement rules.
- Import/export dictionary (JSON/CSV).
- Applied consistently across live transcription and audio-file transcription.
- Optional per-language scoping.

## Dependencies
- [FR-009 Transcription history](FR-009-transcription-history.md)
