# VoiceInk feature gap analysis for Whisper++

This directory captures VoiceInk features that Whisper++ does not have (or has only partially), framed as feature requests with user stories.

## Feature list
- [FR-001 Model management (providers and models)](FR-001-model-management.md) — `wpp-71c`
- [FR-002 Expanded transcription providers](FR-002-expanded-transcription-providers.md) — `wpp-bdt`
- [FR-003 Power Mode profiles (per-app)](FR-003-power-mode-profiles.md) — `wpp-qld`
- [FR-004 Context-aware AI enhancement](FR-004-context-aware-enhancement.md) — `wpp-bib`
- [FR-005 Custom dictionary (spellings + replacements)](FR-005-custom-dictionary.md) — `wpp-7sd`
- [FR-006 Enhancement prompt library (Smart Modes)](FR-006-enhancement-prompt-library.md) — `wpp-s2v`
- [FR-007 Trigger words to auto-switch prompts](FR-007-trigger-words-for-prompts.md) — `wpp-lu1`
- [FR-008 AI assistant mode](FR-008-ai-assistant-mode.md) — `wpp-ub4`
- [FR-009 Transcription history management](FR-009-transcription-history.md) — `wpp-3j9`
- [FR-010 Performance analytics dashboard](FR-010-performance-analytics.md) — `wpp-ffe`
- [FR-011 Audio file transcription](FR-011-audio-file-transcription.md) — `wpp-dhl`
- [FR-012 Automatic cleanup and retention controls](FR-012-auto-cleanup-retention.md) — `wpp-glx`
- [FR-013 Custom start/stop sounds](FR-013-custom-sounds.md) — `wpp-4y7`
- [FR-014 Audio input selection and priority](FR-014-audio-input-selection.md) — `wpp-fjv`
- [FR-015 Media playback control during recording](FR-015-media-playback-control.md) — `wpp-d0r`
- [FR-016 In-app announcements feed](FR-016-announcements-feed.md) — `wpp-a5t`
- [FR-017 Clipboard utilities (last transcript, retry)](FR-017-clipboard-utilities.md) — `wpp-rtj`
- [FR-018 Voice Activity Detection (VAD)](FR-018-voice-activity-detection.md) — `wpp-efu`
- [FR-019 Automatic text formatting](FR-019-text-formatting.md) — `wpp-9xn`
- [FR-020 Guided onboarding and setup](FR-020-guided-onboarding.md) — `wpp-oj5`

## Delivery plan table

| ID | Feature | Priority | Effort | Dependencies | Phase |
| --- | --- | --- | --- | --- | --- |
| FR-001 | Model management | P0 | M |  | Foundations |
| FR-002 | Expanded providers | P1 | M | FR-001 | Foundations |
| FR-003 | Power Mode profiles | P0 | L | FR-006, FR-004 | Personalization |
| FR-004 | Context-aware enhancement | P1 | M | FR-006 | Enhancement |
| FR-005 | Custom dictionary | P1 | M | FR-009 | Enhancement |
| FR-006 | Prompt library | P1 | M | FR-004, FR-008 | Enhancement |
| FR-007 | Trigger words | P2 | S | FR-006 | Enhancement |
| FR-008 | AI assistant mode | P2 | M | FR-006 | Enhancement |
| FR-009 | Transcription history | P0 | L | FR-011, FR-012 | History |
| FR-010 | Performance analytics | P2 | M | FR-009 | History |
| FR-011 | Audio file transcription | P1 | M | FR-009 | History |
| FR-012 | Auto cleanup | P1 | S | FR-009 | History |
| FR-013 | Custom sounds | P2 | S |  | Quality |
| FR-014 | Audio input selection | P1 | M |  | Quality |
| FR-015 | Media playback control | P2 | S |  | Quality |
| FR-016 | Announcements feed | P3 | S |  | Quality |
| FR-017 | Clipboard utilities | P1 | S | FR-009 | History |
| FR-018 | Voice Activity Detection | P2 | S |  | Foundations |
| FR-019 | Automatic text formatting | P2 | S | FR-009 | Enhancement |
| FR-020 | Guided onboarding | P1 | M | FR-001 | Foundations |

## Big TODO list (delivery checklist)

- Consolidate provider/model configuration into a single settings area.
- Add provider validation and test connection actions.
- Expand provider integrations and normalize error handling.
- Implement per-app Power Mode profiles and priority ordering.
- Add enhancement prompt library with create/edit/reorder.
- Add context capture with explicit permissions and privacy controls.
- Implement trigger word detection and safe stripping from output.
- Add AI assistant prompt mode and UI cues.
- Implement custom dictionary with spellings and replacements + import/export.
- Persist transcription history with search, filtering, and bulk actions.
- Add audio file import/transcription with progress and cancellation.
- Implement analytics aggregation (RTF, durations, provider stats).
- Add retention rules and manual cleanup flows for transcripts/audio.
- Add clipboard utilities: copy/paste last transcript and retry last audio.
- Add custom start/stop sound selection and test controls.
- Add audio input selection and device priority handling.
- Add media playback pause/resume during recording using audio focus.
- Add announcements feed with opt-in and caching.
- Add VAD pre-processing with tuning controls.
- Add automatic text formatting toggle and language-aware rules.
- Add onboarding checklist for first-run setup and re-entry in settings.
- Instrument and test: unit tests for data pipelines, UI tests for flows.
- Security & privacy: redact context in logs, clear user data on request.
- Release gating: feature flags, staged rollout, crash/ANR monitoring.

