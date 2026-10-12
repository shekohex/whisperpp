package com.github.shekohex.whisperpp.ui.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.WidthPreviews
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.HubEntry
import com.github.shekohex.whisperpp.ui.settings.common.HubSection
import com.github.shekohex.whisperpp.ui.settings.common.HubSectionList
import com.github.shekohex.whisperpp.ui.settings.common.TopLevelPage

private val AI_MODELS_ENTRIES = listOf(
    HubEntry(Icons.Default.Dns, R.string.more_ai_models_title, R.string.more_ai_models_summary, Destination.ProviderList),
    HubEntry(Icons.Default.SettingsSuggest, R.string.more_provider_selections_title, R.string.more_provider_selections_summary, Destination.ProviderSelections),
    HubEntry(Icons.Default.AutoFixHigh, R.string.more_enhancement_title, R.string.more_enhancement_summary, Destination.Enhancement),
)

private val AUDIO_ENTRIES = listOf(
    HubEntry(Icons.AutoMirrored.Filled.VolumeUp, R.string.more_audio_title, R.string.more_audio_summary, Destination.AudioSettings),
    HubEntry(Icons.Default.Keyboard, R.string.more_dictation_title, R.string.more_dictation_summary, Destination.DictationSettings),
)

private val SETTINGS_ENTRIES = listOf(
    HubEntry(Icons.Default.Insights, R.string.more_insights_title, R.string.more_insights_summary, Destination.Insights),
    HubEntry(Icons.Default.Security, R.string.more_privacy_title, R.string.more_privacy_summary, Destination.PrivacySafety),
    HubEntry(Icons.Default.Backup, R.string.more_backup_title, R.string.more_backup_summary, Destination.BackupRestore),
    HubEntry(Icons.Default.Info, R.string.more_about_title, R.string.more_about_summary, Destination.About),
)

/** More tab (§4.8): AI Models, Audio & dictation, Settings. */
@Composable
fun MoreScreen(onNavigate: (Destination) -> Unit) {
    TopLevelPage(title = stringResource(R.string.more_title)) { modifier ->
        HubSectionList(
            sections = listOf(
                HubSection(stringResource(R.string.more_group_ai_models), AI_MODELS_ENTRIES),
                HubSection(stringResource(R.string.more_group_audio), AUDIO_ENTRIES),
                HubSection(stringResource(R.string.more_group_settings), SETTINGS_ENTRIES),
            ),
            onNavigate = onNavigate,
            modifier = modifier,
        )
    }
}

@WidthPreviews
@Composable
private fun MoreScreenPreview() {
    PreviewSurface { MoreScreen(onNavigate = {}) }
}
