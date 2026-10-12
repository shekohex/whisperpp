package com.github.shekohex.whisperpp.ui.settings.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R

private data class SettingsHelpContent(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val tips: List<Int>,
)

private fun settingsHelpContent(topic: SettingsHelpTopic): SettingsHelpContent {
    return when (topic) {
        SettingsHelpTopic.HOME -> SettingsHelpContent(
            title = R.string.settings_help_home_title,
            summary = R.string.settings_help_home_summary,
            tips = listOf(
                R.string.settings_help_home_tip1,
                R.string.settings_help_home_tip2,
                R.string.settings_help_home_tip3,
            ),
        )

        SettingsHelpTopic.PROVIDERS -> SettingsHelpContent(
            title = R.string.settings_help_providers_title,
            summary = R.string.settings_help_providers_summary,
            tips = listOf(
                R.string.settings_help_providers_tip1,
                R.string.settings_help_providers_tip2,
                R.string.settings_help_providers_tip3,
            ),
        )

        SettingsHelpTopic.PROVIDER_SELECTIONS -> SettingsHelpContent(
            title = R.string.settings_help_provider_selections_title,
            summary = R.string.settings_help_provider_selections_summary,
            tips = listOf(
                R.string.settings_help_provider_selections_tip1,
                R.string.settings_help_provider_selections_tip2,
                R.string.settings_help_provider_selections_tip3,
            ),
        )

        SettingsHelpTopic.LANGUAGE_DEFAULTS -> SettingsHelpContent(
            title = R.string.settings_help_language_defaults_title,
            summary = R.string.settings_help_language_defaults_summary,
            tips = listOf(
                R.string.settings_help_language_defaults_tip1,
                R.string.settings_help_language_defaults_tip2,
                R.string.settings_help_language_defaults_tip3,
            ),
        )

        SettingsHelpTopic.PROMPTS_PROFILES -> SettingsHelpContent(
            title = R.string.settings_help_prompts_profiles_title,
            summary = R.string.settings_help_prompts_profiles_summary,
            tips = listOf(
                R.string.settings_help_prompts_profiles_tip1,
                R.string.settings_help_prompts_profiles_tip2,
                R.string.settings_help_prompts_profiles_tip3,
            ),
        )

        SettingsHelpTopic.APP_MAPPINGS -> SettingsHelpContent(
            title = R.string.settings_help_app_mappings_title,
            summary = R.string.settings_help_app_mappings_summary,
            tips = listOf(
                R.string.settings_help_app_mappings_tip1,
                R.string.settings_help_app_mappings_tip2,
                R.string.settings_help_app_mappings_tip3,
            ),
        )

        SettingsHelpTopic.PRESETS -> SettingsHelpContent(
            title = R.string.settings_help_presets_title,
            summary = R.string.settings_help_presets_summary,
            tips = listOf(
                R.string.settings_help_presets_tip1,
                R.string.settings_help_presets_tip2,
                R.string.settings_help_presets_tip3,
            ),
        )

        SettingsHelpTopic.DICTATION -> SettingsHelpContent(
            title = R.string.settings_help_dictation_title,
            summary = R.string.settings_help_dictation_summary,
            tips = listOf(
                R.string.settings_help_dictation_tip1,
                R.string.settings_help_dictation_tip2,
                R.string.settings_help_dictation_tip3,
            ),
        )

        SettingsHelpTopic.ENHANCEMENT -> SettingsHelpContent(
            title = R.string.settings_help_enhancement_title,
            summary = R.string.settings_help_enhancement_summary,
            tips = listOf(
                R.string.settings_help_enhancement_tip1,
                R.string.settings_help_enhancement_tip2,
                R.string.settings_help_enhancement_tip3,
            ),
        )

        SettingsHelpTopic.PRIVACY_SAFETY -> SettingsHelpContent(
            title = R.string.settings_help_privacy_safety_title,
            summary = R.string.settings_help_privacy_safety_summary,
            tips = listOf(
                R.string.settings_help_privacy_safety_tip1,
                R.string.settings_help_privacy_safety_tip2,
                R.string.settings_help_privacy_safety_tip3,
            ),
        )

        SettingsHelpTopic.BACKUP_RESTORE -> SettingsHelpContent(
            title = R.string.settings_help_backup_restore_title,
            summary = R.string.settings_help_backup_restore_summary,
            tips = listOf(
                R.string.settings_help_backup_restore_tip1,
                R.string.settings_help_backup_restore_tip2,
                R.string.settings_help_backup_restore_tip3,
            ),
        )

        SettingsHelpTopic.ANALYTICS -> SettingsHelpContent(
            title = R.string.settings_help_analytics_title,
            summary = R.string.settings_help_analytics_summary,
            tips = listOf(
                R.string.settings_help_analytics_tip1,
                R.string.settings_help_analytics_tip2,
                R.string.settings_help_analytics_tip3,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHelpAction(topic: SettingsHelpTopic) {
    var showSheet by rememberSaveable(topic) { mutableStateOf(false) }
    val help = settingsHelpContent(topic)

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(help.title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                }

                Text(
                    text = stringResource(help.summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    help.tips.forEach { tip ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                            Text(
                                text = stringResource(tip),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    IconButton(onClick = { showSheet = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = stringResource(R.string.settings_help_action),
        )
    }
}
