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
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.HubEntry
import com.github.shekohex.whisperpp.ui.settings.common.HubEntryList
import com.github.shekohex.whisperpp.ui.settings.common.TopLevelPage

private val MORE_ENTRIES = listOf(
    HubEntry(Icons.Default.Dns, R.string.more_ai_models_title, R.string.more_ai_models_summary, Destination.ProviderList),
    HubEntry(Icons.Default.SettingsSuggest, R.string.more_provider_selections_title, R.string.more_provider_selections_summary, Destination.ProviderSelections),
    HubEntry(Icons.AutoMirrored.Filled.VolumeUp, R.string.more_audio_title, R.string.more_audio_summary, Destination.AudioSettings),
    HubEntry(Icons.Default.Keyboard, R.string.more_dictation_title, R.string.more_dictation_summary, Destination.DictationSettings),
    HubEntry(Icons.Default.AutoFixHigh, R.string.more_enhancement_title, R.string.more_enhancement_summary, Destination.Enhancement),
    HubEntry(Icons.Default.Security, R.string.more_privacy_title, R.string.more_privacy_summary, Destination.PrivacySafety),
    HubEntry(Icons.Default.Backup, R.string.more_backup_title, R.string.more_backup_summary, Destination.BackupRestore),
    HubEntry(Icons.Default.Insights, R.string.more_insights_title, R.string.more_insights_summary, Destination.Insights),
    HubEntry(Icons.Default.Info, R.string.more_about_title, R.string.more_about_summary, Destination.About),
)

/** More tab: every settings area that is not its own tab. */
@Composable
fun MoreScreen(onNavigate: (Destination) -> Unit) {
    TopLevelPage(title = stringResource(R.string.more_title)) { modifier ->
        HubEntryList(
            groupTitle = stringResource(R.string.more_title),
            entries = MORE_ENTRIES,
            onNavigate = onNavigate,
            modifier = modifier,
        )
    }
}
