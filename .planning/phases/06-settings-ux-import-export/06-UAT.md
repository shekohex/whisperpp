---
status: complete
phase: 06-settings-ux-import-export
source:
  - 06-01-SUMMARY.md
  - 06-02-SUMMARY.md
  - 06-03-SUMMARY.md
  - 06-04-SUMMARY.md
started: 2026-03-09T20:35:29Z
updated: 2026-03-09T20:50:30Z
---

## Current Test

[testing complete]

## Tests

### 1. Settings home overview and setup status
expected: Opening Settings lands on a grouped home screen. Setup-critical guidance is shown first (either a setup banner with fix actions or a ready state), each main settings card shows a short one-line status, and Backup & restore appears as its own secondary settings area rather than loose actions on the home screen.
result: pass

### 2. Contextual help follows the current settings screen
expected: Tapping the help action on Settings home opens a help sheet for the home overview, and tapping help inside Backup & restore opens backup-specific guidance instead of generic or empty content.
result: pass

### 3. Backup export is password-gated and updates home status
expected: Starting an export first asks for a password and confirmation. Blank or mismatched passwords show inline validation, and a successful export writes an encrypted backup file and surfaces a recent export status when you return to Settings home.
result: pass

### 4. Restore requires file pick, mode choice, password, and preview before apply
expected: Starting restore always goes through file selection, then Overwrite or Merge choice, then password entry, then a preview screen before any settings are changed. Wrong password or unreadable backup shows an error instead of applying anything.
result: pass

### 5. Merge restore lets you include categories before confirming
expected: In Merge mode, the restore preview lets you keep or exclude whole backup categories before restore. Any warnings or skipped items are visible in the preview, and restore confirmation only becomes possible after at least one category stays selected.
result: pass

### 6. Restore completion shows follow-up actions and flows back to home
expected: After restore finishes, the Backup & restore screen shows a completion summary with skipped items and any repair actions. Returning to Settings home shows the latest restore status and any repair-needed banner, and repair actions route to the relevant settings screen.
result: pass

## Summary

total: 6
passed: 6
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
