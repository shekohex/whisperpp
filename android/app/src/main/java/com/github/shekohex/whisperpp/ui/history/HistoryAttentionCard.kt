package com.github.shekohex.whisperpp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.ui.theme.Spacing

/**
 * What is wrong with a take and what to do about it: the status with its reason, Retry (the saved
 * recording is transcribed again without the keyboard) with progress and cancel, and "mark as
 * resolved" for a failed take that already has its text. Nothing for a plain success.
 */
@Composable
internal fun HistoryAttentionCard(
    entry: TranscriptHistoryEntry,
    state: HistoryDetailViewModel.UiState,
    onRetry: () -> Unit,
    onCancelRetry: () -> Unit,
    onMarkResolved: () -> Unit,
) {
    if (entry.statusLabel() == null && !state.isRetrying) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (entry.needsAttention) {
                Text(
                    text = stringResource(R.string.history_detail_failed_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            HistoryStatusChip(entry)
            if (!entry.hasText) {
                Text(
                    text = stringResource(R.string.history_detail_no_text_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state.isRetrying -> RetryProgress(isEnhancing = state.isEnhancing, onCancel = onCancelRetry)
                state.canRetry -> RetryButton(onRetry)
            }
            if (entry.needsAttention && entry.hasText && !state.isWorking) {
                OutlinedButton(onClick = onMarkResolved) {
                    Text(stringResource(R.string.history_mark_resolved))
                }
            }
        }
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.history_retry), modifier = Modifier.padding(start = Spacing.sm))
        }
        Text(
            text = stringResource(R.string.history_retry_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RetryProgress(isEnhancing: Boolean, onCancel: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(if (isEnhancing) R.string.history_reenhance_running else R.string.history_retry_running),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.component_cancel)) }
    }
}
