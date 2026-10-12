package com.github.shekohex.whisperpp.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** When, where and with what the transcript was made. */
@Composable
fun HistoryDetailMetadata(
    entry: TranscriptHistoryEntry,
    app: HistoryAppInfo?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val whenText = DateUtils.formatDateTime(
        context,
        entry.createdAt,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
    )
    val seconds = ((entry.durationMs + 500) / 1000).toInt()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MetadataRow(stringResource(R.string.history_meta_when), whenText)
            MetadataRow(stringResource(R.string.history_meta_duration), formatClock(seconds * 1000))
            if (entry.hasText) {
                MetadataRow(
                    stringResource(R.string.history_meta_words),
                    pluralStringResource(R.plurals.history_word_count, entry.wordCount, entry.wordCount),
                )
            }
            entry.appPackage?.let { MetadataRow(stringResource(R.string.history_meta_app), app?.label ?: it) }
            if (entry.providerId.isNotBlank()) MetadataRow(stringResource(R.string.history_meta_provider), entry.providerId)
            if (entry.sttModelId.isNotBlank()) MetadataRow(stringResource(R.string.history_meta_model), entry.sttModelId)
            entry.enhancementModelId?.let { MetadataRow(stringResource(R.string.history_meta_enhancement_model), it) }
            entry.language?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.history_meta_language), it) }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
