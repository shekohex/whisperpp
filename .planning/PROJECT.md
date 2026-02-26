# Whisper++

## What This Is

Whisper++ is an Android keyboard (IME) that turns speech into text in any app.
Users bring their own OpenAI-compatible and/or Gemini-compatible provider endpoints + API keys, then choose models + prompts.
The keyboard supports dictation (with optional LLM enhancement) and a command mode to transform selected text.

## Core Value

Speak anywhere; get accurate, context-appropriate text inserted instantly.

## Requirements

### Validated

- [x] Android IME keyboard UI that can record and insert text into the focused input (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`) - existing
- [x] Audio recording to WAV via `AudioRecord` (`android/app/src/main/java/com/github/shekohex/whisperpp/recorder/RecorderManager.kt`) - existing
- [x] Configurable STT providers (OpenAI Whisper endpoint, self-hosted whisper-asr, custom) (`android/app/src/main/java/com/github/shekohex/whisperpp/WhisperTranscriber.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/data/ProviderModels.kt`) - existing
- [x] Optional Smart Fix post-processing via OpenAI Chat Completions or Google Gemini (`android/app/src/main/java/com/github/shekohex/whisperpp/SmartFixer.kt`, `android/app/src/main/res/raw/smart_fix_prompt.txt`) - existing
- [x] Settings UI + persistence for providers/profiles via DataStore (`android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`, `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`) - existing
- [x] Self-update via GitHub Releases (check/download/verify/install) (`android/app/src/main/java/com/github/shekohex/whisperpp/updater/*`) - existing

### Active

- [ ] Provider/model system: providers expose a list of models with kinds (stt, text, multimodal) and capabilities (e.g., streaming, context size)
- [ ] Dictation realtime composing insertion when the selected STT model supports partials/streaming; disable realtime when unsupported
- [ ] Dictation pipeline: insert raw composing text while speaking; on stop run enhancement (base prompt + per-app prompt append) and auto-replace in place; fallback to raw if enhancement fails; 1-tap undo
- [ ] Manual per-app prompt profiles (by app package name) with base + per-app append
- [ ] Selected-text command mode: Command key -> read selection best-effort (clipboard fallback) -> apply spoken instruction via LLM -> replace selection with undo
- [ ] Secrets/privacy hardening (API keys storage, redaction, avoid logging sensitive content)

### Out of Scope

- Managed service mode (accounts/billing/backend) - v1 is BYO API key only
- Always-on voice keyword activation - too intrusive for v1
- Accessibility service dependency for selection - rely on IME best-effort + clipboard fallback
- Automatic app context classification for prompts - manual mapping in v1

## Context

- Android app with dual surfaces: settings app UI (Compose) + keyboard UI hosted in `InputMethodService`
- Persistence via `DataStore<Preferences>`; providers/profiles stored as JSON
- Network: OkHttp for transcription + Smart Fix; Ktor for update checks
- Existing provider presets include OpenAI Whisper (STT), OpenAI Chat Completions (Smart Fix), Google Gemini (Smart Fix), and a self-hosted whisper-asr service
- Distribution: GitHub Releases with an in-app updater

## Constraints

- **Platform**: Android IME (must work across third-party apps); minSdk 24
- **Interoperability**: support OpenAI-compatible and Gemini-compatible APIs; user-configured base URLs/keys
- **UX**: realtime is only enabled when the chosen STT model supports it; enhancement replaces in place with undo
- **Security/Privacy**: never log or leak API keys; treat selected text as sensitive user data

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| v1 targets BYO API key power users | Avoid managed backend/auth/billing in early versions | Pending |
| Default output is LLM-enhanced; fallback to raw transcript on failure | Keep speed/robustness while still getting polish | Pending |
| Realtime insertion uses composing text; realtime disabled when provider can't stream | IME-friendly updates without breaking apps; graceful degradation | Pending |
| Enhancement auto-replaces dictated segment in place with 1-tap undo | Fast flow, reversible | Pending |
| Prompting is base prompt + per-app append; per-app mapping is manual | Predictable behavior and user control | Pending |
| Include selected-text command mode in v1; Command key; replace selection; clipboard fallback | High leverage workflow without requiring accessibility | Pending |
| Provider abstraction includes providers -> models with kinds/capabilities | Needed for pluggable STT/LLM/multimodal | Pending |

---
*Last updated: 2026-02-26 after initialization*
