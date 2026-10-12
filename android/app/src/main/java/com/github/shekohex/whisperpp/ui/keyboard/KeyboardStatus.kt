package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.keyboard.KeyboardState
import com.github.shekohex.whisperpp.keyboard.isLocked

@Composable
fun PausedControlsCenter(
    state: KeyboardState,
    recordingTimeMs: () -> Long,
    onResume: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (state.isLocked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                Icons.Default.Pause,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            ElapsedTimeText(
                millis = recordingTimeMs,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        FilledTonalIconButton(
            onClick = onResume,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.record_action_resume),
            )
        }
    }
}

@Composable
fun StatusContent(state: KeyboardState, level: () -> Float, recordingTimeMs: () -> Long) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
        },
        label = "KeyboardStateTransition"
    ) { targetState ->
        when (targetState) {
            KeyboardState.Ready -> {
                Text(
                    stringResource(R.string.input_ready),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            KeyboardState.Recording -> {
                StatusWaveform(level)
            }
            KeyboardState.RecordingLocked -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusWaveform(level)
                }
            }
            KeyboardState.Paused, KeyboardState.PausedLocked -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (targetState.isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    ElapsedTimeText(
                        millis = recordingTimeMs,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        modifier = Modifier.animateAlphaLoop()
                    )
                }
            }
            KeyboardState.Transcribing -> {
                TranscribingStatus()
            }
            KeyboardState.Enhancing -> {
                WhimsicalStatus()
            }
        }
    }
}

@Composable
fun TranscribingStatus() {
    val infiniteTransition = rememberInfiniteTransition(label = "TranscribingShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TranscribingShimmerOffset"
    )
    
    val baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.primary
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerOffset * 400f - 100f, 0f),
        end = Offset(shimmerOffset * 400f + 100f, 0f)
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.status_transcribing),
            style = MaterialTheme.typography.bodyMedium.copy(brush = brush)
        )
    }
}

@Composable
private fun StatusWaveform(level: () -> Float) {
    // 48 bars at 3 dp + 2 dp gap; a narrower parent just shows fewer (newest) bars.
    LiveWaveform(
        level = level,
        modifier = Modifier.width(238.dp).height(32.dp),
    )
}

internal enum class CenterContent {
    Status,
    CancelHint,
    LockHint,
}

/** Committed hints stay up until the finger lifts so the shake / click is visible. */
internal fun centerContentFor(state: KeyboardState, gesture: GestureUi): CenterContent = when {
    gesture.phase == GesturePhase.Cancelled -> CenterContent.CancelHint
    gesture.phase == GesturePhase.Locked -> CenterContent.LockHint
    state != KeyboardState.Recording -> CenterContent.Status
    gesture.axis == GestureAxis.Horizontal -> CenterContent.CancelHint
    gesture.axis == GestureAxis.Vertical -> CenterContent.LockHint
    else -> CenterContent.Status
}

internal fun HapticKind.toFeedbackType(): HapticFeedbackType = when (this) {
    HapticKind.LongPress -> HapticFeedbackType.LongPress
    HapticKind.Confirm -> HapticFeedbackType.Confirm
    HapticKind.Reject -> HapticFeedbackType.Reject
}

/**
 * Follows the tracked pointer until it lifts or is cancelled. Compose delivers a system cancel as an
 * already-consumed release, which is how it is told apart from a real lift.
 */
internal suspend fun AwaitPointerEventScope.trackRecordPointer(
    down: PointerInputChange,
    dispatch: (GestureInput) -> Unit,
) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: continue
        if (!change.pressed) {
            dispatch(if (change.isConsumed) GestureInput.Cancel else GestureInput.Up(change.uptimeMillis))
            change.consume()
            return
        }
        if (change.positionChanged()) {
            val offset = change.position - down.position
            dispatch(GestureInput.Move(offset.x, offset.y, change.uptimeMillis))
            change.consume()
        }
    }
}

/** Main record button: one gesture handler (§4.1) plus accessibility actions mapped to the same intents (H-15). */
