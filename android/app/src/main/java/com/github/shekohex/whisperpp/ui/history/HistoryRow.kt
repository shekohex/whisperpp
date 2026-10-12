package com.github.shekohex.whisperpp.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.ui.components.ChipStatus
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.StatusChip
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.AppShapes
import com.github.shekohex.whisperpp.ui.theme.Spacing

/** One transcript in the list: preview, relative time and app / model / enhanced chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRow(
    entry: TranscriptHistoryEntry,
    app: HistoryAppInfo?,
    nowMillis: Long,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeState = rememberSwipeToDismissBoxState()
    // A swipe selects the row and springs back.
    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
            onToggleSelection()
            swipeState.reset()
        }
    }
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer, AppShapes.card)
                    .padding(horizontal = Spacing.xl),
                contentAlignment = if (swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    ) {
        HistoryRowContent(entry, app, nowMillis, isSelecting, isSelected, onClick, onToggleSelection)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRowContent(
    entry: TranscriptHistoryEntry,
    app: HistoryAppInfo?,
    nowMillis: Long,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val relativeTime = remember(entry.createdAt, nowMillis) {
        DateUtils.getRelativeTimeSpanString(
            entry.createdAt,
            nowMillis,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
    Surface(
        shape = AppShapes.card,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isSelected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            if (isSelecting) {
                Checkbox(checked = isSelected, onCheckedChange = null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.finalText,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.appPackage?.let { MetaChip(text = app?.label ?: it, app = app) }
                    MetaChip(text = entry.sttModelId, icon = Icons.Default.Memory)
                    if (entry.enhancedText != null) {
                        StatusChip(
                            text = stringResource(R.string.history_enhanced_marker),
                            status = ChipStatus.Ok,
                            icon = Icons.Default.AutoAwesome,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    app: HistoryAppInfo? = null,
) {
    Surface(
        modifier = modifier,
        shape = AppShapes.chip,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val bitmap = app?.icon
            when {
                bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.size(16.dp))
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Spacing.xs),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun HistoryRowPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val now = 1_800_000_000_000L
            HistoryRow(
                entry = TranscriptHistoryEntry(
                    id = 1,
                    createdAt = now - 14 * DateUtils.MINUTE_IN_MILLIS,
                    rawText = "um so the meeting is moved to thursday",
                    enhancedText = "The meeting is moved to Thursday. موعد الاجتماع تغيّر.",
                    providerId = "openai",
                    sttModelId = "whisper-1",
                    appPackage = "com.example.chat",
                    durationMs = 4_000,
                    wordCount = 7,
                ),
                app = HistoryAppInfo(label = "Chat", icon = null),
                nowMillis = now,
                isSelecting = true,
                isSelected = true,
                onClick = {},
                onToggleSelection = {},
            )
        }
    }
}
