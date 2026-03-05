# Phase 5: Command Mode & Presets - Context

**Gathered:** 2026-03-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Enable command mode via a dedicated Command key to transform selected text with spoken instructions. When no selection exists, guide users through clipboard fallback; apply the transform result by replacing text with one-tap undo. Include a starter preset library (>= 3) usable for both dictation enhancement and selected-text transforms.

</domain>

<decisions>
## Implementation Decisions

### Clipboard fallback flow
- If no selection exists, show a guided fallback sheet with short, actionable steps.
- Require explicit clipboard preview confirmation (short snippet + character count) before using clipboard text.
- Resume only after user taps Continue; capture spoken instruction after text is confirmed.
- For very large clipboard text, show a warning but allow intentional continue.
- Allow two attempts total (initial + one retry); then abort safely with clear guidance.
- On cancel or fallback failure, exit command mode with no text changes.
- Do not reuse previously confirmed clipboard text; each command run requires fresh confirmation.
- If clipboard access is unavailable, show the reason and a manual select/copy recovery path.

### Preset library behavior
- Ship starter presets: cleanup, shorten, and tone rewrite.
- Provide an in-flow quick picker sheet during command mode.
- Use one shared preset library across dictation enhancement and selected-text transforms, with separate defaults per mode.
- Combine preset + spoken instruction; spoken instruction overrides preset where they conflict.

### Apply + undo behavior
- Apply transform results by immediate replacement of the selected text.
- Provide one-tap undo, available until the next command run or 30 seconds (whichever comes first).
- On transform failure/timeout, keep original text unchanged and show an error with Retry CTA.
- After apply or undo, keep resulting/restored text selected for fast re-run.

### Command-mode interaction
- Dedicated Command key uses single-tap to enter command mode.
- Show a compact status overlay with stage feedback (waiting, listening, processing, done/error).
- Support cancel via explicit Cancel control and Command-key toggle.
- On listen silence, reprompt once; if still silent, exit cleanly.

### OpenCode's Discretion
- Exact visual styling (layout, spacing, iconography, motion) for fallback sheet, status overlay, and preset picker.
- Exact copy polish for prompts/errors while preserving the selected behavior.

</decisions>

<specifics>
## Specific Ideas

- Preset and spoken instruction should apply together, not be mutually exclusive.
- Conflict rule is explicit: spoken instruction wins where it conflicts with preset intent.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope.

</deferred>

---

*Phase: 05-command-mode-presets*
*Context gathered: 2026-03-05*
