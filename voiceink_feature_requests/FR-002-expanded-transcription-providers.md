# Expanded transcription providers

## Summary
Add additional cloud STT providers (Groq, Deepgram, Mistral, Gemini, Soniox, ElevenLabs) plus custom provider profiles.

## Gap vs Whisper++
Whisper++ supports OpenAI, Whisper ASR, NVIDIA NIM, and OpenAI-compatible endpoints only.

## User Stories
- As a user, I want provider choice for cost, latency, and accuracy.
- As a team, we want to use our preferred provider without proxying.

## Acceptance Criteria
- Provider-specific settings (API key, endpoint, model list, language support).
- Consistent error handling and retry/backoff.
- Per-provider capability detection (streaming, language list, diarization if available).

## Dependencies
- [FR-001 Model management](FR-001-model-management.md)
