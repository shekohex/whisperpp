# Phase 1: Privacy & Safety Controls - Context

**Gathered:** 2026-02-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can control what data is sent and avoid accidental capture/leaks:
- Dictation and command mode are unavailable in secure fields with a clear explanation
- Provider API keys are stored securely and excluded from logs
- Network logging is redacted by default (no auth headers; no user audio/text payloads)
- Per-app send policy can block all external sending and is enforced
- UI discloses what data is sent (audio/text) and which provider endpoint(s) receive it

</domain>

<decisions>
## Implementation Decisions

### Secure fields: gating + blocked UX
- Treat as secure: password-like fields, OTP-like fields, and any "no personalized learning"/privacy-flagged fields
- In secure fields, mic/command keys stay visible but disabled with a lock icon
- No proactive "secure field" banner; explanation appears only when user taps the disabled action
- Tapping disabled keys opens a details sheet
- Details sheet includes the reason + a link to Privacy & Safety settings
- Include "Don't show again" for the secure-field explanation (global setting)
- If dictation/command is active and focus moves into a secure field: stop immediately; no further sending; show a short notice

### Data disclosure surfaces
- Primary disclosure lives on a dedicated Privacy & Safety screen in Settings
- Show a one-time disclosure on first use (once globally)
- Disclose base URL plus endpoint paths used
- Disclosure is mode-specific: audio vs text; dictation vs enhancement vs command mode

### Per-app send policy
- Default for apps without a rule: Allow
- Per-app policy is a single toggle: allow/block all external sending (no audio-vs-text split)
- Settings UI uses a searchable installed-apps list (show package name in app details)
- When blocked for current app: relevant keyboard actions disabled; tap explains and deep-links to that app's rule

### Secrets + diagnostics boundaries
- API keys are never shown in full by default; show "Key set" + last 4 characters
- No copy/export of keys from UI (replace/clear only)
- Provide a user-facing toggle for verbose networking logs, but logs remain redacted (no auth headers; no user audio/text payloads)
- Error UI stays minimal and safe: provider name + status code + base URL domain only (no paths, no payload, no key)

### OpenCode's Discretion
- None explicitly granted; remaining microcopy/layout can vary as long as it honors decisions above

</decisions>

<specifics>
## Specific Ideas

No specific product references - open to standard patterns.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope.

</deferred>

---

*Phase: 01-privacy-and-safety-controls*
*Context gathered: 2026-02-26*
