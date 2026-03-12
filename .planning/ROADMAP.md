# ROADMAP: Whisper++

**Depth:** comprehensive
**Scope:** v1 requirements only
**Current focus:** Phase 8

## Overview

Whisper++ is an Android keyboard (IME) that turns speech into text in any app, with optional LLM-based enhancement and a selected-text command mode. v1 focuses on correctness across arbitrary editors, BYO providers/models, reversible transforms, and privacy-first controls.

## Phases

| Phase | Name | Goal | Status |
|------:|------|------|--------|
| 1 | Privacy & Safety Controls | Users can control what data is sent and avoid accidental capture/leaks | Complete |
| 2 | Providers & Models | Users can configure BYO providers/models for STT + text transforms | Complete |
| 3 | Dictation | Users can dictate reliably with streaming-gated partials, cancellation, and undo | Complete (4/4, 2026-03-03) |
| 4 | Prompts, Profiles & Enhancement | Users get per-app/per-language prompting + safe post-dictation enhancement with undo | Complete (7/7, 2026-03-05) |
| 5 | Command Mode & Presets | Users can transform selected text via voice instructions with clipboard fallback and undo | Complete (3/3, 2026-03-05) |
| 6 | Settings UX + Import/Export | Users can configure and back up/restore all core behavior with polished settings UI | Complete (4/4, 2026-03-09) |
| 7 | Local Analytics Dashboard | Users can view and reset local-only usage analytics and time-saved estimates | Complete (3/3, 2026-03-11) |
| 8 | Phase 06 Verification Recovery | Users can verify shipped settings/import-export behavior so phase-06 requirements are no longer orphaned | Complete (2/2, 2026-03-12) |
| 9 | Privacy Traceability & Command Disclosure Alignment | Users see accurate command disclosures before command-mode capture and privacy requirements regain traceable coverage | Planned |

---

### Phase 1: Privacy & Safety Controls

**Goal:** Users can control what data is sent and avoid accidental capture/leaks.

**Dependencies:** None

**Requirements:** PRIV-01, PRIV-02, PRIV-03, PRIV-04, PRIV-05

**Success Criteria (observable):**
1. In secure fields (password/OTP/etc), dictation and command mode are unavailable, and the UI explains why.
2. Users can configure providers without exposing API keys in plaintext UI/logs; keys remain protected across app restarts.
3. Network/debug logging does not include auth headers or user audio/text payloads by default.
4. Users can set a per-app send policy to block sending audio/text externally; when blocked, Whisper++ refuses to send and clearly explains the block.
5. The UI clearly discloses what data is sent (audio/text) and which provider endpoint(s) will receive it.

**Plans:** 6 plans

Plans:
- [x] 01-01-PLAN.md — Harden networking (redacted logs, safe errors, true cancellation)
- [x] 01-02-PLAN.md — Secure-field detection + IME send gate + disabled UI with explanation
- [x] 01-03-PLAN.md — Keystore-backed API key storage + remove plaintext persistence/export
- [x] 01-04-PLAN.md — Per-app send policy UI + enforcement (block external sending per app)
- [x] 01-05-PLAN.md — Privacy disclosures (settings + first-use) + redacted verbose logging toggle
- [x] 01-06-PLAN.md — IME-safe blocked explanation (secure-field + app-policy) gap closure

### Phase 2: Providers & Models

**Goal:** Users can configure BYO providers/models for STT + text transforms.

**Dependencies:** Phase 1

**Requirements:** PROV-01, PROV-02, PROV-03, PROV-04, PROV-05, PROV-06

**Success Criteria (observable):**
1. Users can add/edit/delete providers (type, base URL, API key) and manage a per-provider model list.
2. Provider models expose kind (stt/text/multimodal) and indicate whether streaming partials are supported.
3. Users can choose an STT provider/model for dictation and a (potentially different) text provider/model for enhancement/command mode.
4. OpenAI-compatible endpoints work for STT and text transforms, and Gemini-compatible endpoints work for text transforms.

**Plans:** 5 plans

Plans:
- [x] 02-01-PLAN.md — Provider/model schema v2 + migration + disclosure derivation tests
- [x] 02-02-PLAN.md — Provider edit UX rules + model metadata editor (kind + streaming)
- [x] 02-03-PLAN.md — Active STT/text selections + command override + setup-needed banner
- [x] 02-04-PLAN.md — Runtime wiring for selections + base-URL routing (OpenAI STT/text, Gemini text)
- [x] 02-05-PLAN.md — Provider Test actions + optional model import with raw response UI

### Phase 3: Dictation

**Goal:** Users can dictate reliably with streaming-gated partials, cancellation, and undo.

**Dependencies:** Phase 1, Phase 2

**Requirements:** DICT-01, DICT-02, DICT-03, DICT-04, DICT-05, DICT-06, DICT-07

**Success Criteria (observable):**
1. Users can start/stop dictation via a mic key with a clear recording state.
2. If the selected STT model supports streaming, partial transcription updates appear as composing text while speaking.
3. If streaming is unsupported/disabled, no partials are inserted; only the final transcript is inserted on stop.
4. Users can cancel dictation and no additional text is inserted after cancel.
5. Users can select dictation language, undo the last dictation insertion, and results never insert into a different field/app after focus changes.

### Phase 4: Prompts, Profiles & Enhancement

