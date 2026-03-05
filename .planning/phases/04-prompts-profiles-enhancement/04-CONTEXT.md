# Phase 4: Prompts, Profiles & Enhancement - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver per-app and per-language prompt/model behavior for dictation enhancement, then safely replace dictated text with enhancement output and support undo back to raw transcript.

This phase clarifies behavior within that boundary only; no new capabilities are added.

</domain>

<decisions>
## Implementation Decisions

### Override precedence
- Prompt composition: `global base prompt + mapped profile append + app-specific append`.
- App mapping can explicitly disable append (`no-append`) while still keeping global base prompt.
- Model/provider precedence: `app override > per-language default > global default`.
- Partial app overrides cascade missing fields from per-language, then global defaults.
- Per-language defaults resolve STT and text channels independently.
- Unmapped apps use global profile path plus per-language defaults.
- If mapped profile is deleted: fallback to global behavior and flag mapping as needing attention.
- If override references missing/invalid provider-model: fallback down precedence and surface a non-blocking warning.

### Enhancement trigger policy
- Enhancement auto-runs on every successful dictation stop, including short transcripts.
- Empty or punctuation-only transcripts skip enhancement and insert as-is.
- If enhancement cannot run (policy block, invalid model, etc.), insert raw transcript and show reason.
- Timeout strategy: balanced wait before raw fallback; offline path still attempts then times out.
- Retry policy: one quick retry for transient failures (including rate-limit), then fallback to raw.
- On fallback, show detailed notice each time; include provider + model; auto-dismiss after short duration.
- Fallback raw insertion is verbatim STT output (no local rewrite).
- If fallback reason is intentional app send-policy block, use info-style notice (not error-style).
- Keep trying enhancement on each dictation even after repeated prior failures (no auto-disable).
- Enhancement is single-flight: new dictation cannot start while active dictation/enhancement flow is running.
- If focus/app changes before enhancement returns, do not apply replacement.

### Replace + undo flow
- Insert raw transcript immediately, then replace that dictated segment in place on enhancement success.
- Replacement targets original captured dictated segment, not current caret location.
- If user edits raw segment before enhancement returns, still force replacement with enhancement output.
- Undo restores the exact raw dictated segment in the original replacement range.
- Undo scope is single latest enhancement replacement.
- Undo remains valid even if user manually edited enhanced text before tapping undo.
- Caret lands at end of replaced segment after successful replacement.
- Replacement is segment-scoped only; surrounding text must not be modified.
- Expose enhancement undo via temporary IME undo action/chip, available until next dictation/replacement.
- Even when replacement overwrote interim edits, undo restore source remains original raw transcript.

### Profile mapping workflow
- App mapping picker: searchable installed-app list plus manual package-name fallback.
- Mapping granularity: one profile per app package.
- Unmapped apps remain unmapped and run default behavior.
- Per-app overrides (append/STT/text) are edited inside mapping detail screen.
- Include user and system apps in mapping picker.
- Support bulk multi-select mapping (assign one profile to multiple apps in one action).
- Keep mappings by package even if app is uninstalled (reapply if app returns).
- If profile is deleted while referenced, keep mapping row flagged and fallback behavior active.

### OpenCode's Discretion
- Exact timeout values for "balanced wait" and "quick retry".
- Exact UI component choice and wording style for detailed fallback notices and info-style policy notices.
- App picker sorting/grouping defaults and system-app visual treatment.

</decisions>

<specifics>
## Specific Ideas

- Strong preference for explicit, detailed runtime feedback over silent fallback.
- Preference for deterministic, layered precedence and single-flight enhancement behavior.

</specifics>

<deferred>
## Deferred Ideas

- Redo action after enhancement undo (separate capability; future phase).

</deferred>

---

*Phase: 04-prompts-profiles-enhancement*
*Context gathered: 2026-03-04*
