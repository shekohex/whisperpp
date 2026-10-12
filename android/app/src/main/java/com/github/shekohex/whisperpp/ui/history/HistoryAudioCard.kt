package com.github.shekohex.whisperpp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.util.Locale

private const val POSITION_REFRESH_MS = 250L

/** Play/pause with elapsed / total time for the kept recording. */
@Composable
fun HistoryAudioCard(player: HistoryAudioPlayer, modifier: Modifier = Modifier) {
    val state by player.state.collectAsStateWithLifecycle()
    LaunchedEffect(player, state.isPlaying) {
        while (player.state.value.isPlaying) {
            player.refreshPosition()
            delay(POSITION_REFRESH_MS)
        }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::toggle, enabled = state.isReady && !state.hasError) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (state.isPlaying) R.string.history_audio_pause else R.string.history_audio_play,
                    ),
                )
            }
            if (state.hasError) {
                Text(
                    text = stringResource(R.string.history_audio_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                LinearProgressIndicator(
                    progress = {
                        if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.history_audio_progress,
                        formatClock(state.positionMs),
                        formatClock(state.durationMs),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

internal fun formatClock(millis: Int): String {
    val totalSeconds = (millis.coerceAtLeast(0) + 500) / 1000
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
