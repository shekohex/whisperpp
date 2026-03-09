# Phase 6: Settings UX + Import/Export - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver a polished Material 3 settings experience where users can review, change, back up, and restore all existing Whisper++ behavior. This phase covers how existing settings are organized, surfaced, exported, and restored; it does not add new product capabilities.

</domain>

<decisions>
## Implementation Decisions

### Restore behavior
- Import must offer a clear choice between overwrite and merge each time.
- The overwrite/merge choice should be presented in a bottom sheet immediately after file selection, then followed by a preview and explicit confirmation before applying changes.
- Merge should support including or excluding whole categories before apply.
- For merge conflicts, imported values win by default.
- If the import file is partly invalid or unsupported, restore valid parts and show a report of skipped items.
- After restore, keep the user in settings and show a completion summary.
- If imported changes make active selections invalid on the current device, clear those selections and explain what needs attention.

### Settings home structure
- Settings home should feel like grouped section cards, not a flat utility list or analytics dashboard.
- Navigation should use nested groups first rather than exposing every major area directly on the top-level screen.
- Each top-level row/card should show one concise status line.
- Setup-critical items should appear first.

### Backup contents
- Backups should be password-encrypted and required for every export; there is no plain export path.
- Export requires password entry plus confirmation.
- Encrypted backups may include sensitive/provider data.
- Import should request the password immediately after file pick, before preview/apply.
- Export must show exactly which categories are included, including sensitive contents.
- Backup identity should include app version and export timestamp.
- Cross-version imports should be attempted with warnings, not blocked outright.
- If credentials are missing or unusable after import, show a repair checklist pointing to the affected providers/selections.

### Guidance and feedback
- Incomplete setup or broken selections should surface as a persistent banner on settings home.
- Unconfigured sections should use short, action-oriented guidance copy.
- Major actions like export/import/save should show an inline summary plus toast feedback.
- The top-bar help affordance should provide contextual tips for the current settings area.

### OpenCode's Discretion
- Exact Material 3 component choices, spacing, and theming details.
- Exact labels for nested groups and section-card presentation.
- Exact preview/report layout for import summaries, skipped-item reports, and repair checklist screens.

</decisions>

<specifics>
## Specific Ideas

- Restore mode choice should feel quick and explicit via bottom sheet, then move into a preview-and-confirm flow.
- Settings home should feel polished and grouped instead of like a long undifferentiated list.
- Backup/export UX should be transparent about included categories and sensitive contents.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope.

</deferred>

---

*Phase: 06-settings-ux-import-export*
*Context gathered: 2026-03-09*
