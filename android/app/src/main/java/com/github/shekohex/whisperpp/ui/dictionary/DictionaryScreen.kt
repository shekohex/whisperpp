package com.github.shekohex.whisperpp.ui.dictionary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.EmptyState
import com.github.shekohex.whisperpp.ui.settings.common.TopLevelPage

/** Dictionary tab placeholder until the dictionary UI WP lands. */
@Composable
fun DictionaryScreen() {
    TopLevelPage(title = stringResource(R.string.nav_dictionary)) { modifier ->
        EmptyState(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.placeholder_coming_soon_title),
            body = stringResource(R.string.dictionary_placeholder_message),
            modifier = modifier,
        )
    }
}
