package com.github.shekohex.whisperpp.ui.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.settings.common.TopLevelPage

/** History tab placeholder until the transcript history WP lands. */
@Composable
fun HistoryScreen() {
    TopLevelPage(title = stringResource(R.string.nav_history)) { modifier ->
        EmptyState(
            icon = Icons.Default.History,
            title = stringResource(R.string.placeholder_coming_soon_title),
            body = stringResource(R.string.history_placeholder_message),
            modifier = modifier,
        )
    }
}
