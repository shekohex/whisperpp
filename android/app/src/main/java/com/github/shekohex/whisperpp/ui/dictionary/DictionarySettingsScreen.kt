package com.github.shekohex.whisperpp.ui.dictionary

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.settings.common.DetailTopBar

/** Dictionary settings as a page, opened from Privacy & Safety's Auto-Learn note. */
@Composable
fun DictionarySettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { DetailTopBar(title = stringResource(R.string.dictionary_settings_title), onBack = onBack) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        DictionarySettingsPane(onReviewNow = {}, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
