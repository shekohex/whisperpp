---
status: diagnosed
trigger: "Diagnose UAT issue #1 root cause (do not implement fix)."
created: 2026-02-26T17:59:50Z
updated: 2026-02-26T18:06:16Z
---

## Current Focus

hypothesis: Confirmed: blocked explanation UI uses ModalBottomSheet in a constrained IME host, leading to an effectively empty sheet presentation in UAT.
test: N/A (diagnosis complete)
expecting: N/A
next_action: return root-cause diagnosis (no code changes)

## Symptoms

expected: Secure-field blocked sheet appears with reason text/content.
actual: Field is locked and bottom sheet is shown, but sheet content is empty.
errors: None reported; visual/content failure.
reproduction: Run UAT test 1 for privacy/safety controls and trigger secure-field blocked state.
started: Reported during phase 01 UAT.

## Eliminated

- hypothesis: Reason text is not being passed into blocked sheet state.
  evidence: KeyboardScreen directly renders title/description/reason from literals/resources when externalSendingBlocked is true; no nullable content model is used.
  timestamp: 2026-02-26T18:00:37Z
- hypothesis: Theme/resources made text transparent or empty.
  evidence: strings.xml contains non-empty secure-field strings; theme files do not set zero-alpha/zero-size text behavior.
  timestamp: 2026-02-26T18:05:56Z

## Evidence

- timestamp: 2026-02-26T18:00:13Z
  checked: .planning/phases/01-privacy-and-safety-controls/01-UAT.md
  found: Test 1 reports locked action works but explanation sheet is empty; severity marked major.
  implication: Blocking gate triggers correctly; failure is in explanation-sheet content rendering/data.
- timestamp: 2026-02-26T18:00:37Z
  checked: WhisperInputService.kt and KeyboardScreen.kt secure-field block flow
  found: externalSendBlockedReason/externalSendBlockedByAppPolicy correctly drive locked state; secure-field ModalBottomSheet contains non-empty title, description, reason, and button text.
  implication: Empty sheet is not caused by missing reason data propagation.
- timestamp: 2026-02-26T18:01:38Z
  checked: ui/theme/Theme.kt, Color.kt, Type.kt, and values/themes.xml
  found: No custom typography or theme color overrides set text alpha/size to zero; Material3 defaults should render text normally.
  implication: Empty rendering is unlikely to be a simple color/resource configuration defect.
- timestamp: 2026-02-26T18:05:56Z
  checked: WhisperInputService.onCreateInputView + KeyboardScreen bottom-sheet composition
  found: IME UI host ComposeView uses WRAP_CONTENT height and the primary keyboard surface is fixed to 72dp; blocked explanation relies on ModalBottomSheet in this constrained host.
  implication: The blocked-sheet component choice/host constraint combination is the likely reason the sheet appears with no visible content in UAT.
- timestamp: 2026-02-26T18:05:56Z
  checked: UAT failed cases 1 and 5
  found: Both secure-field and app-policy failures report identical symptom: lock shown, bottom sheet empty.
  implication: Shared UI path (blocked explanation sheet) is the common failure point, not secure-field reason calculation.
- timestamp: 2026-02-26T18:06:16Z
  checked: External reference (StackOverflow 77983141 via search summary)
  found: Compose ModalBottomSheet + InputMethodService context is a known problematic combination for sheet content visibility/resize behavior.
  implication: Observed UAT symptom matches known platform/UI-host limitation pattern.

## Resolution

root_cause: 
  Blocked explanation UX uses Material3 ModalBottomSheet inside IME InputMethodService keyboard UI where host layout is tightly constrained (ComposeView WRAP_CONTENT + 72dp keyboard surface), causing sheet content to render effectively empty in this context.
  Reason/state plumbing is intact; failure is presentation-layer behavior of the shared blocked-sheet component.
fix: 
verification: 
files_changed: []
