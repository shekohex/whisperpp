package com.github.shekohex.whisperpp.ui.dictionary

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PageScaffold

/** Dictionary settings as a page, opened from Privacy & Safety's Auto-Learn note. */
@Composable
fun DictionarySettingsScreen(onBack: () -> Unit) {
    PageScaffold(
        title = stringResource(R.string.dictionary_settings_title),
        onBack = onBack,
        largeTitle = false,
    ) {
        DictionarySettingsPane(onReviewNow = {}, modifier = Modifier.fillMaxSize())
    }
}
