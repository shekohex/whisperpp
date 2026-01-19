# Transcription history management

## Summary
Persist transcriptions locally with search, bulk actions, and export.

## Gap vs Whisper++
Whisper++ does not store or manage transcription history.

## User Stories
- As a user, I want to find and reuse past transcriptions.
- As a user, I want to delete sensitive transcripts in bulk.

## Acceptance Criteria
- Local history database with metadata (model, duration, timestamps).
- Search and filters (enhanced vs raw, provider).
- Bulk select, delete, and export (CSV/JSON).

## Dependencies
- [FR-011 Audio file transcription](FR-011-audio-file-transcription.md)
- [FR-012 Auto cleanup](FR-012-auto-cleanup-retention.md)
