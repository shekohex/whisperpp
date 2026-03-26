---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 09
current_phase_name: privacy traceability command disclosure alignment
current_plan: 4
status: ready_for_verification
stopped_at: Completed 09-04-PLAN.md
last_updated: "2026-03-26T21:03:09.639Z"
progress:
  total_phases: 9
  completed_phases: 9
  total_plans: 38
  completed_plans: 38
  percent: 100
---

# STATE: Whisper++

**Core value:** Speak anywhere; get accurate, context-appropriate text inserted instantly.

**Current Phase:** 09
**Current Phase Name:** privacy traceability command disclosure alignment
**Status:** Phase complete — ready for verification
**Current Plan:** 4
**Total Plans in Phase:** 4

**Progress:** [██████████] 100%

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 02-providers-models P01 | 20 min | 3 tasks | 8 files |
| Phase 02-providers-models P02 | 10 min | 2 tasks | 1 files |
| Phase 02-providers-models P03 | 16 min | 3 tasks | 5 files |
| Phase 02-providers-models P04 | 3 min | 3 tasks | 3 files |
| Phase 02-providers-models P05 | 6h 32m | 3 tasks | 4 files |
| Phase 03-dictation P01 | 5 min | 2 tasks | 3 files |
| Phase 03-dictation P02 | 32 min | 3 tasks | 6 files |
| Phase 03-dictation P03 | 9 min | 2 tasks | 4 files |
| Phase 03-dictation P04 | 25 min | 3 tasks | 5 files |
| Phase 04-prompts-profiles-enhancement P03 | 2 min | 2 tasks | 3 files |
| Phase 04-prompts-profiles-enhancement P02 | 3 min | 2 tasks | 2 files |
| Phase 04-prompts-profiles-enhancement P01 | 21 min | 2 tasks | 5 files |
| Phase 04 P05 | 17 min | 2 tasks | 1 files |
| Phase 04 P04 | 44 min | 3 tasks | 5 files |
| Phase 04 P07 | 1 min | 2 tasks | 2 files |
| Phase 04-prompts-profiles-enhancement P06 | 12 min | 2 tasks | 1 files |
| Phase 05-command-mode-presets P02 | 10 min | 2 tasks | 7 files |
| Phase 05-command-mode-presets P01 | 18 min | 3 tasks | 6 files |
| Phase 05 P03 | 25 min | 3 tasks | 4 files |
| Phase 06-settings-ux-import-export P01 | 8 min | 2 tasks | 5 files |
| Phase 06-settings-ux-import-export P03 | 6 min | 2 tasks | 6 files |
| Phase 06 P02 | 24 min | 2 tasks | 4 files |
| Phase 06 P04 | 21 min | 2 tasks | 4 files |
| Phase 07-local-analytics-dashboard P01 | 16 min | 3 tasks | 9 files |
| Phase 07-local-analytics-dashboard P02 | 8 min | 2 tasks | 3 files |
| Phase 07-local-analytics-dashboard P03 | 19 min | 3 tasks | 7 files |
| Phase 08-phase-06-verification-recovery P01 | 14 min | 2 tasks | 3 files |
| Phase 08-phase-06-verification-recovery P02 | 4 min | 2 tasks | 2 files |
| Phase 09 P01 | 5 min | 3 tasks | 7 files |
| Phase 09 P02 | 53 min | 3 tasks | 6 files |
| Phase 09 P03 | 10 min | 3 tasks | 8 files |
| Phase 09 P04 | 0 min | 1 tasks | 1 files |

## Accumulated Context

### Decisions

