---
status: awaiting_human_verify
trigger: "Investigate issue: mic-empty-bottom-sheet"
created: 2026-03-26T00:00:00Z
updated: 2026-03-26T00:27:00Z
---

## Current Focus

hypothesis: First-use disclosure should now render visibly in the IME host because it no longer relies on ModalBottomSheet
test: Human verification in the real keyboard flow by tapping mic on a build where the first-use disclosure can appear
expecting: Visible disclosure card appears with Continue / Open Privacy & Safety / Cancel, and Continue proceeds into normal recording UI
next_action: wait for end-to-end verification from the user/device

## Symptoms

expected: Tapping the mic icon should open the normal recording UI/state for dictation.
actual: Tapping the mic icon opens an empty bottom sheet with no visible content.
errors: No visible error, toast, snackbar, crash, or log message was reported.
reproduction: Open the keyboard and tap the microphone icon.
started: Started after recent changes.

## Eliminated

## Evidence

- timestamp: 2026-03-26T00:05:00Z
  checked: android codebase bottom-sheet and microphone references
  found: KeyboardScreen.kt contains several ModalBottomSheet usages and is the main keyboard UI entrypoint for mic-related sheet behavior
  implication: The regression is likely in keyboard sheet state/render branching rather than a separate activity or settings screen

- timestamp: 2026-03-26T00:10:00Z
  checked: KeyboardScreen.kt full mic and bottom-sheet composition
  found: Normal recording UI is inline, not sheet-based; a Ready-state mic tap can only surface a sheet through gated flows such as blocked explanation, first-use disclosure, or command-mode clipboard/preset dialogs
  implication: The empty bottom sheet is not the recording UI itself; it is a pre-recording modal path triggered before recording begins

- timestamp: 2026-03-26T00:10:00Z
  checked: existing debug sessions uat1-empty-blocked-sheet and uat2-empty-blocked-sheet
  found: prior diagnoses identified Material3 ModalBottomSheet in the IME host as a known source of empty/clipped sheet presentation for the blocked-explanation flow
  implication: any newly added/default-state ModalBottomSheet in KeyboardScreen is a strong suspect for the same symptom

- timestamp: 2026-03-26T00:18:00Z
  checked: WhisperInputService.onMicAction and first-use disclosure flow
  found: a Ready-state mic tap now awaits first-use dictation disclosure before recording; the disclosure is assigned to firstUseDisclosure state and rendered in KeyboardScreen before any recording state begins
  implication: the mic regression is a pre-recording disclosure path, not a failure in dictation state transitions

- timestamp: 2026-03-26T00:18:00Z
  checked: git history for WhisperInputService.kt and KeyboardScreen.kt
  found: commit 4fbf313 ("feat(01-05): gate first-use sends and wire verbose log preference") introduced the first-use dictation disclosure and rendered it with a plain ModalBottomSheet in KeyboardScreen
  implication: the reported timeline matches the first-use disclosure change and directly ties the symptom to a new IME-hosted sheet implementation

- timestamp: 2026-03-26T00:27:00Z
  checked: targeted Gradle verification
  found: `./gradlew :app:testDebugUnitTest --tests com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatterTest :app:assembleDebugAndroidTest` completed successfully
  implication: the modified keyboard code compiles, the new androidTest source compiles into the androidTest artifact, and related disclosure unit coverage remains green

## Resolution

root_cause: First-use dictation disclosure intercepts Ready-state mic taps and renders with a plain Material3 ModalBottomSheet inside the InputMethodService host. In this IME-constrained window, that sheet can present as an empty bottom sheet, so users see a blank modal instead of visible disclosure controls or recording UI.
fix: Replace the first-use disclosure ModalBottomSheet with an IME-safe popup/card overlay and add focused regression coverage for the disclosure content in a constrained host.
verification: 
  - `:app:testDebugUnitTest --tests com.github.shekohex.whisperpp.privacy.PrivacyDisclosureFormatterTest` passed
  - `:app:assembleDebugAndroidTest` passed, compiling the new `FirstUseDisclosureContentUiTest`
  - end-to-end IME interaction still requires device/emulator human verification
files_changed:
  - android/app/src/main/java/com/github/shekohex/whisperpp/ui/keyboard/KeyboardScreen.kt
  - android/app/src/androidTest/java/com/github/shekohex/whisperpp/ui/keyboard/FirstUseDisclosureContentUiTest.kt
