---
status: diagnosed
phase: 01-privacy-and-safety-controls
source:
  - 01-01-SUMMARY.md
  - 01-02-SUMMARY.md
  - 01-03-SUMMARY.md
  - 01-04-SUMMARY.md
  - 01-05-SUMMARY.md
started: 2026-02-26T17:39:08Z
updated: 2026-02-26T18:06:40Z
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
  root_cause: "Blocked explanation UI uses ModalBottomSheet in IME-constrained host and opens in a clipped/partial presentation, so content appears empty while block state itself is correct."
  artifacts:
    - path: "android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt"
      issue: "Blocked explanation rendered via ModalBottomSheet without explicit expanded sheet-state handling in IME host."
    - path: "android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt"
      issue: "Block gating state is correctly wired; failure is in shared explanation presentation path."
  missing:
    - "Use explicit bottom-sheet state (skipPartiallyExpanded=true) and force expanded/open behavior for blocked explanation."
    - "Provide IME-safe explanation container fallback if sheet host still clips content."
    - "Add regression coverage for secure-field blocked explanation content visibility."
  debug_session: ".planning/debug/uat1-empty-blocked-sheet.md"
- truth: "After blocking an app in Privacy & Safety, Whisper++ refuses external sending in that app and explains that it is blocked by app policy."
  status: failed
  reason: "User reported: it is locked, but the bottom sheet is empty."
  severity: major
  test: 5
  root_cause: "Same shared blocked-sheet rendering defect as secure-field flow: app-policy block state is set, but explanation ModalBottomSheet presentation in IME appears empty."
  artifacts:
    - path: "android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt"
      issue: "App-policy and secure-field explanation share the same blocked-sheet UI path."
    - path: "android/app/src/main/res/values/strings.xml"
      issue: "Explanation strings exist; missing text is not caused by empty resources."
  missing:
    - "Apply explicit expanded sheet-state handling for app-policy blocked explanation UI."
    - "Add regression coverage for app-policy blocked explanation content visibility."
  debug_session: ".planning/debug/uat2-empty-blocked-sheet.md"
