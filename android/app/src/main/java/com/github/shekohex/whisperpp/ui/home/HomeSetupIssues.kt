package com.github.shekohex.whisperpp.ui.home

import android.content.res.Resources
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_MODEL_ID
import com.github.shekohex.whisperpp.data.PreferenceKeys.COMMAND_TEXT_PROVIDER_ID
import com.github.shekohex.whisperpp.ui.navigation.Destination

/** Issues for [HomeSetupBanner]; a missing provider / dictation model is covered by the setup checklist. */
internal fun buildSetupIssues(
    summary: HomeViewModel.Summary,
    repairCount: Int,
    resources: Resources,
    onNavigate: (Destination) -> Unit,
): List<HomeSetupIssue> = buildList {
    val validation = summary.validation
    if (summary.providers.isNotEmpty() && !validation.isTextValid) {
        add(
            HomeSetupIssue(
                resources.getString(R.string.home_issue_enhancement),
                resources.getString(R.string.home_issue_enhancement_action),
            ) { onNavigate(Destination.ProviderSelections) },
        )
    }
    val commandOverridePresent = summary.prefs[COMMAND_TEXT_PROVIDER_ID].orEmpty().isNotBlank() ||
        summary.prefs[COMMAND_TEXT_MODEL_ID].orEmpty().isNotBlank()
    if (commandOverridePresent && !validation.isCommandOverrideValid) {
        add(
            HomeSetupIssue(
                resources.getString(R.string.home_issue_command_override),
                resources.getString(R.string.home_issue_command_override_action),
            ) { onNavigate(Destination.ProviderSelections) },
        )
    }
    if (repairCount > 0) {
        add(
            HomeSetupIssue(
                resources.getQuantityString(R.plurals.home_issue_restore_repairs, repairCount, repairCount),
                resources.getString(R.string.home_issue_restore_action),
            ) { onNavigate(Destination.BackupRestore) },
        )
    }
}
