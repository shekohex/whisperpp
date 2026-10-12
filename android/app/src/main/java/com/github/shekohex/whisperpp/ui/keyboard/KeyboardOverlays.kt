package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.command.CommandStage
import com.github.shekohex.whisperpp.data.TRANSFORM_PRESETS

/** "Discard this recording?" in the keyboard window (H-14; was an AlertDialog with an IME token). */
@Composable
internal fun CancelConfirmationSheet(
    maxHeight: Dp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KeyboardInlineSheet(
        title = stringResource(R.string.cancel_confirmation_title),
        maxHeight = maxHeight,
        onDismiss = onDismiss,
        showCloseButton = false,
    ) {
        Text(
            text = stringResource(R.string.cancel_confirmation_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_confirmation_dismiss))
            }
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.cancel_confirmation_confirm))
            }
        }
    }
}

@Composable
internal fun CommandClipboardSheet(
    overlayMaxHeight: Dp,
    unavailableReason: String?,
    preview: String,
    charCount: Int,
    isLarge: Boolean,
    attemptsRemaining: Int,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    KeyboardInlineSheet(
        title = stringResource(R.string.command_clipboard_sheet_title),
        maxHeight = overlayMaxHeight,
        onDismiss = onCancel,
    ) {
        Text(
            text = stringResource(R.string.command_clipboard_sheet_steps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (unavailableReason != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = unavailableReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Text(
                text = stringResource(R.string.command_clipboard_sheet_recovery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (preview.isNotBlank()) {
            ClipboardPreview(preview = preview, charCount = charCount)
        }

        if (isLarge) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.command_clipboard_sheet_large_warning, charCount, charCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.command_clipboard_sheet_attempts_remaining, attemptsRemaining),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.command_clipboard_sheet_continue))
        }
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.command_clipboard_sheet_refresh))
        }
    }
}

@Composable
private fun ClipboardPreview(preview: String, charCount: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.command_clipboard_sheet_preview_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            )
            Text(
                text = stringResource(R.string.command_clipboard_sheet_char_count, charCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CommandPresetPickerSheet(
    overlayMaxHeight: Dp,
    selectedPresetId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    KeyboardInlineSheet(
        title = stringResource(R.string.command_preset_picker_title),
        maxHeight = overlayMaxHeight,
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.command_preset_picker_helper),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.selectableGroup()) {
            TRANSFORM_PRESETS.forEach { preset ->
                val selected = preset.id == selectedPresetId
                ListItem(
                    headlineContent = { Text(stringResource(preset.titleRes)) },
                    supportingContent = { Text(stringResource(preset.descriptionRes)) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(preset.id) },
                )
            }
        }
    }
}

@Composable
internal fun EnhancementNoticeBanner(notice: EnhancementNoticeUiState, onDismiss: () -> Unit) {
    val isError = notice.style == EnhancementNoticeStyle.ERROR
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(
                onClickLabel = stringResource(R.string.keyboard_notice_dismiss),
                role = Role.Button,
                onClick = onDismiss,
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor,
        tonalElevation = 6.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Error else Icons.Default.Info,
                contentDescription = null,
            )
            Text(
                text = notice.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(imageVector = Icons.Default.Close, contentDescription = null)
        }
    }
}

/** "Hold to record" hint, end-aligned over the record button (was a fixed-offset popup window). */
@Composable
internal fun LongPressHintBanner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 8.dp,
        ) {
            Text(
                text = stringResource(R.string.long_press_hint),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Command-mode status strip; actions wrap onto a second line on narrow (320–360 dp) keyboards. */
@Composable
internal fun CommandOverlayBar(
    overlayMaxHeight: Dp,
    stage: CommandStage,
    stageLabel: String,
    errorMessage: String?,
    presetLabel: String,
    undoAvailable: Boolean,
    undoRemainingMs: Long,
    onOpenPresetPicker: () -> Unit,
    onUndo: () -> Unit,
    onStopListening: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .heightIn(max = overlayMaxHeight),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        text = stageLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!errorMessage.isNullOrBlank() && stage == CommandStage.ERROR) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.command_cancel))
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = onOpenPresetPicker,
                    label = { Text(stringResource(R.string.command_preset_chip_label, presetLabel)) },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                if (undoAvailable) {
                    AssistChip(
                        onClick = onUndo,
                        label = {
                            Text(stringResource(R.string.command_undo_chip_label, formatCommandUndoSeconds(undoRemainingMs)))
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
                when (stage) {
                    CommandStage.LISTENING -> FilledTonalButton(onClick = onStopListening) {
                        Text(stringResource(R.string.command_stop_listening))
                    }
                    CommandStage.ERROR -> FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.command_retry))
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun formatCommandUndoSeconds(remainingMs: Long): Int {
    if (remainingMs <= 0L) return 0
    val seconds = (remainingMs + 999L) / 1000L
    return seconds.toInt().coerceAtLeast(0)
}