**Goal:** Users get per-app/per-language prompting + safe post-dictation enhancement with undo.

**Dependencies:** Phase 1, Phase 2, Phase 3

**Requirements:** PROF-01, PROF-02, PROF-03, PROF-04, ENH-01, ENH-02, ENH-03, ENH-04

**Success Criteria (observable):**
1. Users can define a global base prompt and create prompt profiles.
2. Users can map apps (by package name) to a prompt profile; per-app mapping can override prompt append and chosen STT/text providers/models.
3. Users can configure per-language defaults (language -> STT and text providers/models) and see them applied when dictating/transforming.
4. After dictation stops, enhancement runs by default and inserts enhanced text; if enhancement fails, Whisper++ falls back to the raw transcript.
5. When enhancement succeeds, Whisper++ auto-replaces the dictated segment in place, and users can undo to restore the raw transcript.

### Phase 5: Command Mode & Presets

**Goal:** Users can transform selected text via voice instructions with clipboard fallback and undo.

**Dependencies:** Phase 1, Phase 2, Phase 3, Phase 4

**Requirements:** CMD-01, CMD-02, CMD-03, CMD-04, ENH-05

**Success Criteria (observable):**
1. Users can enter command mode via a dedicated Command key.
2. Command mode uses selected text from the editor when available; otherwise it guides the user through a clipboard fallback workflow.
3. Users can speak an instruction; Whisper++ transcribes it and sends (instruction + selected text) to the selected text model/provider.
4. Command mode replaces the selection with the result and provides 1-tap undo to restore the original selection.
5. Users can choose from a preset transform library (>= 3 presets) for both dictation enhancement and selected-text transforms.

**Plans:** 3 plans

Plans:
- [x] 05-01-PLAN.md — Shared transform preset library + per-mode default presets (settings)
- [x] 05-02-PLAN.md — Command core contracts (selection resolver, undo contract, prompt builder) with unit tests
- [x] 05-03-PLAN.md — IME command mode end-to-end (Command key, clipboard fallback, voice instruction, transform, replace + undo, preset picker)

### Phase 6: Settings UX + Import/Export

**Goal:** Users can configure and back up/restore all core behavior with polished settings UI.

**Dependencies:** Phase 1, Phase 2, Phase 3, Phase 4, Phase 5

**Requirements:** UI-01, SET-01, SET-02

**Success Criteria (observable):**
1. Settings UI uses Material 3 components and theming.
2. Users can configure all core behavior in settings (providers/models, prompts, per-app/per-language overrides, dictation/enhancement/command toggles).
3. Users can export full settings to a shareable file and import it to restore settings (with clear overwrite/merge behavior).

**Plans:** 4 plans

Plans:
- [x] 06-01-PLAN.md — Build the encrypted backup envelope and full export snapshot foundation
- [x] 06-02-PLAN.md — Add import analysis/apply semantics with partial restore and repair reporting
- [x] 06-03-PLAN.md — Rework settings home into grouped Material 3 cards with contextual help
- [x] 06-04-PLAN.md — Ship the backup/restore UI flow with preview, merge/overwrite choice, and completion summary

### Phase 7: Local Analytics Dashboard

**Goal:** Users can view and reset local-only usage analytics and time-saved estimates.

**Dependencies:** Phase 3, Phase 5, Phase 6

**Requirements:** STATS-01, STATS-02, PRIV-06

**Success Criteria (observable):**
1. Users can view local-only usage stats (dictation minutes, sessions, words dictated, words/min, keystrokes saved).
2. Settings home includes an analytics dashboard with an estimated time-saved summary.
3. Users can reset analytics, and analytics are not transmitted by default.

**Plans:** 3 plans

Plans:
- [x] 07-01-PLAN.md — Build the local-only analytics repository, formatter, and backup/privacy boundary
- [x] 07-02-PLAN.md — Capture completed vs cancelled dictation analytics exactly once in the IME runtime
- [x] 07-03-PLAN.md — Add the settings-home analytics mini dashboard, dedicated analytics screen, and reset UI

### Phase 8: Phase 06 Verification Recovery

**Goal:** Users can rely on verified settings and import/export behavior, and phase-06 requirements are no longer orphaned from audit coverage.

**Dependencies:** Phase 6

**Requirements:** UI-01, SET-01, SET-02

**Gap Closure:** Closes the audit orphaned-requirement gaps caused by missing phase-06 verification coverage and restores traceable verification for the shipped settings/import-export work.

**Plans:** 2/2 plans complete

Plans:
- [x] 08-01-PLAN.md — Add stable settings verification seams and focused phase-06 Compose UI coverage
- [x] 08-02-PLAN.md — Author 06-VERIFICATION.md and update recovered requirement traceability

### Phase 9: Privacy Traceability & Command Disclosure Alignment

**Goal:** Users see accurate privacy disclosures before command-mode audio is captured, and phase-01 privacy requirements are traceable to verified artifacts again.

**Dependencies:** Phase 1, Phase 5

**Requirements:** PRIV-01, PRIV-02, PRIV-03, PRIV-04, PRIV-05, CMD-03

**Gap Closure:** Closes the audit partial gaps for PRIV-01 through PRIV-05, fixes the phase-01 to phase-05 command disclosure integration issue, and restores the first-time command-mode privacy and consent flow before spoken instruction capture.
