package com.github.shekohex.whisperpp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.Spacing

@Composable
fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = AppShapes.card,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
        placeholder = { Text(stringResource(R.string.history_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.history_search_clear))
                }
            }
        },
    )
}

/** Shown above the list when recording history is switched off but old entries remain. */
@Composable
fun HistoryDisabledBanner(onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.history_disabled_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.history_disabled_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onEnable) { Text(stringResource(R.string.history_enable)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryDisabledBannerPreview() {
    PreviewSurface { HistoryDisabledBanner(onEnable = {}, modifier = Modifier.padding(Spacing.lg)) }
}
