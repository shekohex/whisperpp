package com.github.shekohex.whisperpp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** "All" / "Needs attention (n)": the second chip is how Home's banner lands on the failed takes. */
@Composable
internal fun HistoryFilterRow(
    selected: HistoryFilter,
    needsAttentionCount: Int,
    onSelect: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = selected == HistoryFilter.ALL,
            onClick = { onSelect(HistoryFilter.ALL) },
            label = { Text(stringResource(R.string.history_filter_all)) },
        )
        FilterChip(
            selected = selected == HistoryFilter.NEEDS_ATTENTION,
            onClick = { onSelect(HistoryFilter.NEEDS_ATTENTION) },
            label = { Text("${stringResource(R.string.history_filter_attention)} ($needsAttentionCount)") },
        )
    }
}
