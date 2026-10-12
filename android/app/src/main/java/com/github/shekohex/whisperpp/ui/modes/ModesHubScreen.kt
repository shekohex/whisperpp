package com.github.shekohex.whisperpp.ui.modes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.HubEntry
import com.github.shekohex.whisperpp.ui.settings.common.HubEntryList
import com.github.shekohex.whisperpp.ui.settings.common.TopLevelPage

private val MODES_ENTRIES = listOf(
    HubEntry(Icons.Default.AutoFixHigh, R.string.modes_presets_title, R.string.modes_presets_summary, Destination.Presets),
    HubEntry(Icons.Default.Apps, R.string.modes_app_mappings_title, R.string.modes_app_mappings_summary, Destination.AppMappings),
    HubEntry(Icons.Default.Translate, R.string.modes_language_defaults_title, R.string.modes_language_defaults_summary, Destination.LanguageDefaults),
)

/** Modes tab: a hub over presets, per-app mappings and per-language defaults until WP-6 adds real modes. */
@Composable
fun ModesHubScreen(onNavigate: (Destination) -> Unit) {
    TopLevelPage(title = stringResource(R.string.modes_title)) { modifier ->
        HubEntryList(
            groupTitle = stringResource(R.string.modes_title),
            entries = MODES_ENTRIES,
            onNavigate = onNavigate,
            intro = stringResource(R.string.modes_hub_intro),
            modifier = modifier,
        )
    }
}
