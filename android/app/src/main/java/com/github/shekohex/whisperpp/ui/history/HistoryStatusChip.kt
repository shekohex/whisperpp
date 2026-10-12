package com.github.shekohex.whisperpp.ui.history

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.data.history.TranscriptHistoryEntry
import com.github.shekohex.whisperpp.domain.dictation.HistoryFailureReason
import com.github.shekohex.whisperpp.domain.dictation.HistoryStatus
import com.github.shekohex.whisperpp.ui.components.ChipStatus
import com.github.shekohex.whisperpp.ui.components.StatusChip

@StringRes
internal fun HistoryFailureReason?.labelRes(): Int = when (this) {
    HistoryFailureReason.RECORDER_ERROR -> R.string.history_reason_recorder
    HistoryFailureReason.TRANSCRIPTION_FAILED -> R.string.history_reason_transcription
    HistoryFailureReason.NETWORK_ERROR -> R.string.history_reason_network
    HistoryFailureReason.EMPTY_TRANSCRIPT -> R.string.history_reason_empty
    HistoryFailureReason.ENHANCEMENT_FAILED -> R.string.history_reason_enhancement
    HistoryFailureReason.INSERT_FAILED -> R.string.history_reason_insert_failed
    HistoryFailureReason.INSERT_BLOCKED -> R.string.history_reason_insert_blocked
    HistoryFailureReason.CANCELLED -> R.string.history_reason_cancelled
    HistoryFailureReason.INTERRUPTED -> R.string.history_reason_interrupted
    null -> R.string.history_status_failed
}

/** The take's state when it is not a plain success: failed with its reason, still processing, or a finished take that fell back to raw text. */
internal sealed interface HistoryStatusLabel {
    data class Failed(val reason: HistoryFailureReason?) : HistoryStatusLabel
    data object Processing : HistoryStatusLabel
    data object EnhancementFailed : HistoryStatusLabel
}

internal fun TranscriptHistoryEntry.statusLabel(): HistoryStatusLabel? = when {
    status == HistoryStatus.FAILED -> HistoryStatusLabel.Failed(failureReason)
    status.isInFlight -> HistoryStatusLabel.Processing
    failureReason == HistoryFailureReason.ENHANCEMENT_FAILED -> HistoryStatusLabel.EnhancementFailed
    else -> null
}

/** Status of a take that needs the user's eye; nothing for a plain success. */
@Composable
internal fun HistoryStatusChip(entry: TranscriptHistoryEntry, modifier: Modifier = Modifier) {
    when (val label = entry.statusLabel()) {
        null -> Unit
        is HistoryStatusLabel.Failed -> StatusChip(
            text = stringResource(label.reason.labelRes()),
            status = ChipStatus.Error,
            icon = Icons.Default.ErrorOutline,
            modifier = modifier,
        )
        HistoryStatusLabel.Processing -> StatusChip(
            text = stringResource(R.string.history_status_processing),
            status = ChipStatus.Neutral,
            icon = Icons.Default.Sync,
            modifier = modifier,
        )
        HistoryStatusLabel.EnhancementFailed -> StatusChip(
            text = stringResource(R.string.history_reason_enhancement),
            status = ChipStatus.Warning,
            icon = Icons.Default.WarningAmber,
            modifier = modifier,
        )
    }
}
