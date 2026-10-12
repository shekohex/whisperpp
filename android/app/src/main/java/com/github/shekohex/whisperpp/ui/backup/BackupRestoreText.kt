package com.github.shekohex.whisperpp.ui.backup

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.ClearedSelection
import com.github.shekohex.whisperpp.data.ImportAnalysis
import com.github.shekohex.whisperpp.data.RestoreMode
import com.github.shekohex.whisperpp.data.RestoreRepairArea
import com.github.shekohex.whisperpp.data.RestoreRepairEntry
import com.github.shekohex.whisperpp.data.RestoreSelectionType
import com.github.shekohex.whisperpp.data.RestoreWarning
import com.github.shekohex.whisperpp.data.RestoreWarningKind
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_ADVANCED_PREFERENCES
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_DICTIONARY
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_HISTORY
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_KEYBOARD_BEHAVIOR
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_MODES
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS
import com.github.shekohex.whisperpp.data.SETTINGS_BACKUP_CATEGORY_TRANSFORM_PRESETS
import com.github.shekohex.whisperpp.data.SkippedImportItem

// The repository keeps English text in its models (it is also serialized into backup files), so the
// UI maps the typed fields (category ids, warning kinds, repair areas) to localized resources here.

private val CATEGORY_LABELS: Map<String, Int> = mapOf(
    SETTINGS_BACKUP_CATEGORY_PROVIDERS_MODELS to R.string.backup_category_providers_models,
    SETTINGS_BACKUP_CATEGORY_PROVIDER_CREDENTIALS to R.string.backup_category_provider_credentials,
    SETTINGS_BACKUP_CATEGORY_ACTIVE_SELECTIONS to R.string.backup_category_active_selections,
    SETTINGS_BACKUP_CATEGORY_LANGUAGE_DEFAULTS to R.string.backup_category_language_defaults,
    SETTINGS_BACKUP_CATEGORY_PROMPTS_PROFILES to R.string.backup_category_prompts_profiles,
    SETTINGS_BACKUP_CATEGORY_APP_MAPPINGS to R.string.backup_category_app_mappings,
    SETTINGS_BACKUP_CATEGORY_TRANSFORM_PRESETS to R.string.backup_category_transform_presets,
    SETTINGS_BACKUP_CATEGORY_KEYBOARD_BEHAVIOR to R.string.backup_category_keyboard_behavior,
    SETTINGS_BACKUP_CATEGORY_PRIVACY_SAFETY to R.string.backup_category_privacy_safety,
    SETTINGS_BACKUP_CATEGORY_ADVANCED_PREFERENCES to R.string.backup_category_advanced_preferences,
    SETTINGS_BACKUP_CATEGORY_DICTIONARY to R.string.backup_category_dictionary,
    SETTINGS_BACKUP_CATEGORY_MODES to R.string.backup_category_modes,
    SETTINGS_BACKUP_CATEGORY_HISTORY to R.string.backup_category_history,
)

/** Localized label for a backup category; unknown ids (newer backups) fall back to [fallback]. */
@Composable
internal fun backupCategoryLabel(categoryId: String, fallback: String = categoryId): String =
    CATEGORY_LABELS[categoryId]?.let { stringResource(it) } ?: fallback

@StringRes
internal fun restoreSelectionLabel(type: RestoreSelectionType): Int = when (type) {
    RestoreSelectionType.ACTIVE_STT -> R.string.backup_selection_dictation
    RestoreSelectionType.ACTIVE_TEXT -> R.string.backup_selection_enhancement
    RestoreSelectionType.COMMAND_TEXT -> R.string.backup_selection_command
}

@StringRes
internal fun restoreModeLabel(mode: RestoreMode): Int = when (mode) {
    RestoreMode.OVERWRITE -> R.string.backup_mode_overwrite
    RestoreMode.MERGE -> R.string.backup_mode_merge
}

@Composable
internal fun restoreWarningText(warning: RestoreWarning, analysis: ImportAnalysis): String = when (warning.kind) {
    RestoreWarningKind.NEWER_APP_VERSION ->
        stringResource(R.string.backup_warning_newer_app, analysis.backupAppVersionName)
    RestoreWarningKind.OLDER_APP_VERSION ->
        stringResource(R.string.backup_warning_older_app, analysis.backupAppVersionName)
    RestoreWarningKind.NEWER_SCHEMA_VERSION -> stringResource(R.string.backup_warning_newer_format)
    RestoreWarningKind.OLDER_SCHEMA_VERSION -> stringResource(R.string.backup_warning_older_format)
}

/**
 * Skipped items keep the repository's technical reason (English, from the file parser) after the
 * localized category label.
 */
@Composable
internal fun skippedItemText(skipped: SkippedImportItem): String {
    val label = backupCategoryLabel(skipped.categoryId)
    return skipped.itemKey?.let { stringResource(R.string.backup_skipped_item_with_key, label, it, skipped.reason) }
        ?: stringResource(R.string.backup_skipped_item, label, skipped.reason)
}

/** Provider ids whose API key is missing after the restore. */
internal fun missingCredentialProviderIds(checklist: List<RestoreRepairEntry>): Set<String> =
    checklist.filter { it.area == RestoreRepairArea.PROVIDER_CREDENTIALS }.mapNotNull { it.providerId }.toSet()

@Composable
internal fun clearedSelectionText(
    cleared: ClearedSelection,
    missingCredentials: Set<String>,
    providerNames: Map<String, String>,
): String {
    val selection = stringResource(restoreSelectionLabel(cleared.selectionType))
    val reason = if (cleared.providerId in missingCredentials) {
        stringResource(R.string.backup_cleared_missing_key, providerNames[cleared.providerId] ?: cleared.providerId)
    } else {
        stringResource(R.string.backup_cleared_unavailable)
    }
    return stringResource(R.string.backup_summary_entry, selection, reason)
}

@Composable
internal fun repairEntryText(entry: RestoreRepairEntry, missingCredentials: Set<String>): String {
    val providerName = entry.providerName ?: entry.providerId.orEmpty()
    val selectionType = entry.selectionType
    return when {
        entry.area == RestoreRepairArea.PROVIDER_CREDENTIALS || selectionType == null ->
            stringResource(R.string.backup_repair_reenter_key, providerName)
        entry.providerId != null && entry.providerId in missingCredentials ->
            stringResource(R.string.backup_repair_key_then_select, providerName, stringResource(restoreSelectionLabel(selectionType)))
        else -> stringResource(R.string.backup_repair_choose_selection, stringResource(restoreSelectionLabel(selectionType)))
    }
}
