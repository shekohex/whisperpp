package com.github.shekohex.whisperpp.ui.settings.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.ui.navigation.Destination

/** One row of a hub tab (Modes / More) that opens [destination]. */
data class HubEntry(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val destination: Destination,
)

/** Large-title scaffold for top-level tabs; the bottom bar / rail is drawn by the nav host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelPage(
    title: String,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = { LargeTopAppBar(title = { Text(title) }) },
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        content(Modifier.fillMaxSize().padding(padding))
    }
}

/** A titled group of [HubEntry] rows. */
data class HubSection(val title: String, val entries: List<HubEntry>)

/** A tab body listing [entries] in one group titled [groupTitle], with an optional [intro] paragraph. */
@Composable
fun HubEntryList(
    groupTitle: String,
    entries: List<HubEntry>,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    intro: String? = null,
) {
    HubSectionList(listOf(HubSection(groupTitle, entries)), onNavigate, modifier, intro)
}

/** A tab body with one settings group per [sections] entry, with an optional [intro] paragraph. */
@Composable
fun HubSectionList(
    sections: List<HubSection>,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    intro: String? = null,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (intro != null) {
            item {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        sections.forEach { section ->
            item(key = section.title) {
                SettingsGroup(title = section.title) {
                    section.entries.forEach { entry ->
                        SettingsItem(
                            icon = entry.icon,
                            title = stringResource(entry.titleRes),
                            subtitle = stringResource(entry.summaryRes),
                            onClick = { onNavigate(entry.destination) },
                        )
                    }
                }
            }
        }
    }
}