- v1 is BYO API key only (no accounts/billing/backend).
- Realtime insertion is capability-gated; composing text for partials.
- Enhancement replaces in place with 1-tap undo; fallback to raw on failure.
- Command mode uses best-effort selection, with clipboard fallback.
- Networking errors shown to users are constrained to provider + HTTP code + endpoint host.
- Provider networking logs default to NONE with an opt-in HEADERS mode (no BODY).
- Cancellation is treated as benign and must cancel the underlying OkHttp Call.
- External-send entry points must use a single IME secure-field gate before any network-bound action.
- Secure-field blocked UX is tap-to-explain only (no proactive banner) with a global don’t-show-again preference.
- Settings destination requests from IME must route via MainActivity extras and safely fall back to settings main.
- Provider API keys are runtime-only on `ServiceProvider` and must never serialize into providers JSON/export payloads.
- Plaintext provider `apiKey` migration uses raw JSON tree parsing with sanitize-then-flag ordering guarded by `PROVIDER_API_KEY_MIGRATION_DONE`.
- IME dictation and Smart Fix must inject provider API keys from `SecretsStore` at call time via `provider.copy(apiKey=...)`.
- Per-app send policy persists as packageName→blocked JSON with default-allow semantics when no rule exists.
- Privacy & Safety must be addressable as a dedicated settings destination (`privacy_safety`) for IME deep-links.
- IME external-send blocking must enforce both secure-field and per-app rules, with app-rule-specific blocked explanation.
- Blocked explanation fallback uses a dedicated activity host when in-IME sheet rendering is unreliable.
- Privacy disclosures must be generated from live provider config (base URL + endpoint path) via a shared formatter.
- First-use disclosure gating is mode-specific (`dictation`, `enhancement`, `command`) and resettable from Privacy & Safety.
- Verbose diagnostics logging is user-togglable but constrained to redacted HEADERS logging only.
- [Phase 02-providers-models]: Kept JSON field name endpoint for compatibility; semantics are now base URL
- [Phase 02-providers-models]: Default auth mode is API_KEY except WHISPER_ASR which defaults to NO_AUTH
- [Phase 02]: Gemini Smart Fix uses x-goog-api-key header (no key= in URL)
- [Phase 02]: Provider diagnostics show raw response in UI with redaction; never log bodies
- [Phase 03-dictation]: Release from hold pauses recording (no auto-transcribe)
- [Phase 03]: DICT-07 focus safety uses FocusKey with focusInstanceId incremented on onStartInput and onStartInputView
- [Phase 03]: Non-streaming dictation finalizes only on explicit Send; mic release pauses without inserting
- [Phase 03]: Undo validates selected text matches inserted transcript before deleting
- [Phase 03]: Undo quick action visibility is sticky after insertion and cleared on next dictation action
- [Phase 03]: Gate streaming partials to OpenAI provider + realtime protocol derivable from base URL
- [Phase 03]: Finalize insertion always uses composing replace path (setComposingText + finishComposingText)
- [Phase 03]: Best-effort finalize uses last known streaming transcript if non-streaming transcription fails
- [Phase 04]: EnhancementRunner uses withTimeout + single retry envelope
- [Phase 04]: Enhancement skip policy: blank/punctuation-only transcripts are never sent
- [Phase 04]: Prompts & profiles settings: base prompt uses explicit Save action (trim-on-save)
- [Phase 04]: Enhancement pipeline is raw-first with segment-scoped replace + single enhancement-undo
- [Phase 04]: RuntimeSelectionResolver precedence applied for dictation STT and enhancement TEXT, with non-blocking notices
- [Phase 05]: SelectionResolver returns NeedsClipboard(snapshot) when selection indices exist but selected text is unreadable
- [Phase 05]: Enhancement default preset fallback is cleanup; command default fallback is tone_rewrite
- [Phase 05]: Command mode listening uses explicit Stop action (no auto-stop).
- [Phase 05]: Clipboard fallback requires per-run preview confirmation and tracks attempts remaining.
- [Phase 06-settings-ux-import-export]: Use PBKDF2WithHmacSHA1 plus AES-GCM metadata-backed envelopes for API 24-compatible password backups.
- [Phase 06-settings-ux-import-export]: Keep provider credentials in a dedicated encrypted backup category separate from provider JSON serialization.
- [Phase 06-settings-ux-import-export]: Freeze the Phase 6 backup category manifest so later import/export UI can reuse stable IDs, labels, and sensitive flags.
- [Phase 06-settings-ux-import-export]: Settings home is a grouped overview with setup-critical items first and maintenance controls visually secondary.
- [Phase 06-settings-ux-import-export]: Settings help uses a shared route-aware bottom sheet across home and nested screens.
- [Phase 06-settings-ux-import-export]: Settings deep-links resolve through a centralized whitelist so nested destinations work and unknown routes fall back safely.
- [Phase 06]: Import always produces a typed analysis preview before any restore writes.
- [Phase 06]: Merge semantics are category-scoped with imported values winning conflicts and explicit include/exclude support.
- [Phase 06]: Post-restore validation clears unusable selections and returns repair checklist entries for missing credentials.
- [Phase 06]: Backup and restore now lives in a dedicated nested settings screen instead of loose home actions.
- [Phase 06]: Backup and restore status flows back to settings home through savedStateHandle so users stay in settings after export or restore.
- [Phase 06]: Restore repair actions route credential fixes to Providers and selection fixes to Provider selections.
- [Phase 07-local-analytics-dashboard]: Analytics persistence uses a dedicated datastore file so backup exclusion can operate at file granularity.
- [Phase 07-local-analytics-dashboard]: Dashboard strings remain derived from raw counts at read time; formatted copy is never persisted.
- [Phase 07-local-analytics-dashboard]: Seven-day history is stored as a fixed LocalDate-keyed bucket window with dates serialized as strings for JVM-safe tests.
- [Phase 07-local-analytics-dashboard]: WhisperInputService now keys analytics on DictationController sessionId so retries, duplicate callbacks, and streaming terminal paths collapse into one outcome write.
- [Phase 07-local-analytics-dashboard]: Completed analytics are emitted only after the raw insert and enhancement outcome are known, with Smart Fix cancellation after raw insertion treated as a raw completion instead of a cancelled run.
- [Phase 07-local-analytics-dashboard]: Analytics dashboard instrumentation injects AnalyticsSnapshot directly into Compose content so empty, populated, and reset scenarios remain deterministic.
- [Phase 07-local-analytics-dashboard]: Stable analytics UI coverage uses minimal test tags on the home card and numeric values plus scroll-aware assertions for offscreen sections.
- [Phase 08]: Use explicit root test tags for shipped settings/import-export surfaces instead of copy-only selectors.
- [Phase 08]: Expose ImportPreviewCard and RestoreSummaryCard as internal composables so androidTests can render deterministic fixtures without changing production state wiring.
- [Phase 08]: Use completed 06-UAT evidence for real SAF interaction and final Material validation instead of overstating automation coverage.
- [Phase 08]: Keep UI-01, SET-01, and SET-02 mapped to phase 8 in REQUIREMENTS because phase 8 closes the audit gap rather than changing the original feature delivery phase.
- [Phase 09]: Use a task-local command disclosure seam that composes existing dictation/enhancement disclosures until wave-2 runtime alignment lands.
- [Phase 09]: Expose stable Privacy & Safety test tags so Compose verification can target disclosure and send-policy controls deterministically.
- [Phase 09]: Command disclosure rows now come from the shared formatter for both IME and Privacy & Safety.
- [Phase 09]: Command-mode first-use consent resolves before recorder start while shared send-block checks still wrap record, STT, and transform.
- [Phase 09]: Keep PRIV-01 through PRIV-05 and CMD-03 owned by phase 09 because this recovery phase closes the audit gap rather than rewriting original delivery history.
- [Phase 09]: Reuse existing SecretsStore and SettingsBackupRepositoryExportTest evidence for PRIV-02 instead of inventing new privacy-storage work.
- [Phase 09]: Preserve the phase-09 checklist and traceability rows exactly as shipped and fix only the stale REQUIREMENTS footer metadata.

### Blockers

- `connectedDebugAndroidTest` for phase 08 plan 01 still needs a real device or a KVM-capable emulator host; current environment has neither a connected device nor usable x86_64 emulator acceleration.

### Notes / To Watch

- IME editor surfaces are unreliable; drop late events on focus changes.

## Session Continuity

**Last session:** 2026-03-26T21:03:09.637Z
**Stopped At:** Completed 09-04-PLAN.md
**Resume file:** None
