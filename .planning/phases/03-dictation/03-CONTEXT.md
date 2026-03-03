# Phase 3: Dictation - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver reliable dictation in the IME with clear mic states, streaming-gated partials, cancellation, and undo. Keep insertions safe across focus/app changes. This phase does not add new capabilities beyond dictation behavior.

</domain>

<decisions>
## Implementation Decisions

### Mic interaction flow
- Interaction model should feel like WhatsApp/Telegram voice-note UX.
- Primary input is hold-to-talk; releasing finger pauses (does not send).
- Swipe up while holding enters locked hands-free mode.
- In paused/locked controls: trash on left, resume center, send on right.
- Swipe-to-cancel remains available in both hold and locked modes; require a longer swipe to reduce accidental cancel.
- Trash cancel requires confirmation; once confirmed, remove visible partial text immediately.
- Recording state uses VAD-driven wave effect while active; paused state shows paused icon + timer.
- Start/lock/cancel transitions should have haptic feedback.
- If keyboard hides or user switches keyboard/app, auto-pause and show toast (no auto-cancel/finalize).
- No hard dictation duration limit.
- Paused sessions do not expire; user must explicitly resume/send/cancel.

### Partial text behavior
- If streaming is supported, show partials inline as composing text.
- Partial updates should be throttled/smooth rather than every tiny chunk.
- If streaming is unsupported/disabled, insert no partials; insert only final text on send/stop.
- Finalization should atomically replace composing content, and always run replace path even when final equals latest partial.
- Live partials should use minimal punctuation/capitalization; spoken formatting commands (e.g., "new line", "comma") are disabled.
- Streaming may rewrite earlier words without a strict backtracking window.
- Disable manual text edits while recording is active.
- Keep visible partial text when paused.
- Use standard platform composing style; do not add confidence styling.
- If stream drops mid-session, keep partial text and finalize best effort.
- Keep partial behavior consistent across languages.
- Do not show a special "changed a lot" notice when final differs from partial.

### Cancel and undo rules
- Cancel discards the current dictation session content.
- Undo supports a multi-step dictation stack, stepped by repeated undo taps.
- One send operation equals one undo step, even with pause/resume inside the session.
- Undo appears as an inline quick action and stays until the next dictation action.
- Undo should still target dictated segment after manual typing when safe; if not safely applicable, fail with clear notice.
- Undo is scoped to the current field/context only (not cross-app/global).
- After undo, restore cursor to original insertion position.

### Focus safety handling
- Auto-pause on focus change, app backgrounding, and focus change while finger is holding mic.
- After auto-pause, require explicit resume (no auto-resume).
- Sending is allowed to current focused editable field.
- Track app + field identity for session context/safety decisions.
- If no editable field is focused on send: copy transcript to clipboard, show toast, keep session paused.
- If focused field is secure on send: block send, keep session paused, wait for non-secure field.
- If focus enters secure field while paused: keep paused until non-secure field is focused.
- Multiple focus changes keep a single consistent paused state.
- Safety messages should be short, toast-style reason strings.

### OpenCode's Discretion
- Exact gesture thresholds (lock swipe-up distance, cancel swipe distance/tolerance).
- Exact VAD wave rendering approach (native capability vs custom vs external package), while preserving chosen UX behavior.

</decisions>

<specifics>
## Specific Ideas

- "Make it similar to WhatsApp/Telegram voice note input."
- "Swipe to cancel ... gesture handling needs more control."
- "We are using a state machine, we should be leveraging that."

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope.

</deferred>

---

*Phase: 03-dictation*
*Context gathered: 2026-03-03*
