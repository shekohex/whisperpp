---
status: diagnosed
trigger: "Diagnose UAT issue #2 root cause (do not implement fix). Issue: Test 5 expected per-app blocked sheet explaining app policy. User reported: 'it is locked, but the bottom sheet is empty'"
created: 2026-02-26T18:00:02Z
updated: 2026-02-26T18:05:38Z
---

## Current Focus

hypothesis: Confirmed: blocked explanation sheet relies on default ModalBottomSheet state, which can render collapsed/partially-expanded in IME-constrained layout and appear empty.
test: Check blocked-sheet configuration and bottom-sheet state usage across codebase.
expecting: No explicit expansion/skip-partial configuration for blocked sheet.
next_action: Return root-cause diagnosis and required fix direction (no implementation).

## Symptoms

expected: Per-app blocked sheet appears and explains app policy when an app is locked/blocked.
actual: App is locked, but bottom sheet appears empty.
errors: None reported by user.
reproduction: Execute UAT Test 5 flow for blocked app policy sheet; observe empty bottom sheet.
started: Reported during Phase 01 UAT.

## Eliminated

- hypothesis: Shared secure-field sheet strings are empty in default resources.
  evidence: values/strings.xml contains non-empty values for secure_field_sheet_title, secure_field_sheet_description, and secure_field_reason_*.
  timestamp: 2026-02-26T18:01:27Z

- hypothesis: Locale-specific resources override secure-field sheet strings to empty values.
  evidence: Search across res/**/strings*.xml found secure-field keys only in values/strings.xml.
  timestamp: 2026-02-26T18:01:44Z

- hypothesis: Theme color scheme makes secure-field bottom sheet text invisible.
  evidence: Theme uses standard Material3 dynamic/light/dark color schemes; no custom transparent/identical text-surface assignments found.
  timestamp: 2026-02-26T18:02:31Z

## Evidence

- timestamp: 2026-02-26T18:00:15Z
  checked: .planning/phases/01-privacy-and-safety-controls/01-UAT.md
  found: Test 5 and Test 1 both report same symptom: action is correctly locked but explanation bottom sheet is empty.
  implication: Enforcement/locking path likely works; failure is isolated to sheet content population.

- timestamp: 2026-02-26T18:00:30Z
  checked: Code search across android sources
  found: Blocked-state UI is composed in KeyboardScreen with distinct branch for externalSendBlockedByAppPolicy, while block enforcement state comes from WhisperInputService.
  implication: Root cause likely in KeyboardScreen presentation branch rather than policy enforcement logic.

- timestamp: 2026-02-26T18:00:52Z
  checked: android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt
  found: App-policy bottom sheet path has explicit hardcoded title/description/reason text and open-settings button; no obvious empty-string mapping in this file.
  implication: Empty sheet symptom likely comes from runtime state/timing or style/visibility issue outside static copy definitions.

- timestamp: 2026-02-26T18:01:09Z
  checked: android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt
  found: Blocked-state props are passed directly to KeyboardScreen; enforcement state is refreshed in shouldBlockExternalSend() and input lifecycle callbacks.
  implication: If sheet is empty in both secure-field and app-policy tests, a shared presentation dependency (e.g., resources/theme) is a stronger suspect than lock enforcement logic.

- timestamp: 2026-02-26T18:01:27Z
  checked: android/app/src/main/res/values/strings.xml
  found: All secure-field sheet and reason strings in default resources are populated with non-empty text.
  implication: Empty sheet is not caused by missing default string values.

- timestamp: 2026-02-26T18:01:44Z
  checked: Search in android/app/src/main/res/**/strings*.xml
  found: No locale-specific overrides exist for secure-field sheet/reason strings.
  implication: Blank UI is not caused by translation resource overrides.

- timestamp: 2026-02-26T18:02:31Z
  checked: ui/theme/Theme.kt and ui/theme/Color.kt
  found: Color scheme is Material3 default/dynamic; no explicit styling that should make all bottom-sheet text invisible.
  implication: Root cause more likely in logic/path selection than global theming.

- timestamp: 2026-02-26T18:02:59Z
  checked: privacy/SecureFieldDetector.kt
  found: Detector can block fields with IME_FLAG_NO_PERSONALIZED_LEARNING in addition to password/OTP heuristics.
  implication: Lock icon can appear for reasons unrelated to per-app policy, but this still does not directly explain empty sheet content.

- timestamp: 2026-02-26T18:03:31Z
  checked: .planning/phases/01-privacy-and-safety-controls docs (UAT/plan/summary/verification)
  found: Planned and verified behavior expects blocked sheet to explain policy reason, matching current code intent, but no artifact addresses modal sizing/state in IME.
  implication: Failure likely in presentation mechanics rather than missing high-level feature wiring.

- timestamp: 2026-02-26T18:04:59Z
  checked: ui/settings/PrivacySafetyScreen.kt and privacy/SendPolicyRepository.kt
  found: Per-app block rules are normalized, persisted, and read by package name as expected; this path does not explain empty bottom-sheet content.
  implication: Test 5 failure is more consistent with explanation-sheet rendering behavior than policy storage logic.

- timestamp: 2026-02-26T18:05:38Z
  checked: KeyboardScreen.kt and project-wide search for bottom-sheet state APIs
  found: Blocked explanation sheet uses ModalBottomSheet defaults with no explicit SheetState / skipPartiallyExpanded control; no rememberModalBottomSheetState usage exists in codebase.
  implication: In IME-sized host windows, default partial-sheet behavior can present as an empty sheet despite non-empty content definitions.

## Resolution

root_cause: "Blocked explanation modal uses default Material3 ModalBottomSheet state in an InputMethodService host. Without explicit full expansion (or partial-skip), sheet can open in a clipped/partial state that appears empty in UAT while lock enforcement still works."
fix: "Use explicit bottom-sheet state for blocked explanation (e.g., rememberModalBottomSheetState(skipPartiallyExpanded = true) and show expanded state) and ensure content is visible in IME constraints."
verification: "Diagnosis-only mode; no code changes applied."
files_changed: []
