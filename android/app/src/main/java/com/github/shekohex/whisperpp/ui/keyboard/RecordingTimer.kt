package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val DOT_DIM_ALPHA = 0.2f

/** `mm:ss`; minutes keep counting past 59. */
fun formatTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

/** `mm:ss` label that recomposes once per second however often [millis] ticks. */
@Composable
fun ElapsedTimeText(
    millis: () -> Long,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val seconds by remember(millis) { derivedStateOf { millis() / 1000L } }
    Text(
        text = formatTime(seconds * 1000L),
        style = style,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** Recording time with a red dot blinking at 1 Hz (B-07). */
@Composable
fun RecordingTimer(millis: () -> Long) {
    val transition = rememberInfiniteTransition(label = "RecDot")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = DOT_DIM_ALPHA,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = GestureTokens.TIMER_BLINK_PERIOD_MS
                val half = GestureTokens.TIMER_BLINK_PERIOD_MS / 2
                1f at 0
                1f at half - 1
                DOT_DIM_ALPHA at half
                DOT_DIM_ALPHA at GestureTokens.TIMER_BLINK_PERIOD_MS
            },
        ),
        label = "DotAlpha",
    )
    val error = MaterialTheme.colorScheme.error

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = dotAlpha }
                .background(error, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        ElapsedTimeText(
            millis = millis,
            color = error,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
