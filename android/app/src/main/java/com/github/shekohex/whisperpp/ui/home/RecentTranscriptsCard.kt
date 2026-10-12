package com.github.shekohex.whisperpp.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.SectionHeader
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.Spacing

const val RECENT_TRANSCRIPTS_TAG = "home_recent_transcripts"
const val RECENT_TRANSCRIPTS_LIMIT = 5

/** Last few transcripts (§4.9 item 4): tap opens the transcript, long-press copies it. */
@Composable
fun RecentTranscriptsCard(
    entries: List<TranscriptHistoryEntry>,
    onOpen: (TranscriptHistoryEntry) -> Unit,
    onCopy: (TranscriptHistoryEntry) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = remember { System.currentTimeMillis() },
) {
    Column(modifier = modifier.testTag(RECENT_TRANSCRIPTS_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.home_recent_title), modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_recent_see_all)) }
        }
        Card(
            shape = AppShapes.card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                entries.take(RECENT_TRANSCRIPTS_LIMIT).forEach { entry ->
                    RecentTranscriptRow(entry, nowMillis, onOpen = { onOpen(entry) }, onCopy = { onCopy(entry) })
                }
            }
        }
    }
}

@Composable
private fun RecentTranscriptRow(
    entry: TranscriptHistoryEntry,
    nowMillis: Long,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val time = remember(entry.createdAt, nowMillis) {
        DateUtils.getRelativeTimeSpanString(entry.createdAt, nowMillis, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_recent_open),
                onLongClickLabel = stringResource(R.string.home_recent_copy),
                onLongClick = onCopy,
                onClick = onOpen,
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.finalText,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun sampleTranscripts(now: Long = 1_760_000_000_000L): List<TranscriptHistoryEntry> = listOf(
    "Remind me to send the quarterly report to Sara before Thursday's sync.",
    "مرحبا، سأصل بعد عشر دقائق تقريباً. Traffic is heavy on the bridge.",
    "Draft a reply thanking the team for shipping the onboarding flow.",
).mapIndexed { index, text ->
    TranscriptHistoryEntry(
        id = index.toLong() + 1,
        createdAt = now - (index + 1) * 14 * DateUtils.MINUTE_IN_MILLIS,
        rawText = text,
        providerId = "openai",
        sttModelId = "whisper-1",
        durationMs = 4_000,
        wordCount = text.split(' ').size,
    )
}

@ThemePreviews
@Composable
private fun RecentTranscriptsCardPreview() {
    PreviewSurface {
        RecentTranscriptsCard(
            entries = sampleTranscripts(),
            onOpen = {},
            onCopy = {},
            onSeeAll = {},
            modifier = Modifier.padding(Spacing.lg),
            nowMillis = 1_760_000_000_000L,
        )
    }
}
