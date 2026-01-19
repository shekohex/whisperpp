# Audio file transcription

## Summary
Allow users to import an audio file and transcribe it through the same pipeline.

## Gap vs Whisper++
Only live mic transcription is supported.

## User Stories
- As a user, I want to transcribe meeting recordings.
- As a user, I want the result stored in history with metadata.

## Acceptance Criteria
- File picker for common audio formats with conversion as needed.
- Progress UI and cancel support.
- Output stored in history with model, duration, and source.

## Dependencies
- [FR-009 Transcription history](FR-009-transcription-history.md)
