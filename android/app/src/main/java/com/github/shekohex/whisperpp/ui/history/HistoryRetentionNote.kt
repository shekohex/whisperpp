package com.github.shekohex.whisperpp.ui.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R

/** The retention policy in one line: "Recordings and transcripts are deleted after N days." */
@Composable
internal fun HistoryRetentionNote(retentionDays: Int, modifier: Modifier = Modifier) {
    Text(
        text = if (retentionDays <= 0) {
            stringResource(R.string.history_retention_policy_forever)
        } else {
            pluralStringResource(R.plurals.history_retention_policy, retentionDays, retentionDays)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
