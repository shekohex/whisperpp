package com.github.shekohex.whisperpp.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R

/** "Slide up to lock": the open lock rises with [progress] and clicks shut (scale bump) when [locked]. */
@Composable
fun LockHint(
    progress: Float,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val click = remember { Animatable(1f) }
    LaunchedEffect(locked) {
        if (!locked) return@LaunchedEffect
        click.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = GestureTokens.COMMIT_FEEDBACK_MS
                GestureTokens.LOCK_CLICK_SCALE at GestureTokens.COMMIT_FEEDBACK_MS / 3
            },
        )
    }
    val tint = MaterialTheme.colorScheme.tertiary

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    translationY = -progress * GestureTokens.LOCK_HINT_RISE_DP * density
                    scaleX = click.value
                    scaleY = click.value
                },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.slide_up_to_lock),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = 0.6f + 0.4f * progress },
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { alpha = 1f - progress },
        )
    }
}
