# Phase 2: Providers & Models - Context

**Gathered:** 2026-03-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can configure BYO providers/models for STT and text transforms:
- Add/edit/delete providers with type, base URL, and API key
- Maintain per-provider model lists with kind and streaming-partials capability metadata
- Choose active STT provider/model for dictation
- Choose active text provider/model for enhancement and command mode
- Support OpenAI-compatible endpoints for STT and text transforms, and Gemini-compatible endpoints for text transforms

</domain>

<decisions>
## Implementation Decisions

### Provider setup UX
- Provider names default to `type + host` and are user-editable.
- Base URL is prefilled per provider type and remains overrideable.
- Save is blocked until all required fields are valid.
- API key is required by default, with explicit no-auth mode for local/self-hosted endpoints.
- If deleting a provider that is currently selected, deletion flow requires reassignment before delete completes.
- Provider type is immutable after creation; include a Duplicate action to clone an existing provider into a new editable draft.
- Provide a manual non-blocking provider test action after save.
- When same type + base URL already exists, warn but still allow duplicates.

### Model catalog rules
- Models are manually manageable, with optional provider fetch/import.
- `multimodal` models are available in both STT and text selectors.
- New STT-capable models default `streaming partials supported` to off.
- If deleting a model currently selected, deletion flow requires reassignment before delete completes.

### Default selection behavior
- Active STT and text selections require explicit user choice; no auto-selection.
- Enhancement and command mode share one text selection by default, with optional advanced command override.
- If STT is set but text is unset, STT-only actions remain available while text-dependent actions stay blocked.
- When active selections become invalid, show an immediate setup-needed banner with CTA to the direct selectors page.
- Selection UIs show compatible options only.
- Active selection uses a two-step picker: provider, then model.
- Users can explicitly clear STT/text selections to `none`.
- If command override is unset, it inherits shared text selection changes until explicitly overridden.

### Compatibility behavior
- Provider type does not hard-block usage; compatibility filtering is based on model kind.
- For potentially unsupported operations, attempt the request and show provider response.
- Provider setup uses base URL only (no per-operation endpoint input).
- Test UI offers capability-specific tests and explicit test-anyway options.
- Error detail preference is full raw provider response.

### OpenCode's Discretion
- No explicit discretion granted beyond microcopy/layout details.

</decisions>

<specifics>
## Specific Ideas

- Include a Duplicate button in provider details for quick clone-and-edit flow.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope.

</deferred>

---

*Phase: 02-providers-models*
*Context gathered: 2026-03-02*
