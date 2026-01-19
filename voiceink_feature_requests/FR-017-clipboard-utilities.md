# Clipboard utilities (last transcript, retry)

## Summary
Provide quick actions to copy/paste the last transcription and retry transcription.

## Gap vs Whisper++
No history or quick clipboard utilities.

## User Stories
- As a user, I want to paste the last transcript quickly.
- As a user, I want to retry the last transcription with a different model.

## Acceptance Criteria
- Actions to copy/paste last transcript (raw or enhanced).
- Retry last transcription from stored audio when available.
- Optional restore previous clipboard contents after paste.

## Dependencies
- [FR-009 Transcription history](FR-009-transcription-history.md)
