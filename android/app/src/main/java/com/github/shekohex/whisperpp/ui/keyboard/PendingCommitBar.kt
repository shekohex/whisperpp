package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R

/** Offered when a transcript finished after focus moved to another field (G-07). */
@Composable
fun PendingCommitBar(
    text: String,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.dictation_pending_commit_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.dictation_pending_commit_discard))
                }
                TextButton(onClick = onCopy) {
                    Text(stringResource(R.string.dictation_pending_commit_copy))
                }
                FilledTonalButton(onClick = onInsert) {
                    Text(stringResource(R.string.dictation_pending_commit_insert))
                }
            }
        }
    }
}
