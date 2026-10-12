package com.github.shekohex.whisperpp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.Spacing

const val ATTENTION_CARD_TAG = "home_recordings_attention"

/** "2 recordings need attention": failed takes that are still saved; opens History filtered to them. */
@Composable
fun RecordingsAttentionCard(count: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .testTag(ATTENTION_CARD_TAG),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = AppShapes.card,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = pluralStringResource(R.plurals.home_attention_title, count, count),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.home_attention_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun RecordingsAttentionCardPreview() {
    PreviewSurface {
        RecordingsAttentionCard(count = 2, onOpen = {}, modifier = Modifier.padding(Spacing.lg))
    }
}
