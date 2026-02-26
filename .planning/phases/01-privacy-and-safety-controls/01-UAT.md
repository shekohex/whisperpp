---
status: complete
phase: 01-privacy-and-safety-controls
source:
  - 01-01-SUMMARY.md
  - 01-02-SUMMARY.md
  - 01-03-SUMMARY.md
  - 01-04-SUMMARY.md
  - 01-05-SUMMARY.md
started: 2026-02-26T17:39:08Z
updated: 2026-02-26T17:59:13Z
---

## Current Test

[testing complete]

## Tests

### 1. Secure field blocks external sending
expected: In a password/OTP-like field, the main action is locked/disabled and external sending does not start. Tapping the locked action opens an explanation sheet with the block reason.
result: issue
reported: "Yes, it is locked, but the explination bottom sheet appears and it is empty! no text or conent in it."
severity: major

### 2. Entering secure field stops active send immediately
expected: If dictation/Smart Fix is active and focus moves to a secure field, sending stops immediately and a short secure-field notice appears.
result: pass

### 3. Provider API keys are masked in settings
expected: Provider edit UI does not prefill existing API key values; it only shows key-set state and last4. Replace/Clear key actions work.
result: pass

### 4. Exported settings do not contain API keys
expected: Exported settings payload excludes provider API keys and secret preference keys.
result: pass

### 5. Per-app block policy is enforced
expected: After blocking an app in Privacy & Safety, Whisper++ refuses external sending in that app and explains that it is blocked by app policy.
result: issue
reported: "it is locked, but the bottom sheet is empty."
severity: major

### 6. Privacy disclosures show sent data and endpoints
expected: Privacy & Safety disclosure section shows what data is sent (audio/text) and the target provider base URL + endpoint path for each mode.
result: pass

### 7. First-use dictation disclosure gates sending
expected: On first dictation send, a disclosure modal appears with Continue/Cancel/Open Privacy & Safety; dictation starts only after Continue.
result: pass

### 8. First-use enhancement disclosure gates Smart Fix
expected: On first enhancement send, a disclosure modal appears; declining prevents Smart Fix send and dictation falls back to raw transcript insertion.
result: pass

## Summary

total: 8
passed: 6
issues: 2
pending: 0
skipped: 0

## Gaps

- truth: "In a password/OTP-like field, the main action is locked/disabled and external sending does not start. Tapping the locked action opens an explanation sheet with the block reason."
  status: failed
  reason: "User reported: Yes, it is locked, but the explination bottom sheet appears and it is empty! no text or conent in it."
  severity: major
  test: 1
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
- truth: "After blocking an app in Privacy & Safety, Whisper++ refuses external sending in that app and explains that it is blocked by app policy."
  status: failed
  reason: "User reported: it is locked, but the bottom sheet is empty."
  severity: major
  test: 5
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
