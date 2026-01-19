# Automatic cleanup and retention controls

## Summary
Auto-delete old transcripts and audio files based on retention settings.

## Gap vs Whisper++
No retention controls or automated cleanup.

## User Stories
- As a privacy-sensitive user, I want old transcripts removed automatically.
- As a user, I want to manage storage usage.

## Acceptance Criteria
- Separate retention policies for transcripts and audio files.
- Manual cleanup action with preview of what will be deleted.
- Background cleanup scheduling with clear logs.

## Dependencies
- [FR-009 Transcription history](FR-009-transcription-history.md)
