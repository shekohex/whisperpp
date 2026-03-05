# Requirements: Whisper++

**Defined:** 2026-02-26
**Core Value:** Speak anywhere; get accurate, context-appropriate text inserted instantly.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### UI & Settings

- [ ] **UI-01**: Settings UI uses Material 3 (Material You) components + theming
- [ ] **SET-01**: All core behavior is configurable in settings (providers/models, prompts, per-app/per-language overrides, dictation/enhancement/command toggles)
- [ ] **SET-02**: User can export/import full settings (providers/models/profiles/mappings/toggles) and share the export file; settings can be backed up and restored

### Providers & Models

- [x] **PROV-01**: User can add/edit/delete providers (type, base URL, API key) and models per provider
- [x] **PROV-02**: Provider models include kind (stt/text/multimodal) + capability flags (at minimum: streaming partials supported)
- [x] **PROV-03**: User can choose an STT model/provider for dictation
- [x] **PROV-04**: User can choose a text model/provider for enhancement + command mode (can differ from STT)
- [x] **PROV-05**: OpenAI-compatible endpoints can be used for STT and text transforms (user-configured base URL)
- [x] **PROV-06**: Gemini-compatible endpoints can be used for text transforms (user-configured base URL)

### Dictation

- [x] **DICT-01**: User can start/stop dictation via a mic key with clear recording state
- [x] **DICT-02**: If the selected STT model supports streaming, partial transcription updates insert as composing text while speaking
- [x] **DICT-03**: If streaming is unsupported/disabled, dictation inserts only the final transcript on stop (no partial insertion)
- [x] **DICT-04**: User can cancel dictation and no additional text is inserted after cancel
- [x] **DICT-05**: User can select dictation language and it is applied to STT requests
- [x] **DICT-06**: User can undo the last dictation insertion
- [x] **DICT-07**: If editor focus changes during dictation, Whisper++ does not insert late results into a different field/app

### Enhancement & Prompts

- [x] **ENH-01**: After dictation stops, Whisper++ runs enhancement by default and pastes enhanced text (fallback to raw on failure)
- [x] **ENH-02**: Enhancement uses an effective prompt = base prompt + per-app prompt append (when configured)
- [ ] **ENH-03**: When enhancement succeeds, Whisper++ auto-replaces the dictated segment in place
- [ ] **ENH-04**: User can undo the last enhancement replacement (restores raw transcript)
- [ ] **ENH-05**: Whisper++ includes a small transform preset library (at least 3) usable for dictation or selected-text transforms

### Per-App Profiles

- [ ] **PROF-01**: User can define a global base prompt
- [ ] **PROF-02**: User can create prompt profiles and manually map apps (by package name) to a profile
- [x] **PROF-03**: Per-app mapping can override prompt append and the chosen STT/text providers/models
- [x] **PROF-04**: User can configure per-language defaults (language -> STT model/provider and text model/provider)

### Command Mode (Selected Text)

- [ ] **CMD-01**: User can enter command mode via a dedicated Command key
- [ ] **CMD-02**: Command mode uses selected text from the editor when available; otherwise uses a clipboard fallback workflow
- [ ] **CMD-03**: User can speak an instruction; Whisper++ transcribes it and sends (instruction + selected text) to the selected text model/provider
- [ ] **CMD-04**: Command mode replaces the selection with the result and provides 1-tap undo

### Analytics (Local)

- [ ] **STATS-01**: Whisper++ tracks local-only usage stats (dictation minutes, sessions, words dictated, words/min, keystrokes saved)
- [ ] **STATS-02**: Settings home includes an analytics dashboard including an estimated "time saved" summary

### Privacy & Safety

- [ ] **PRIV-01**: Dictation and command mode are disabled in secure fields (password/OTP/etc) with a clear explanation
- [ ] **PRIV-02**: Provider API keys are stored securely (Keystore-backed) and excluded from logs
- [ ] **PRIV-03**: Network logging redacts auth headers and does not log user audio/text payloads by default
- [ ] **PRIV-04**: User can set per-app send policy to block sending audio/text to external providers and Whisper++ enforces it
- [ ] **PRIV-05**: UI clearly discloses what data is sent (audio/text) and to which provider endpoint(s)
- [ ] **PRIV-06**: Usage analytics are stored locally only, can be reset, and are not transmitted by default

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Dictation

- **DICT-20**: Voice punctuation phrase library and customization
- **DICT-21**: Glossary/custom vocabulary/hotwords (best-effort across providers)

### Enhancement & Trust

- **ENH-20**: Diff preview before applying replacements
- **ENH-21**: Multiple rewrite candidates with quick apply

### Command Mode

- **CMD-20**: Structured voice commands for editing/navigation (delete word, move cursor, select ranges)

### Local-First

- **LOCAL-01**: On-device STT (optional) for offline/private dictation
- **LOCAL-02**: On-device rewrite/transforms (optional)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Managed service mode (accounts/billing/backend) | v1 is BYO API key only |
| Always-on hotword activation | Too intrusive + battery/privacy risk |
| Accessibility service dependency for selection | Prefer IME + clipboard fallback |
| Automatic app context classification for prompts | Manual mapping in v1 |
| Cloud analytics/telemetry by default | Trust/privacy risk; keep analytics local-only |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| UI-01 | 6 | Pending |
| SET-01 | 6 | Pending |
| SET-02 | 6 | Pending |
| PROV-01 | 2 | Complete |
| PROV-02 | 2 | Complete |
| PROV-03 | 2 | Complete |
| PROV-04 | 2 | Complete |
| PROV-05 | 2 | Complete |
| PROV-06 | 2 | Complete |
| DICT-01 | 3 | Complete |
| DICT-02 | 3 | Complete |
| DICT-03 | 3 | Complete |
| DICT-04 | 3 | Complete |
| DICT-05 | 3 | Complete |
| DICT-06 | 3 | Complete |
| DICT-07 | 3 | Complete |
| ENH-01 | 4 | Complete |
| ENH-02 | 4 | Complete |
| ENH-03 | 4 | Pending |
| ENH-04 | 4 | Pending |
| ENH-05 | 5 | Pending |
| PROF-01 | 4 | Pending |
| PROF-02 | 4 | Pending |
| PROF-03 | 4 | Complete |
| PROF-04 | 4 | Complete |
| CMD-01 | 5 | Pending |
| CMD-02 | 5 | Pending |
| CMD-03 | 5 | Pending |
| CMD-04 | 5 | Pending |
| STATS-01 | 7 | Pending |
| STATS-02 | 7 | Pending |
| PRIV-01 | 1 | Complete |
| PRIV-02 | 1 | Complete |
| PRIV-03 | 1 | Complete |
| PRIV-04 | 1 | Complete |
| PRIV-05 | 1 | Complete |
| PRIV-06 | 7 | Pending |

**Coverage:**
- v1 requirements: 37 total
- Mapped to phases: 37
- Unmapped: 0

---
*Requirements defined: 2026-02-26*
*Last updated: 2026-02-26 after roadmap creation*
