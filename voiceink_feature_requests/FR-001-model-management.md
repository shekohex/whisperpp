# Model management (providers and models)

## Summary
Centralize management of transcription providers and their models, including validation and testing.

## Gap vs Whisper++
Model configuration is scattered and mostly manual; no unified view to manage multiple providers and model lists.

## User Stories
- As a user, I want to manage multiple providers and switch defaults quickly.
- As a user, I want model lists with display names and capabilities.
- As a user, I want to test a provider connection before saving.

## Acceptance Criteria
- CRUD providers with API keys, endpoints, and timeouts.
- Per-provider model list management (name, id, notes, language support).
- Validation and test-connection action with clear errors.
- Export/import provider configurations.
