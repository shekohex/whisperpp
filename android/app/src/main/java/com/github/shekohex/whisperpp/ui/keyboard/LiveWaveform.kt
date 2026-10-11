package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val OLD_EDGE_FADE_FRACTION = 0.2f
private const val NEW_EDGE_FADE_FRACTION = 0.05f
private const val NEW_EDGE_MIN_ALPHA = 0.4f

/**
 * Scrolling, mirrored-bar voice waveform (newest sample on the right, on the left in RTL).
 *
 * [level] (0..1) is polled once per frame and turned into one history sample every 20 ms; the bars scroll by
 * the fractional progress between samples so motion stays smooth at any refresh rate. Drawing allocates nothing.
 * The canvas must be given a size by [modifier]; if it is narrower than [barCount] bars, fewer bars are drawn.
 */
@Composable
fun LiveWaveform(
    level: () -> Float,
    modifier: Modifier = Modifier,
    barCount: Int = 48,
    barWidth: Dp = 3.dp,
    gap: Dp = 2.dp,
    minBarHeight: Dp = 2.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val history = remember(barCount) { WaveformHistory(barCount) }
    var frame by remember(history) { mutableIntStateOf(0) }
    val currentLevel by rememberUpdatedState(level)

    LaunchedEffect(history) {
        history.clear()
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val elapsed = if (lastFrameNanos == 0L) 0L else now - lastFrameNanos
                lastFrameNanos = now
                history.advance(elapsed, currentLevel())
                frame++
            }
        }
    }

    val description = stringResource(R.string.live_audio_level)
    Canvas(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        frame // Read so every new frame invalidates the draw phase only.

        val barWidthPx = barWidth.toPx()
        val stepPx = barWidthPx + gap.toPx()
        val bars = min(barCount, ((size.width + gap.toPx()) / stepPx).toInt())
        if (bars <= 0) return@Canvas

        val spanPx = bars * stepPx - gap.toPx()
        val scrollPx = history.fraction * stepPx
        val minHeightPx = minBarHeight.toPx()
        val heightRangePx = (size.height - minHeightPx).coerceAtLeast(0f)
        val radius = CornerRadius(barWidthPx / 2f)
        val rtl = layoutDirection == LayoutDirection.Rtl

        for (age in 0 until bars) {
            val distanceFromNewest = age * stepPx + scrollPx
            val position = (distanceFromNewest / spanPx).coerceIn(0f, 1f)
            val alpha = edgeAlpha(position)
            if (alpha <= 0f) continue

            val barHeight = minHeightPx + history[age] * heightRangePx
            val left = if (rtl) distanceFromNewest else size.width - barWidthPx - distanceFromNewest
            drawRoundRect(
                color = color,
                topLeft = Offset(left, (size.height - barHeight) / 2f),
                size = Size(barWidthPx, barHeight),
                cornerRadius = radius,
                alpha = alpha,
            )
        }
    }
}

/** [position] runs 0 at the newest edge to 1 at the oldest; both ends fade, the old end to nothing. */
private fun edgeAlpha(position: Float): Float = when {
    position > 1f - OLD_EDGE_FADE_FRACTION -> (1f - position) / OLD_EDGE_FADE_FRACTION
    position < NEW_EDGE_FADE_FRACTION ->
        NEW_EDGE_MIN_ALPHA + (1f - NEW_EDGE_MIN_ALPHA) * position / NEW_EDGE_FADE_FRACTION
    else -> 1f
}

@Composable
private fun rememberFakeSineLevel(): () -> Float {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val seconds = (now - startNanos) / 1_000_000_000.0
                val speech = 0.5 + 0.5 * sin(2.0 * PI * 0.7 * seconds)
                val syllables = 0.5 + 0.5 * sin(2.0 * PI * 3.1 * seconds)
                level = (speech * syllables).toFloat()
            }
        }
    }
    return { level }
}

@Preview(name = "LiveWaveform", showBackground = true, widthDp = 320)
@Composable
private fun LiveWaveformPreview() {
    WhisperToInputTheme(darkTheme = false) {
        Surface {
            Box(Modifier.padding(16.dp)) {
                LiveWaveform(
                    level = rememberFakeSineLevel(),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                )
            }
        }
    }
}

@Preview(name = "LiveWaveform dark", showBackground = true, widthDp = 320)
@Composable
private fun LiveWaveformDarkPreview() {
    WhisperToInputTheme(darkTheme = true) {
        Surface {
            Box(Modifier.padding(16.dp)) {
                LiveWaveform(
                    level = rememberFakeSineLevel(),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                )
            }
        }
    }
}
